package com.g95nc.controller

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.BOOL
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.HMONITOR
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface Dxva2 : Library {
    companion object {
        val INSTANCE = Native.load("dxva2", Dxva2::class.java) as Dxva2
    }

    @Structure.FieldOrder("hPhysicalMonitor", "szPhysicalMonitorDescription")
    class PHYSICAL_MONITOR : Structure() {
        @JvmField var hPhysicalMonitor: Pointer? = null
        @JvmField var szPhysicalMonitorDescription: CharArray = CharArray(128)
    }

    fun GetNumberOfPhysicalMonitorsFromHMONITOR(hMonitor: HMONITOR, pdwNumberOfPhysicalMonitors: IntByReference): Boolean
    fun GetPhysicalMonitorsFromHMONITOR(hMonitor: HMONITOR, dwPhysicalMonitorArraySize: Int, pPhysicalMonitorArray: Array<PHYSICAL_MONITOR>): Boolean
    fun GetVCPFeatureAndVCPFeatureReply(hMonitor: Pointer, bVCPCode: Byte, pvct: IntByReference?, pdwCurrentValue: IntByReference, pdwMaximumValue: IntByReference?): Boolean
    fun SetVCPFeature(hMonitor: Pointer, bVCPCode: Byte, dwNewValue: Int): Boolean
    fun DestroyPhysicalMonitor(hMonitor: Pointer): Boolean
}

class Win32Dxva2Controller : MonitorController {

    // GetPhysicalMonitorsFromHMONITOR hands out OS handles that must be released with
    // DestroyPhysicalMonitor. The previous implementation re-enumerated and re-opened handles on
    // every single VCP read/write and never released any of them - a handle leak on every poll.
    // We now discover the handle once and reuse it, only re-discovering on failure.
    private var cachedHandle: Pointer? = null

    override suspend fun isToolAvailable(): Boolean {
        // Since we rely on Windows DLLs, it's true if we are on Windows and dxva2 is loaded
        return Platform.detect() == Platform.WINDOWS
    }

    private fun releaseCachedHandle() {
        cachedHandle?.let { Dxva2.INSTANCE.DestroyPhysicalMonitor(it) }
        cachedHandle = null
    }

    private fun findG95NChandle(forceRefresh: Boolean = false): Pointer? {
        if (!forceRefresh && cachedHandle != null) return cachedHandle
        releaseCachedHandle()

        val allHandles = mutableListOf<Pointer>()
        var matchedHandle: Pointer? = null

        val callback = WinUser.MONITORENUMPROC { hMonitor, _, _, _ ->
            val countRef = IntByReference()
            if (Dxva2.INSTANCE.GetNumberOfPhysicalMonitorsFromHMONITOR(hMonitor, countRef) && countRef.value > 0) {
                val array = Array(countRef.value) { Dxva2.PHYSICAL_MONITOR() }
                if (Dxva2.INSTANCE.GetPhysicalMonitorsFromHMONITOR(hMonitor, countRef.value, array)) {
                    for (pm in array) {
                        val handle = pm.hPhysicalMonitor ?: continue
                        allHandles.add(handle)

                        if (matchedHandle == null) {
                            val curRef = IntByReference()
                            val code = 0xE2.toByte()
                            if (Dxva2.INSTANCE.GetVCPFeatureAndVCPFeatureReply(handle, code, null, curRef, null)) {
                                if (((curRef.value shr 8) and 0xFF) == 0x39) {
                                    matchedHandle = handle
                                }
                            }
                        }
                    }
                }
            }
            1 // Keep enumerating so every handle we open gets tracked for cleanup
        }
        User32.INSTANCE.EnumDisplayMonitors(null, null, callback, null)

        val selected = matchedHandle ?: allHandles.firstOrNull()
        allHandles.filter { it != selected }.forEach { Dxva2.INSTANCE.DestroyPhysicalMonitor(it) }

        cachedHandle = selected
        return selected
    }

    private suspend fun getVcpValue(vcp: Int): Int? = withContext(Dispatchers.IO) {
        val handle = findG95NChandle() ?: return@withContext null
        val curRef = IntByReference()
        if (Dxva2.INSTANCE.GetVCPFeatureAndVCPFeatureReply(handle, vcp.toByte(), null, curRef, null)) {
            return@withContext curRef.value
        }

        // Handle may have gone stale (monitor sleep/reconnect) - rediscover once and retry.
        val refreshed = findG95NChandle(forceRefresh = true) ?: return@withContext null
        val retryRef = IntByReference()
        if (Dxva2.INSTANCE.GetVCPFeatureAndVCPFeatureReply(refreshed, vcp.toByte(), null, retryRef, null)) {
            retryRef.value
        } else {
            null
        }
    }

    private suspend fun setVcpValue(vcp: Int, value: Int): Boolean = withContext(Dispatchers.IO) {
        val handle = findG95NChandle() ?: return@withContext false
        if (Dxva2.INSTANCE.SetVCPFeature(handle, vcp.toByte(), value)) return@withContext true

        val refreshed = findG95NChandle(forceRefresh = true) ?: return@withContext false
        Dxva2.INSTANCE.SetVCPFeature(refreshed, vcp.toByte(), value)
    }

    private fun vcpCodeToInt(vcp: String): Int = when (vcp) {
        "E2" -> 0xE2
        "E3" -> 0xE3
        "E8" -> 0xE8
        "60" -> 0x60
        else -> throw IllegalArgumentException("Unknown VCP code: $vcp")
    }

    override suspend fun getStatus(): MonitorStatus? {
        // The native DXVA2 path is faster/more direct than the CLI tools, but the monitor's DDC
        // bus can still echo a stale value right after a source/layout switch - use the same
        // validating retry loop as the other platforms, just with a shorter budget.
        LastControllerError.clear()
        return DdcStatusReader.readStatus(
            DdcStatusReader.PollingConfig(maxAttempts = 6, pollDelayMs = 300, zeroConfirmations = 2)
        ) { vcp -> getVcpValue(vcpCodeToInt(vcp)) }
    }

    override suspend fun setLayout(layout: Layout): Boolean {
        return setVcpValue(0xE2, layout.code)
    }

    override suspend fun setMainSource(sourceCode: SourceCode): Boolean {
        return setVcpValue(0x60, sourceCode.mccsCode)
    }

    override suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean {
        val e3Val = (channel.channel shl 8) or sourceCode.e3Code
        return setVcpValue(0xE3, e3Val)
    }

    override suspend fun setAudioSource(audioCode: Int): Boolean {
        val target = audioCode and 0xFF
        // Same "successful write != monitor actually applied it" issue documented for the CLI
        // controllers - verify by reading back and retry the write itself if it didn't stick.
        repeat(3) {
            if (!setVcpValue(0xE8, audioCode)) return false
            if ((getVcpValue(0xE8) ?: -1) and 0xFF == target) return true
        }
        return false
    }
}

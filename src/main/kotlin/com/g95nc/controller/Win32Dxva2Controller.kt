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
}

class Win32Dxva2Controller : MonitorController {

    override suspend fun isToolAvailable(): Boolean {
        // Since we rely on Windows DLLs, it's true if we are on Windows and dxva2 is loaded
        return System.getProperty("os.name").lowercase().contains("win")
    }

    private fun findG95NChandle(): Pointer? {
        var foundHandle: Pointer? = null
        var fallbackHandle: Pointer? = null

        val callback = WinUser.MONITORENUMPROC { hMonitor, _, _, _ ->
            val countRef = IntByReference()
            if (Dxva2.INSTANCE.GetNumberOfPhysicalMonitorsFromHMONITOR(hMonitor, countRef) && countRef.value > 0) {
                val array = Array(countRef.value) { Dxva2.PHYSICAL_MONITOR() }
                if (Dxva2.INSTANCE.GetPhysicalMonitorsFromHMONITOR(hMonitor, countRef.value, array)) {
                    for (pm in array) {
                        if (fallbackHandle == null) fallbackHandle = pm.hPhysicalMonitor
                        
                        val curRef = IntByReference()
                        val code = 0xE2.toByte()
                        if (Dxva2.INSTANCE.GetVCPFeatureAndVCPFeatureReply(pm.hPhysicalMonitor!!, code, null, curRef, null)) {
                            val cur = curRef.value
                            if (((cur shr 8) and 0xFF) == 0x39) {
                                foundHandle = pm.hPhysicalMonitor
                                return@MONITORENUMPROC 0 // Stop enumerating
                            }
                        }
                    }
                }
            }
            1 // Continue
        }
        User32.INSTANCE.EnumDisplayMonitors(null, null, callback, null)
        
        return foundHandle ?: fallbackHandle
    }

    private suspend fun getVcpValue(vcp: Int): Int? = withContext(Dispatchers.IO) {
        val handle = findG95NChandle() ?: return@withContext null
        val curRef = IntByReference()
        if (Dxva2.INSTANCE.GetVCPFeatureAndVCPFeatureReply(handle, vcp.toByte(), null, curRef, null)) {
            curRef.value
        } else {
            null
        }
    }

    private suspend fun setVcpValue(vcp: Int, value: Int): Boolean = withContext(Dispatchers.IO) {
        val handle = findG95NChandle() ?: return@withContext false
        Dxva2.INSTANCE.SetVCPFeature(handle, vcp.toByte(), value)
    }

    override suspend fun getStatus(): MonitorStatus? {
        val e2 = getVcpValue(0xE2) ?: return null
        val e3 = getVcpValue(0xE3) ?: return null
        val e8 = getVcpValue(0xE8) ?: 0
        val v60 = getVcpValue(0x60) ?: 0

        val layout = when (e2) {
            0x3901 -> Layout.PBP_2_50_50 
            0x3909 -> Layout.PBP_3_50_25_25
            0x390b -> Layout.PBP_3_25_50_25
            0x390d -> Layout.PBP_3_25_25_50
            0x390f -> Layout.PIP
            else -> Layout.OFF
        }
        
        return MonitorStatus(
            layout = layout,
            layoutStatusRaw = e2,
            paneSources = emptyMap(),
            audioSource = e8,
            mainInputSource = v60
        )
    }

    override suspend fun setLayout(layout: Layout): Boolean {
        return setVcpValue(0xE2, layout.code)
    }

    override suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean {
        val e3Val = (channel.channel shl 8) or sourceCode.e3Code
        return setVcpValue(0xE3, e3Val)
    }

    override suspend fun setAudioSource(audioCode: Int): Boolean {
        return setVcpValue(0xE8, audioCode)
    }
}

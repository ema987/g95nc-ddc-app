package com.g95nc.controller

/** Test double for [CommandRunner]: scripts responses per invocation instead of spawning a process. */
class FakeCommandRunner(private val handler: (List<String>) -> String) : CommandRunner {
    val invocations = mutableListOf<List<String>>()

    override suspend fun run(command: List<String>, timeoutMillis: Long): String {
        invocations.add(command)
        return handler(command)
    }

    fun countInvocationsContaining(token: String): Int = invocations.count { cmd -> cmd.any { it.contains(token) } }
}

/** Test double for [MonitorController]: records every call instead of touching real hardware. */
class FakeMonitorController(private val statusToReturn: MonitorStatus? = null) : MonitorController {
    sealed class Call {
        data class SetLayout(val layout: Layout) : Call()
        data class SetMainSource(val source: SourceCode) : Call()
        data class SetSource(val channel: PaneChannel, val source: SourceCode) : Call()
        data class SetAudioSource(val audioCode: Int) : Call()
    }

    val calls = mutableListOf<Call>()

    override suspend fun getStatus(): MonitorStatus? = statusToReturn

    override suspend fun setLayout(layout: Layout): Boolean {
        calls.add(Call.SetLayout(layout))
        return true
    }

    override suspend fun setMainSource(sourceCode: SourceCode): Boolean {
        calls.add(Call.SetMainSource(sourceCode))
        return true
    }

    override suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean {
        calls.add(Call.SetSource(channel, sourceCode))
        return true
    }

    override suspend fun setAudioSource(audioCode: Int): Boolean {
        calls.add(Call.SetAudioSource(audioCode))
        return true
    }

    override suspend fun isToolAvailable(): Boolean = true
}

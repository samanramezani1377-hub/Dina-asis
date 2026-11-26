package com.samanramezani.dina.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LogLevel { INFO, WARN, ERROR }

data class AgentLogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

/** In-memory live log shared by the UI, queue, model engine and mock tools. Newest entries are first. */
class AgentLogStore {
    private val sequence = java.util.concurrent.atomic.AtomicLong(0)
    private val mutable = MutableStateFlow<List<AgentLogEntry>>(emptyList())
    val entries: StateFlow<List<AgentLogEntry>> = mutable

    fun info(message: String) = add(LogLevel.INFO, message)
    fun warn(message: String) = add(LogLevel.WARN, message)
    fun error(message: String, throwable: Throwable? = null) =
        add(LogLevel.ERROR, if (throwable == null) message else "$message: ${throwable.message ?: throwable::class.java.simpleName}")

    private fun add(level: LogLevel, message: String) {
        val entry = AgentLogEntry(sequence.incrementAndGet(), System.currentTimeMillis(), level, message)
        mutable.value = listOf(entry) + mutable.value.take(MAX_ENTRIES - 1)
    }

    fun copyText(): String = mutable.value.joinToString("\n") { entry ->
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(entry.timestamp))
        "[$time] [${entry.level}] ${entry.message}"
    }

    companion object { private const val MAX_ENTRIES = 2000 }
}

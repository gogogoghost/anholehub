package cc.jaxy.anlobehub.feature.chat

import cc.jaxy.anlobehub.core.data.chat.ChatMessage

/**
 * Groups a flat history into render turns, mirroring web conversation flow:
 * each user message starts a turn; following assistant/tool rows fold into
 * it. Short assistant preludes ("Searching, one moment…") preceding tool
 * calls collapse into the steps header with the tool calls themselves.
 */
sealed interface ChatTurn {
    data class User(val message: ChatMessage) : ChatTurn
    data class AssistantGroup(
        /** Collapsed prelude + tool rows ("Ran N steps"). */
        val steps: List<ChatMessage>,
        /** Final substantive answer (null when the turn has steps only). */
        val answer: ChatMessage?,
    ) : ChatTurn
}

/** Assistant rows shorter than this (ahead of tool calls) count as preludes. */
private const val PRELUDE_MAX_CHARS = 120

fun List<ChatMessage>.toTurns(): List<ChatTurn> {
    val turns = mutableListOf<ChatTurn>()
    var pending = mutableListOf<ChatMessage>()
    fun flushPending() {
        if (pending.isEmpty()) return
        turns.add(assistantGroup(pending.toList()))
        pending = mutableListOf()
    }
    for (message in this) {
        when (message.role) {
            "user" -> {
                flushPending()
                turns.add(ChatTurn.User(message))
            }
            else -> pending.add(message)
        }
    }
    flushPending()
    return turns
}

private fun assistantGroup(rows: List<ChatMessage>): ChatTurn.AssistantGroup {
    if (rows.size == 1 && rows[0].role == "assistant") {
        return ChatTurn.AssistantGroup(steps = emptyList(), answer = rows[0])
    }
    // The last assistant row with substantive content is the answer; tool
    // rows and short preludes ahead of the first tool call fold into steps.
    val firstTool = rows.indexOfFirst { it.role == "tool" || it.role == "function" }
    val lastAnswer = rows.indexOfLast {
        it.role == "assistant" && it.content.length > PRELUDE_MAX_CHARS
    }
    if (firstTool < 0 || lastAnswer < 0) {
        // No tool traffic (or nothing substantive): render rows as-is, with
        // the last assistant row as the answer for header purposes.
        val lastAssistant = rows.indexOfLast { it.role == "assistant" }
        if (lastAssistant < 0) {
            return ChatTurn.AssistantGroup(steps = rows, answer = null)
        }
        return ChatTurn.AssistantGroup(
            steps = rows.subList(0, lastAssistant),
            answer = rows[lastAssistant],
        )
    }
    return ChatTurn.AssistantGroup(
        steps = rows.subList(0, lastAnswer),
        answer = rows[lastAnswer],
    ).let {
        // Rows after the answer (trailing tool echoes) rejoin the steps.
        if (lastAnswer < rows.size - 1) {
            it.copy(steps = it.steps + rows.subList(lastAnswer + 1, rows.size))
        } else {
            it
        }
    }
}

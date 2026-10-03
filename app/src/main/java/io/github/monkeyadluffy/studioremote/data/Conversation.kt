package io.github.monkeyadluffy.studioremote.data

/**
 * A thread's message tree. Studio keeps every edit and regeneration as a sibling branch;
 * [leafId] picks the branch on screen, and [path] is the root-to-leaf list shown in the chat.
 */
data class Conversation(
    val nodes: List<UiMessage> = emptyList(),
    val leafId: String? = null,
) {
    private val byId: Map<String, UiMessage> by lazy { nodes.associateBy { it.id } }

    private val children: Map<String?, List<UiMessage>> by lazy {
        nodes.groupBy { it.parentId }.mapValues { (_, kids) -> kids.sortedBy { it.createdAt } }
    }

    val path: List<UiMessage> by lazy {
        val out = ArrayList<UiMessage>()
        val seen = HashSet<String>()
        var cur = leafId?.let(byId::get)
        while (cur != null && seen.add(cur.id)) {
            out += cur
            cur = cur.parentId?.let(byId::get)
        }
        out.asReversed()
    }

    operator fun get(id: String): UiMessage? = byId[id]

    /** Alternatives to [m] at the same point of the conversation (itself included), oldest first. */
    fun siblings(m: UiMessage): List<UiMessage> = children[m.parentId].orEmpty().filter { it.role == m.role }

    /** Shows the branch through [id], continuing down its most recent replies. */
    fun focus(id: String): Conversation {
        var cur = id
        while (true) cur = children[cur]?.maxByOrNull { it.createdAt }?.id ?: break
        return copy(leafId = cur)
    }

    fun add(vararg messages: UiMessage, leaf: String = messages.last().id): Conversation {
        val ids = messages.map { it.id }.toSet()
        return Conversation(nodes.filterNot { it.id in ids } + messages, leaf)
    }

    fun update(id: String, change: (UiMessage) -> UiMessage): Conversation =
        if (id in byId) copy(nodes = nodes.map { if (it.id == id) change(it) else it }) else this

    companion object {
        /**
         * Opens on the most recent message's branch, like the Studio web UI, continuing down to its
         * replies. A reply Studio created is stamped by the computer's clock, so it can look older
         * than the phone's question when the clocks disagree; descending keeps it on screen.
         */
        fun of(messages: List<UiMessage>): Conversation {
            val newest = messages.maxByOrNull { it.createdAt } ?: return Conversation()
            return Conversation(messages).focus(newest.id)
        }
    }
}

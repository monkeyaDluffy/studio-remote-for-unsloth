package io.github.monkeyadluffy.studioremote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import io.github.monkeyadluffy.studioremote.R
import io.github.monkeyadluffy.studioremote.data.DEEP_RESEARCH_TOOL
import io.github.monkeyadluffy.studioremote.data.ResearchRun
import io.github.monkeyadluffy.studioremote.data.ToolCall
import io.github.monkeyadluffy.studioremote.data.UiMessage
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import java.util.Locale

/** Position of a message among its alternatives (edits or regenerations). */
class Branch(val index: Int, val count: Int, val prevId: String?, val nextId: String?)

/** "‹ 2/3 ›" switcher under a message that has alternatives. */
@Composable
fun BranchSwitcher(b: Branch, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val prev = b.prevId?.takeIf { enabled }
        val next = b.nextId?.takeIf { enabled }
        IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_version), size = 32.dp, iconSize = 20.dp,
            tint = if (prev != null) C.TextDim else C.Border) { prev?.let(onSelect) }
        Text("${b.index + 1}/${b.count}", color = C.TextFaint, fontSize = 13.sp)
        IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_version), size = 32.dp, iconSize = 20.dp,
            tint = if (next != null) C.TextDim else C.Border) { next?.let(onSelect) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessage(m: UiMessage, branch: Branch?, canEdit: Boolean, onEdit: () -> Unit, onSelectBranch: (String) -> Unit) {
    val maxWidth = windowWidth() * 0.82f
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        m.files.forEach { name ->
            Row(
                Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.Surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, null, tint = C.TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(name, color = C.Text, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        m.images.forEach { img ->
            DataImage(
                img,
                Modifier.widthIn(max = maxWidth * 0.7f).padding(bottom = 6.dp).clip(RoundedCornerShape(18.dp)),
                ContentScale.FillWidth,
            )
        }
        if (m.text.isNotEmpty()) {
            Box {
                Box(
                    Modifier
                        .widthIn(max = maxWidth)
                        .clip(RoundedCornerShape(24.dp))
                        .background(C.SurfaceHigh)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(m.text, color = C.Text, fontSize = 16.5.sp, lineHeight = 24.sp)
                }
                ActionMenu(
                    menu, { menu = false },
                    listOfNotNull(
                        MenuAction(stringResource(R.string.copy), Icons.Rounded.ContentCopy) { copyToClipboard(context, m.text) },
                        if (canEdit) MenuAction(stringResource(R.string.edit_and_resend), Icons.Rounded.Edit, onClick = onEdit) else null,
                    ),
                )
            }
        }
        branch?.let { BranchSwitcher(it, canEdit, onSelectBranch) }
    }
}

@Composable
fun AssistantMessage(
    m: UiMessage,
    branch: Branch?,
    canSwitchBranch: Boolean,
    onSelectBranch: (String) -> Unit,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
    onToolAnswer: (toolId: String, allow: Boolean) -> Unit,
    onApproveResearch: () -> Unit,
    onCancelResearch: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        if (m.reasoning.isNotEmpty()) {
            ReasoningBlock(m)
            Spacer(Modifier.size(10.dp))
        }

        // The Deep Research hand-off is shown as the research card instead, unless it needs approval.
        m.tools.filter { it.name != DEEP_RESEARCH_TOOL || it.awaiting }.forEach { tool ->
            ToolCard(tool) { allow -> onToolAnswer(tool.id, allow) }
            Spacer(Modifier.size(8.dp))
        }
        if (m.streaming) {
            m.toolStatus?.let {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(14.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(it, color = C.TextDim, fontSize = 14.sp)
                }
            }
        }

        val body = m.text.ifEmpty { m.research?.report.orEmpty() }
        when {
            body.isNotEmpty() -> SelectionContainer { StableMarkdown(body) }
            m.streaming && m.reasoning.isEmpty() && m.tools.isEmpty() && m.toolStatus == null -> TypingDots()
        }

        m.research?.let { run ->
            Spacer(Modifier.size(10.dp))
            ResearchCard(run, onApproveResearch, onCancelResearch)
        }

        m.error?.let {
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.Error.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = C.Error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(it, color = C.Error, fontSize = 14.sp)
            }
        }

        if (!m.streaming && (body.isNotEmpty() || m.error != null || branch != null)) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                branch?.let { BranchSwitcher(it, canSwitchBranch, onSelectBranch) }
                if (body.isNotEmpty()) ActionIcon(Icons.Rounded.ContentCopy, stringResource(R.string.copy)) { copyToClipboard(context, body) }
                if (canRegenerate) ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.regenerate), onRegenerate)
                m.tokensPerSecond?.let {
                    Text(
                        String.format(Locale.US, "%.1f tok/s", it),
                        color = C.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** Collapsible card used for thinking, tool calls and research. */
@Composable
private fun ExpandableCard(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(C.Surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .animateContentSize(),
        content = content,
    )
}

@Composable
private fun Chevron(open: Boolean) {
    Icon(
        Icons.Rounded.KeyboardArrowDown, null, tint = C.TextFaint,
        modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
    )
}

@Composable
private fun ReasoningBlock(m: UiMessage) {
    var open by rememberSaveable(m.id) { mutableStateOf(false) }
    val thinking = m.streaming && m.reasoningMs == null
    val label = when {
        thinking -> stringResource(R.string.thinking_now)
        m.reasoningMs != null -> stringResource(R.string.thought_for, formatDuration(m.reasoningMs))
        else -> stringResource(R.string.thinking)
    }
    val pulse by rememberInfiniteTransition(label = "pulse")
        .animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    val alpha = if (thinking) pulse else 1f

    ExpandableCard(open, { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lightbulb, null, tint = C.TextDim, modifier = Modifier.size(16.dp).alpha(alpha))
            Spacer(Modifier.size(8.dp))
            Text(label, color = C.TextDim, fontSize = 14.sp, modifier = Modifier.alpha(alpha))
            Spacer(Modifier.size(4.dp))
            Chevron(open)
        }
        AnimatedVisibility(open) {
            Text(
                m.reasoning.trim(),
                color = C.TextFaint, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun toolLabel(name: String): String = when (name) {
    "web_search" -> stringResource(R.string.web_search)
    "web_fetch" -> stringResource(R.string.tool_web_fetch)
    "python" -> "Python"
    "terminal" -> "Terminal"
    "edit_file" -> stringResource(R.string.tool_edit_file)
    "render_html" -> stringResource(R.string.tool_render_html)
    "search_knowledge_base" -> stringResource(R.string.tool_search_files)
    "search_conversation" -> stringResource(R.string.tool_search_chat)
    DEEP_RESEARCH_TOOL -> stringResource(R.string.deep_research)
    else -> name
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "web_search", "web_fetch" -> Icons.Outlined.Public
    "python", "terminal" -> Icons.Outlined.Terminal
    "search_knowledge_base" -> Icons.Outlined.Description
    DEEP_RESEARCH_TOOL -> Icons.Outlined.TravelExplore
    else -> Icons.Outlined.Build
}

@Composable
private fun ToolCard(tool: ToolCall, onAnswer: (allow: Boolean) -> Unit) {
    var open by rememberSaveable(tool.id) { mutableStateOf(false) }
    val running = tool.result == null && !tool.awaiting && !tool.denied
    val summary = remember(tool.argsText) { tool.summary }

    ExpandableCard(open, { open = !open }, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) Spinner() else Icon(toolIcon(tool.name), null, tint = C.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                val label = toolLabel(tool.name)
                val title = when {
                    tool.awaiting -> stringResource(R.string.tool_awaiting, label)
                    tool.denied -> stringResource(R.string.tool_denied, label)
                    else -> label
                }
                Text(title, color = C.Text, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                if (summary.isNotBlank()) {
                    Text(
                        summary, color = C.TextDim, fontSize = 13.sp,
                        maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!tool.awaiting) Chevron(open)
        }
        if (tool.awaiting) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.allow), primary = true) { onAnswer(true) }
                PillButton(stringResource(R.string.deny), primary = false) { onAnswer(false) }
            }
        }
        AnimatedVisibility(open && tool.result != null) {
            Text(
                tool.result.orEmpty().take(3000),
                color = C.TextFaint, fontSize = 12.5.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun researchLabel(r: ResearchRun): String = when (r.status) {
    "planning" -> stringResource(R.string.research_planning)
    "awaiting_approval" -> stringResource(R.string.research_awaiting)
    "queued" -> stringResource(R.string.research_queued)
    "running" -> stringResource(R.string.research_running)
    "paused" -> stringResource(R.string.research_paused)
    "cancelling" -> stringResource(R.string.research_cancelling)
    "completed" -> stringResource(R.string.research_completed)
    "failed" -> r.error?.let { stringResource(R.string.research_failed_reason, it) } ?: stringResource(R.string.research_failed)
    "cancelled" -> stringResource(R.string.research_cancelled)
    else -> r.status
}

@Composable
private fun ResearchCard(r: ResearchRun, onApprove: () -> Unit, onCancel: () -> Unit) {
    var open by rememberSaveable(r.id) { mutableStateOf(false) }
    val failed = r.status == "failed"

    ExpandableCard(open, { open = !open }, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!r.terminal && !r.awaitingApproval) {
                Spinner()
            } else {
                Icon(Icons.Outlined.TravelExplore, null, tint = if (failed) C.Error else C.TextDim, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(
                researchLabel(r), color = if (failed) C.Error else C.Text, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
        }
        val plan = r.plan
        // The plan is what the user is approving, so it is always visible while awaiting approval.
        if (plan != null && (open || r.awaitingApproval)) {
            if (plan.title.isNotBlank()) {
                Text(plan.title, color = C.Text, fontSize = 14.5.sp, modifier = Modifier.padding(top = 10.dp))
            }
            plan.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(top = 8.dp)) {
                    Text("${i + 1}.", color = C.TextFaint, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(step.title, color = C.Text, fontSize = 14.sp)
                        if (step.query.isNotBlank()) Text(step.query, color = C.TextFaint, fontSize = 12.5.sp)
                    }
                }
            }
        }
        if (!r.terminal && r.status != "cancelling") {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.awaitingApproval) PillButton(stringResource(R.string.approve_and_start), primary = true, onClick = onApprove)
                PillButton(stringResource(R.string.cancel), primary = false, onClick = onCancel)
            }
        }
    }
}

/** Code blocks are always on a dark surface, so use a dark syntax theme regardless of system mode. */
private val codeHighlights = Highlights.Builder().theme(SyntaxThemes.atom(darkMode = true))

private val markdownComponents = markdownComponents(
    codeBlock = { MarkdownHighlightedCodeBlock(it.content, it.node, it.typography.code, codeHighlights) },
    codeFence = { MarkdownHighlightedCodeFence(it.content, it.node, it.typography.code, codeHighlights) },
)

/**
 * Markdown that keeps showing the previous render while new text is parsed in the background.
 * Without retainState every streamed token briefly renders an empty block, collapsing the
 * message height and making the list jump.
 */
@Composable
private fun StableMarkdown(text: String) {
    val state = rememberMarkdownState(text, retainState = true)
    Markdown(markdownState = state, components = markdownComponents, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(C.Text))
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) =
    IconTap(icon, description, size = 36.dp, iconSize = 18.dp, tint = C.TextFaint, onClick = onClick)

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

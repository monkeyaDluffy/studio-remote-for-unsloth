package io.github.monkeyadluffy.studioremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.Conversation
import io.github.monkeyadluffy.studioremote.data.UiMessage
import kotlinx.coroutines.launch

/** Full-screen pages reachable from the drawer, besides the chat. */
enum class Page { Chat, Models, Images, Videos, Speech }

@Composable
fun MainScreen(vm: AppViewModel, state: UiState) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(Page.Chat) }
    var showModels by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UiMessage?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val screenWidth = windowWidth()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(drawer.isOpen) { if (drawer.isOpen) vm.refreshThreads() }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(!drawer.isOpen && page != Page.Chat) { page = Page.Chat }

    fun closeDrawerAfter(action: () -> Unit) {
        action()
        scope.launch { drawer.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = C.Bg,
                drawerShape = RectangleShape,
                modifier = Modifier.widthIn(max = screenWidth * 0.88f),
                windowInsets = WindowInsets(0),
            ) {
                ChatDrawer(
                    vm = vm,
                    state = state,
                    onOpenThread = { id -> closeDrawerAfter { page = Page.Chat; vm.openThread(id) } },
                    onNewChat = { closeDrawerAfter { page = Page.Chat; vm.newChat() } },
                    onSettings = { showSettings = true },
                    onOpenPage = { p -> closeDrawerAfter { page = p } },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            val openMenu = { scope.launch { drawer.open() }; Unit }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                when (page) {
                    Page.Chat -> {
                        TopBar(
                            state = state,
                            onMenu = openMenu,
                            onModels = { showModels = true; vm.loadModels() },
                            onNewChat = vm::newChat,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                state.messagesLoading -> Box(Modifier.align(Alignment.Center)) { Spinner(28.dp, stroke = 2.dp) }
                                state.messages.isEmpty() -> EmptyState(state) { showModels = true; vm.loadModels() }
                                else -> MessageList(vm, state, onEdit = { editing = it })
                            }
                        }
                        Composer(vm, state)
                    }
                    Page.Models -> ModelsScreen(vm, state, openMenu)
                    Page.Images -> ImageScreen(vm, openMenu)
                    Page.Videos -> VideoScreen(vm, openMenu)
                    Page.Speech -> SpeechScreen(vm, state, openMenu) { page = Page.Models }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
        }
    }

    if (showModels) {
        ModelSheet(vm, state, onManage = { showModels = false; vm.clearVariants(); page = Page.Models }) {
            showModels = false
            vm.clearVariants()
        }
    }
    if (showSettings) SettingsSheet(vm, state) { showSettings = false }
    editing?.let { m -> EditMessageDialog(m, onDismiss = { editing = null }) { text -> vm.editMessage(m.id, text); editing = null } }
}

@Composable
private fun EditMessageDialog(m: UiMessage, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember(m.id) { mutableStateOf(m.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHigh,
        title = { Text("Mesajı düzenle") },
        text = {
            Column {
                Text(
                    "Yeni bir dal olarak gönderilir; eski cevap kaybolmaz, oklarla geri dönebilirsin.",
                    color = C.TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp),
                )
                PillField(text, { text = it }, "Mesaj", singleLine = false, container = C.Surface)
            }
        },
        confirmButton = { TextButton(onClick = { onSend(text) }, enabled = text.isNotBlank()) { Text("Gönder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = C.TextDim) } },
    )
}

@Composable
private fun TopBar(state: UiState, onMenu: () -> Unit, onModels: () -> Unit, onNewChat: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(Icons.Rounded.Menu, "Menü", onClick = onMenu)
        Spacer(Modifier.size(10.dp))
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(C.Surface)
                .clickable(onClick = onModels)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.modelSwitching != null) {
                Spinner(14.dp, color = C.Text, stroke = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            val label = if (state.modelSwitching != null) "Yükleniyor…" else state.status?.label ?: "Model seç"
            Text(
                middleEllipsize(label, 26),
                color = C.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.size(6.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(10.dp))
        CircleButton(Icons.Outlined.EditNote, "Yeni sohbet", onClick = onNewChat)
    }
}

/** "hf.co/mradermacher/…-GGUF:Q4_K_M" style: keep both ends, which carry the useful bits. */
private fun middleEllipsize(s: String, max: Int): String {
    if (s.length <= max) return s
    val tail = max / 2
    return s.take(max - tail - 1) + "…" + s.takeLast(tail)
}

@Composable
private fun EmptyState(state: UiState, onPickModel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Hello, ${state.settings.shownName}",
            style = MaterialTheme.typography.headlineMedium,
            color = C.Text,
            textAlign = TextAlign.Center,
        )
        val noModel = state.status != null && state.status.modelId == null && state.modelSwitching == null
        if (noModel) {
            Spacer(Modifier.size(28.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(C.Surface)
                    .clickable(onClick = onPickModel)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text("Yüklü model yok", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bir model seçmek için dokun", color = C.TextDim, fontSize = 15.sp)
                    Spacer(Modifier.size(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.TextDim, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Where [m] sits among its alternatives, or null when it has none. */
private fun branchOf(c: Conversation, m: UiMessage): Branch? {
    val siblings = c.siblings(m)
    if (siblings.size < 2) return null
    val i = siblings.indexOfFirst { it.id == m.id }
    return Branch(i, siblings.size, siblings.getOrNull(i - 1)?.id, siblings.getOrNull(i + 1)?.id)
}

@Composable
private fun MessageList(vm: AppViewModel, state: UiState, onEdit: (UiMessage) -> Unit) {
    val listState = rememberLazyListState()
    val lastAssistantId = state.messages.lastOrNull { !it.isUser }?.id
    // Reverse layout keeps the newest message pinned to the bottom while it grows.
    LaunchedEffect(state.messages.size) { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(state.messages.asReversed(), key = { it.id }) { m ->
            val branch = branchOf(state.conversation, m)
            if (m.isUser) {
                UserMessage(
                    m, branch,
                    canEdit = !state.generating,
                    onEdit = { onEdit(m) },
                    onSelectBranch = vm::selectBranch,
                )
            } else {
                AssistantMessage(
                    m = m,
                    branch = branch,
                    canSwitchBranch = !state.generating,
                    onSelectBranch = vm::selectBranch,
                    canRegenerate = m.id == lastAssistantId && !state.generating,
                    onRegenerate = vm::regenerate,
                    onToolAnswer = { toolId, allow -> vm.answerTool(m.id, toolId, allow) },
                    onApproveResearch = { vm.approveResearch(m.id) },
                    onCancelResearch = { vm.cancelResearch(m.id) },
                )
            }
        }
    }
}

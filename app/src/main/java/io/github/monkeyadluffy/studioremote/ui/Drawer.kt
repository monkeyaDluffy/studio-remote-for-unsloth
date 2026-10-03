package io.github.monkeyadluffy.studioremote.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.ChatProject
import io.github.monkeyadluffy.studioremote.data.ChatThread
import kotlinx.coroutines.launch

@Composable
fun ChatDrawer(
    vm: AppViewModel,
    state: UiState,
    onOpenThread: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onOpenPage: (Page) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var foldersOpen by rememberSaveable { mutableStateOf(true) }
    var recentOpen by rememberSaveable { mutableStateOf(true) }
    val openFolders = remember { mutableStateOf(setOf<String>()) }
    var renaming by remember { mutableStateOf<ChatThread?>(null) }
    var deleting by remember { mutableStateOf<ChatThread?>(null) }
    var moving by remember { mutableStateOf<ChatThread?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<ChatProject?>(null) }
    var deletingFolder by remember { mutableStateOf<ChatProject?>(null) }

    fun export(t: ChatThread) = scope.launch {
        vm.exportThread(t.id)
            .onSuccess { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, t.title)
                    .putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Sohbeti paylaş"))
            }
            .onFailure { Toast.makeText(context, "Dışa aktarılamadı: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    @Composable
    fun Thread(t: ChatThread, indent: Dp) = ThreadRow(
        t, t.id == state.currentThreadId, indent,
        onClick = { onOpenThread(t.id) },
        onRename = { renaming = t },
        onMove = { moving = t },
        onExport = { export(t) },
        onDelete = { deleting = t },
    )

    val filtered = state.threads.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val inProjects = filtered.groupBy { it.projectId }
    val recent = filtered.filter { t -> t.projectId == null || state.projects.none { it.id == t.projectId } }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header: avatar · search · new chat
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(state.settings.shownName, onClick = onSettings)
            Spacer(Modifier.weight(1f))
            CircleButton(
                if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Ara",
                size = 56.dp,
            ) {
                searching = !searching
                if (!searching) query = ""
            }
            Spacer(Modifier.width(12.dp))
            CircleButton(Icons.Rounded.Add, "Yeni sohbet", size = 56.dp, iconSize = 30.dp, onClick = onNewChat)
        }

        AnimatedVisibility(searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 17.sp),
                cursorBrush = SolidColor(C.Text),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.Surface)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        if (query.isEmpty()) Text("Sohbetlerde ara", color = C.TextFaint, fontSize = 17.sp)
                        inner()
                    }
                },
            )
        }

        StudioTiles(onOpenPage)

        if (state.threadsLoading && state.threads.isEmpty()) LoadingBar()

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (state.projects.isNotEmpty() || !searching) {
                item {
                    SectionHeader("Folders", foldersOpen, { foldersOpen = !foldersOpen }) {
                        IconTap(Icons.Outlined.CreateNewFolder, "Yeni klasör", size = 40.dp, iconSize = 22.dp) { creatingFolder = true }
                    }
                }
                if (foldersOpen) {
                    if (state.projects.isEmpty()) {
                        item {
                            Text(
                                "Henüz klasör yok",
                                color = C.TextFaint, fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                            )
                        }
                    }
                    state.projects.forEach { p ->
                        val open = p.id in openFolders.value
                        val threads = inProjects[p.id].orEmpty()
                        item(key = "p-${p.id}") {
                            FolderRow(
                                p, threads.size, open,
                                onToggle = { openFolders.value = if (open) openFolders.value - p.id else openFolders.value + p.id },
                                onRename = { renamingFolder = p },
                                onDelete = { deletingFolder = p },
                            )
                        }
                        if (open) {
                            if (threads.isEmpty()) {
                                item(key = "pe-${p.id}") {
                                    Text(
                                        "Boş. Bir sohbeti buraya taşımak için sohbete uzun bas.",
                                        color = C.TextFaint, fontSize = 14.sp,
                                        modifier = Modifier.padding(start = 64.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
                                    )
                                }
                            }
                            items(threads, key = { "pt-${it.id}" }) { t -> Thread(t, 64.dp) }
                        }
                    }
                }
            }

            item { SectionHeader("Recent", recentOpen, { recentOpen = !recentOpen }) }
            if (recentOpen) {
                if (recent.isEmpty() && !state.threadsLoading) {
                    item {
                        Text(
                            if (query.isBlank()) "Henüz sohbet yok" else "Sonuç bulunamadı",
                            color = C.TextFaint, fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                        )
                    }
                }
                items(recent, key = { it.id }) { t -> Thread(t, 28.dp) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    renaming?.let { t ->
        NameDialog("Yeniden adlandır", t.title, "Başlık", onDismiss = { renaming = null }) {
            vm.renameThread(t.id, it)
            renaming = null
        }
    }
    deleting?.let { t ->
        ConfirmDialog(
            "Sohbet silinsin mi?", "\"${t.title.take(60)}\" bilgisayardaki Unsloth'tan da silinecek.",
            onDismiss = { deleting = null },
        ) {
            vm.deleteThread(t.id)
            deleting = null
        }
    }
    moving?.let { t ->
        MoveDialog(t, state.projects, onDismiss = { moving = null }) { projectId ->
            vm.moveThread(t.id, projectId)
            moving = null
        }
    }
    if (creatingFolder) {
        NameDialog("Yeni klasör", "", "Klasör adı", onDismiss = { creatingFolder = false }) {
            vm.createProject(it)
            creatingFolder = false
        }
    }
    renamingFolder?.let { p ->
        NameDialog("Klasörü yeniden adlandır", p.name, "Klasör adı", onDismiss = { renamingFolder = null }) {
            vm.renameProject(p.id, it)
            renamingFolder = null
        }
    }
    deletingFolder?.let { p ->
        val count = state.threads.count { it.projectId == p.id }
        ConfirmDialog(
            "Klasör silinsin mi?",
            if (count == 0) "\"${p.name}\" silinecek."
            else "\"${p.name}\" ve içindeki $count sohbet bilgisayardaki Unsloth'tan da silinecek. Sohbetleri saklamak istiyorsan önce klasörden çıkar.",
            onDismiss = { deletingFolder = null },
        ) {
            vm.deleteProject(p.id)
            deletingFolder = null
        }
    }
}

/** Shortcuts to Studio's non-chat pages. */
@Composable
private fun StudioTiles(onOpenPage: (Page) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudioTile(Icons.Outlined.ViewInAr, "Modeller", Modifier.weight(1f)) { onOpenPage(Page.Models) }
        StudioTile(Icons.Outlined.Image, "Görsel", Modifier.weight(1f)) { onOpenPage(Page.Images) }
        StudioTile(Icons.Outlined.Movie, "Video", Modifier.weight(1f)) { onOpenPage(Page.Videos) }
        StudioTile(Icons.Outlined.RecordVoiceOver, "Ses", Modifier.weight(1f)) { onOpenPage(Page.Speech) }
    }
}

@Composable
private fun StudioTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(C.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = C.TextDim, fontSize = 12.5.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(p: ChatProject, count: Int, open: Boolean, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle, onLongClick = { menu = true })
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = C.TextDim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(p.name, style = DrawerItemStyle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", color = C.TextFaint, fontSize = 14.sp)
        }
        ActionMenu(
            menu, { menu = false },
            listOf(
                MenuAction("Yeniden adlandır", Icons.Rounded.Edit, onClick = onRename),
                MenuAction("Sil", Icons.Rounded.Delete, danger = true, onClick = onDelete),
            ),
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, placeholder: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHigh,
        title = { Text(title) },
        text = { PillField(text, { text = it }, placeholder) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = C.TextDim) } },
    )
}

@Composable
private fun MoveDialog(t: ChatThread, projects: List<ChatProject>, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHigh,
        title = { Text("Klasöre taşı") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (projects.isEmpty()) {
                    Text("Henüz klasör yok. Klasörler başlığındaki + ile oluşturabilirsin.", color = C.TextDim)
                }
                projects.forEach { p ->
                    MoveOption(Icons.Outlined.Folder, p.name, selected = t.projectId == p.id) { onMove(p.id) }
                }
                if (t.projectId != null) {
                    MoveOption(Icons.Outlined.FolderOff, "Klasörden çıkar", selected = false) { onMove(null) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = C.TextDim) } },
    )
}

@Composable
private fun MoveOption(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !selected, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected) C.TextFaint else C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, "Şu an burada", tint = C.TextFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 28.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Text, modifier = Modifier.size(26.dp).rotate(rotation))
        Spacer(Modifier.width(8.dp))
        Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    t: ChatThread,
    selected: Boolean,
    indent: Dp,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) C.Surface else C.Bg)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(start = indent - 12.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        ) {
            Text(
                t.title.ifBlank { "Yeni sohbet" },
                style = DrawerItemStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ActionMenu(
            menu, { menu = false },
            listOf(
                MenuAction("Yeniden adlandır", Icons.Rounded.Edit, onClick = onRename),
                MenuAction("Klasöre taşı", Icons.AutoMirrored.Outlined.DriveFileMove, onClick = onMove),
                MenuAction("Dışa aktar / paylaş", Icons.Rounded.Share, onClick = onExport),
                MenuAction("Sil", Icons.Rounded.Delete, danger = true, onClick = onDelete),
            ),
        )
    }
}

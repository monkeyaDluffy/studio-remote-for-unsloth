package io.github.monkeyadluffy.studioremote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.DownloadJob
import io.github.monkeyadluffy.studioremote.HubState
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.CachedRepo
import io.github.monkeyadluffy.studioremote.data.GgufVariant
import io.github.monkeyadluffy.studioremote.data.TASK_IMAGE
import io.github.monkeyadluffy.studioremote.data.TASK_SPEECH
import io.github.monkeyadluffy.studioremote.data.TASK_VIDEO
import io.github.monkeyadluffy.studioremote.downloadKey

private fun taskLabel(task: String?): String = when (task) {
    TASK_IMAGE -> "Görsel"
    TASK_VIDEO -> "Video"
    TASK_SPEECH -> "Ses"
    "automatic-speech-recognition" -> "Sesten yazıya"
    else -> "Sohbet"
}

/** Model management: downloaded models, Hugging Face search, downloads, deletion and loading. */
@Composable
fun ModelsScreen(vm: AppViewModel, state: UiState, onMenu: () -> Unit) {
    val hub by vm.hub.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        vm.hub.refresh()
        vm.refreshStatus()
    }
    BackHandler(hub.repo != null) { vm.hub.closeRepo() }

    val repo = hub.repo
    if (repo != null) {
        RepoDetail(vm, state, hub, repo)
        return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Modeller", onMenu)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("İndirilenler", tab == 0) { tab = 0 }
            TabChip("Hugging Face'te ara", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) LocalTab(vm, state, hub) else SearchTab(vm, hub)
    }
}

@Composable
private fun LocalTab(vm: AppViewModel, state: UiState, hub: HubState) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { ActiveModelCard(vm, state) }
        if (hub.downloads.isNotEmpty()) {
            item { ListLabel("İndiriliyor") }
            items(hub.downloads.values.toList(), key = { it.key }) { job ->
                DownloadRow(job, onCancel = { vm.hub.cancel(job) })
            }
        }
        item { ListLabel("Bilgisayardaki modeller") }
        when {
            hub.localLoading && hub.local.isEmpty() -> item { SheetLoading() }
            hub.local.isEmpty() -> item { SheetEmpty("İndirilmiş model yok. Hugging Face'te arayıp indirebilirsin.") }
        }
        items(hub.local, key = { it.repoId }) { m -> LocalRow(m) { vm.hub.openRepo(m.repoId) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActiveModelCard(vm: AppViewModel, state: UiState) {
    val st = state.status
    val label = st?.label
    InfoCard("Yüklü model") {
        when {
            state.modelSwitching != null -> BusyRow("Yükleniyor: ${state.modelSwitching}")
            st == null || label == null -> Text("Yok", color = C.Text, fontSize = 16.sp)
            else -> {
                Text(label, color = C.Text, fontSize = 16.sp)
                val details = buildList {
                    st.contextLength?.let { add("Bağlam: ${formatTokens(it)}") }
                    st.cacheTypeKv?.let { add("KV: $it") }
                    if (st.isAudio) add("Ses modeli")
                }
                if (details.isNotEmpty()) Text(details.joinToString(" · "), color = C.TextFaint, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                PillButton("Bellekten çıkar", primary = false, onClick = vm::unloadModel)
            }
        }
    }
}

@Composable
private fun LocalRow(m: CachedRepo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.repoId.substringAfter('/'), color = C.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${m.repoId.substringBefore('/')} · ${formatSize(m.sizeBytes)} · ${taskLabel(m.task)}",
                color = C.TextFaint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchTab(vm: AppViewModel, hub: HubState) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            PillField(hub.query, vm.hub::search, "Model ara (ör. qwen, gemma, llama)")
        }
        if (hub.searching) LoadingBar()
        LazyColumn(Modifier.fillMaxSize()) {
            if (hub.query.isBlank()) {
                item { SheetEmpty("Hugging Face'teki GGUF modellerinde arar; en çok indirilenler üstte.") }
            } else if (!hub.searching && hub.results.isEmpty()) {
                item { SheetEmpty("Sonuç bulunamadı") }
            }
            items(hub.results, key = { it.id }) { m ->
                val downloaded = hub.local.any { it.repoId.equals(m.id, ignoreCase = true) }
                Row(
                    Modifier.fillMaxWidth().clickable { vm.hub.openRepo(m.id) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.id.substringAfter('/'), color = C.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${m.id.substringBefore('/')} · ${formatCount(m.downloads)} indirme · ${formatCount(m.likes)} beğeni",
                            color = C.TextFaint, fontSize = 13.sp, maxLines = 1,
                        )
                    }
                    if (downloaded) Icon(Icons.Rounded.Check, "İndirilmiş", tint = C.Accent, modifier = Modifier.size(20.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The quantisations of one repo, with download / load / delete actions. */
@Composable
private fun RepoDetail(vm: AppViewModel, state: UiState, hub: HubState, repo: String) {
    var loading by remember { mutableStateOf<GgufVariant?>(null) }
    var deleting by remember { mutableStateOf<GgufVariant?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    val local = hub.local.firstOrNull { it.repoId.equals(repo, ignoreCase = true) }
    val task = local?.task

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", onClick = vm.hub::closeRepo)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.substringAfter('/'), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(repo.substringBefore('/'), color = C.TextFaint, fontSize = 13.sp)
            }
            if (local != null) IconTap(Icons.Rounded.Delete, "Tümünü sil", tint = C.Error) { deletingAll = true }
        }
        if (task == TASK_IMAGE || task == TASK_VIDEO) {
            Text(
                "Bu bir ${taskLabel(task).lowercase()} modeli; yüklemek için menüdeki ${taskLabel(task)} sayfasını kullan.",
                color = C.TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        when {
            hub.variantsLoading -> SheetLoading()
            hub.variants.isEmpty() -> SheetEmpty("Bu depoda GGUF dosyası bulunamadı.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(hub.variants, key = { it.quant }) { v ->
                    val job = hub.downloads[downloadKey(repo, v.quant)]
                    val active = state.status?.modelId.equals(repo, ignoreCase = true) && state.status?.ggufVariant == v.quant
                    VariantRow(
                        v, job, active,
                        canLoad = task != TASK_IMAGE && task != TASK_VIDEO,
                        onDownload = { vm.hub.download(repo, v) },
                        onCancel = { job?.let(vm.hub::cancel) },
                        onLoad = { loading = v },
                        onDelete = { deleting = v },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    loading?.let { v ->
        LoadOptionsDialog(repo, v, onDismiss = { loading = null }) { ctx, layers, kv ->
            vm.switchModel(repo, v.quant, ctx, layers, kv)
            loading = null
        }
    }
    deleting?.let { v ->
        ConfirmDialog(
            "${v.label} silinsin mi?",
            "Bilgisayardan ${formatSize(v.sizeBytes)} yer açılır. Gerekirse tekrar indirebilirsin.",
            onDismiss = { deleting = null },
        ) {
            vm.hub.delete(repo, v.quant)
            deleting = null
        }
    }
    if (deletingAll) {
        ConfirmDialog(
            "Modelin tamamı silinsin mi?",
            "${repo.substringAfter('/')} modelinin bilgisayardaki tüm dosyaları (${formatSize(local?.sizeBytes ?: 0)}) silinecek.",
            onDismiss = { deletingAll = false },
        ) {
            vm.hub.delete(repo, null)
            deletingAll = false
            vm.hub.closeRepo()
        }
    }
}

@Composable
private fun VariantRow(
    v: GgufVariant,
    job: DownloadJob?,
    active: Boolean,
    canLoad: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.label, color = C.Text, fontSize = 16.sp)
                    if (active) {
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Accent))
                    }
                }
                val note = when {
                    v.downloaded -> "İndirildi"
                    v.partial -> "Yarım kaldı, devam ettirilebilir"
                    else -> null
                }
                Text(listOfNotNull(formatSize(v.sizeBytes), note).joinToString(" · "), color = C.TextFaint, fontSize = 13.sp)
            }
            when {
                job != null -> IconTap(Icons.Rounded.Close, "İndirmeyi durdur", size = 40.dp, iconSize = 20.dp, onClick = onCancel)
                v.downloaded -> {
                    if (canLoad && !active) PillButton("Yükle", primary = true, onClick = onLoad)
                    Spacer(Modifier.size(4.dp))
                    IconTap(Icons.Rounded.Delete, "Sil", size = 40.dp, iconSize = 20.dp, onClick = onDelete)
                }
                else -> IconTap(Icons.Rounded.Download, "İndir", size = 40.dp, iconSize = 22.dp, tint = C.Text, onClick = onDownload)
            }
        }
        job?.let { DownloadBar(it) }
    }
}

@Composable
private fun DownloadRow(job: DownloadJob, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                job.repoId.substringAfter('/') + (job.variant?.let { " · $it" } ?: ""),
                color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            IconTap(Icons.Rounded.Close, "İndirmeyi durdur", size = 36.dp, iconSize = 18.dp, onClick = onCancel)
        }
        DownloadBar(job)
    }
}

@Composable
private fun DownloadBar(job: DownloadJob) {
    ThinProgress(job.progress, Modifier.padding(top = 6.dp))
    val total = if (job.expectedBytes > 0) " / ${formatSize(job.expectedBytes)}" else ""
    Text(
        "%${(job.progress * 100).toInt()} · ${formatSize(job.downloadedBytes)}$total",
        color = C.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

private val CONTEXT_CHOICES = listOf(0, 4096, 8192, 16384, 32768, 65536, 131072)
private val KV_CHOICES = listOf<String?>(null, "f16", "q8_0", "q4_0")

/** Advanced load settings, as on the Studio web load dialog. */
@Composable
private fun LoadOptionsDialog(repo: String, v: GgufVariant, onDismiss: () -> Unit, onLoad: (Int, Int, String?) -> Unit) {
    var ctx by remember { mutableIntStateOf(0) }
    var kv by remember { mutableStateOf<String?>(null) }
    var layers by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHigh,
        title = { Text("${repo.substringAfter('/')} · ${v.label}", fontSize = 18.sp) },
        text = {
            Column {
                SectionLabel("Bağlam uzunluğu")
                ChoiceChips(CONTEXT_CHOICES, ctx, { if (it == 0) "Otomatik" else formatTokens(it) }) { ctx = it }
                Text("Uzun bağlam daha çok bellek ister.", color = C.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Spacer(Modifier.height(14.dp))
                SectionLabel("KV önbellek hassasiyeti")
                ChoiceChips(KV_CHOICES, kv, { it ?: "Varsayılan" }) { kv = it }
                Text("q8_0 / q4_0 belleği azaltır, kaliteyi biraz düşürür.", color = C.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Spacer(Modifier.height(14.dp))
                SectionLabel("GPU katman sayısı (boş = otomatik)")
                PillField(
                    layers, { t -> layers = t.filter(Char::isDigit).take(4) }, "Otomatik", container = C.Surface,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onLoad(ctx, layers.toIntOrNull() ?: -1, kv) }) { Text("Yükle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = C.TextDim) } },
    )
}

@Composable
private fun ListLabel(text: String) {
    Text(
        text, color = C.TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}


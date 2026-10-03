package io.github.monkeyadluffy.studioremote.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.MediaPageState
import io.github.monkeyadluffy.studioremote.MediaStudio
import io.github.monkeyadluffy.studioremote.data.GalleryItem
import io.github.monkeyadluffy.studioremote.data.MediaKind
import io.github.monkeyadluffy.studioremote.data.galleryFilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------- images ----------------

private class ImageSize(val label: String, val w: Int, val h: Int) {
    override fun toString() = "$label $w×$h"
}

private val IMAGE_SIZES = listOf(ImageSize("Kare", 1024, 1024), ImageSize("Dikey", 768, 1344), ImageSize("Yatay", 1344, 768), ImageSize("Küçük", 512, 512))
private val IMAGE_STEPS = listOf(4, 9, 20, 28)

@Composable
fun ImageScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val page by vm.media.state(MediaKind.Image).collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    var sizeIndex by rememberSaveable { mutableIntStateOf(0) }
    var steps by rememberSaveable { mutableIntStateOf(IMAGE_STEPS[1]) }
    val size = IMAGE_SIZES[sizeIndex]
    var viewing by remember { mutableStateOf<GalleryItem?>(null) }
    LaunchedEffect(Unit) { vm.media.open(MediaKind.Image) }

    MediaGrid(columns = 2) {
        fullRow { ScreenHeader("Görsel üret", onMenu) }
        fullRow { MediaModelCard(vm, MediaKind.Image, page, "görsel") }
        fullRow {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PillField(prompt, { prompt = it }, "Ne çizilsin? (İngilizce daha iyi sonuç verir)", singleLine = false)
                Spacer(Modifier.height(10.dp))
                ChoiceChips(IMAGE_SIZES, size, ImageSize::toString) { sizeIndex = IMAGE_SIZES.indexOf(it) }
                Spacer(Modifier.height(6.dp))
                ChoiceChips(IMAGE_STEPS, steps, { "$it adım" }) { steps = it }
                Spacer(Modifier.height(12.dp))
                GenerateButton(page, enabled = prompt.isNotBlank() && page.status?.loaded == true, onCancel = { vm.media.cancel(MediaKind.Image) }) {
                    vm.media.generateImage(prompt.trim(), size.w, size.h, steps, seed = null)
                }
            }
        }
        galleryHeader(page, "Henüz görsel yok")
        items(page.gallery, key = { it.id }) { item ->
            RemoteImage(
                vm.media, galleryFilePath(MediaKind.Image.base, item.id, thumb = true),
                Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable { viewing = item },
                ContentScale.Crop,
            )
        }
    }

    viewing?.let { item ->
        val path = galleryFilePath(MediaKind.Image.base, item.id)
        Viewer(
            item,
            onDismiss = { viewing = null },
            onSave = { saveToPhone(vm.media, it, path, "unsloth-${item.id}.png", "image/png") },
            onDelete = { vm.media.delete(MediaKind.Image, item); viewing = null },
        ) {
            RemoteImage(vm.media, path, Modifier.fillMaxWidth(), ContentScale.FillWidth)
        }
    }
}

// ---------------- videos ----------------

private val VIDEO_FRAMES = listOf<Int?>(null, 49, 81, 121)

@Composable
fun VideoScreen(vm: AppViewModel, onMenu: () -> Unit) {
    val page by vm.media.state(MediaKind.Video).collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    var frames by rememberSaveable { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<GalleryItem?>(null) }
    LaunchedEffect(Unit) { vm.media.open(MediaKind.Video) }

    MediaGrid(columns = 1) {
        fullRow { ScreenHeader("Video üret", onMenu) }
        fullRow { MediaModelCard(vm, MediaKind.Video, page, "video") }
        fullRow {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PillField(prompt, { prompt = it }, "Videoda ne olsun?", singleLine = false)
                Spacer(Modifier.height(10.dp))
                ChoiceChips(VIDEO_FRAMES, frames, { it?.let { n -> "$n kare" } ?: "Varsayılan uzunluk" }) { frames = it }
                Text(
                    "Video üretmek dakikalar sürebilir; uygulamayı kapatsan da bilgisayarda devam eder.",
                    color = C.TextFaint, fontSize = 12.5.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
                GenerateButton(page, enabled = prompt.isNotBlank() && page.status?.loaded == true, onCancel = { vm.media.cancel(MediaKind.Video) }) {
                    vm.media.generateVideo(prompt.trim(), null, null, frames, null)
                }
            }
        }
        galleryHeader(page, "Henüz video yok")
        items(page.gallery, key = { it.id }) { item -> VideoRow(item) { viewing = item } }
    }

    viewing?.let { item ->
        val path = galleryFilePath(MediaKind.Video.base, item.id)
        Viewer(
            item,
            onDismiss = { viewing = null },
            onSave = { saveToPhone(vm.media, it, path, "unsloth-${item.id}.mp4", "video/mp4") },
            onDelete = { vm.media.delete(MediaKind.Video, item); viewing = null },
        ) {
            VideoPlayer(vm.media, path, "unsloth-${item.id}.mp4")
        }
    }
}

@Composable
private fun VideoPlayer(media: MediaStudio, path: String, name: String) {
    val file by produceState<File?>(null, path) {
        value = runCatching { media.fileOnDisk(path, name) }.getOrNull()
    }
    val f = file
    if (f == null) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Spinner(28.dp, stroke = 2.dp) }
        return
    }
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                setVideoPath(f.absolutePath)
                setOnPreparedListener { mp -> mp.isLooping = true; start() }
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 520.dp),
    )
}

@Composable
private fun VideoRow(item: GalleryItem, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, null, tint = C.Text, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.prompt.orEmpty().ifBlank { "Video" }, color = C.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                item.durationS?.let(::formatSeconds),
                if (item.width != null && item.height != null) "${item.width}×${item.height}" else null,
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = C.TextFaint, fontSize = 12.5.sp)
        }
    }
}

// ---------------- shared by the media pages ----------------

/** A row spanning the whole width of a [MediaGrid]. */
fun LazyGridScope.fullRow(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** The scrolling page body: full-width rows on top, gallery cells below. */
@Composable
fun MediaGrid(columns: Int, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp, start = 4.dp, end = 4.dp),
        content = content,
    )
}

fun LazyGridScope.galleryHeader(loading: Boolean, empty: Boolean, emptyText: String) {
    fullRow {
        Row(Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Galeri", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (loading) Spinner(16.dp)
        }
    }
    if (empty && !loading) fullRow { SheetEmpty(emptyText) }
}

private fun LazyGridScope.galleryHeader(page: MediaPageState, emptyText: String) =
    galleryHeader(page.galleryLoading, page.gallery.isEmpty(), emptyText)

/** Which image/video model is loaded, the downloaded ones to pick from, and load progress. */
@Composable
private fun MediaModelCard(vm: AppViewModel, kind: MediaKind, page: MediaPageState, noun: String) {
    val status = page.status
    val loaded = status?.loaded == true
    InfoCard("Model") {
        when {
            page.loadingModel != null -> {
                BusyRow("Yükleniyor: ${page.loadingModel}")
                page.loadFraction?.let { ThinProgress(it, Modifier.padding(top = 8.dp)) }
            }
            loaded -> {
                Text(status.label.orEmpty(), color = C.Text, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                PillButton("Bellekten çıkar", primary = false) { vm.media.unload(kind) }
            }
            else -> Text("Yüklü $noun modeli yok. Yüklemek sohbet modelini bellekten çıkarabilir.", color = C.TextDim, fontSize = 14.sp)
        }
        if (page.loadingModel != null) return@InfoCard
        val others = page.models.filter { !loaded || !it.repoId.equals(status.repoId, ignoreCase = true) }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(if (loaded) "Başka model yükle" else "İndirilmiş $noun modelleri", color = C.TextFaint, fontSize = 13.sp)
            others.forEach { m -> PickRow(m.repoId.substringAfter('/'), formatSize(m.sizeBytes)) { vm.media.pickModel(kind, m) } }
        } else if (!loaded) {
            Spacer(Modifier.height(6.dp))
            Text("İndirilmiş $noun modeli bulunamadı; web arayüzünden indirebilirsin.", color = C.TextFaint, fontSize = 13.sp)
        }
    }

    page.picking?.let { (repo, variants) ->
        AlertDialog(
            onDismissRequest = { vm.media.closePicker(kind) },
            containerColor = C.SurfaceHigh,
            title = { Text(repo.substringAfter('/'), fontSize = 18.sp) },
            text = {
                Column {
                    variants.forEach { v ->
                        PickRow(v.label, formatSize(v.sizeBytes), action = "") {
                            vm.media.load(kind, repo, v.filename, "${repo.substringAfter('/')} · ${v.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.media.closePicker(kind) }) { Text("İptal", color = C.TextDim) } },
        )
    }
}

/** "Üret", or the render's progress with a cancel button while one is running. */
@Composable
private fun GenerateButton(page: MediaPageState, enabled: Boolean, onCancel: () -> Unit, onClick: () -> Unit) {
    if (!page.generating) {
        PrimaryButton("Üret", enabled = enabled, onClick = onClick)
        return
    }
    val p = page.progress
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner(16.dp)
            Spacer(Modifier.size(10.dp))
            val text = buildString {
                append("Üretiliyor")
                if (p != null && p.totalSteps > 0) append(" · adım ${p.step}/${p.totalSteps}")
                p?.etaSeconds?.let { append(" · ~${it.toInt()} sn kaldı") }
            }
            Text(text, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            PillButton("İptal", primary = false, onClick = onCancel)
        }
        if (p != null && p.fraction > 0) ThinProgress(p.fraction, Modifier.padding(top = 10.dp))
    }
}

/** Full-screen view of one gallery item with save and delete. */
@Composable
private fun Viewer(
    item: GalleryItem,
    onDismiss: () -> Unit,
    onSave: suspend (Context) -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.Close, "Kapat", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                CircleButton(Icons.Rounded.Download, "Telefona kaydet") { scope.launch { onSave(context) } }
                Spacer(Modifier.size(10.dp))
                CircleButton(Icons.Rounded.Delete, "Sil") { confirmDelete = true }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
            item.prompt?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = C.TextDim, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(16.dp))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Silinsin mi?", "Bilgisayardaki Studio galerisinden de silinecek.", onDismiss = { confirmDelete = false }) {
            confirmDelete = false
            onDelete()
        }
    }
}

/** An authenticated Studio image, fetched once and cached. */
@Composable
private fun RemoteImage(media: MediaStudio, path: String, modifier: Modifier, scale: ContentScale) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = runCatching {
            val bytes = media.fileBytes(path)
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }.getOrNull()
    }
    val b = bitmap
    if (b != null) Image(b, null, modifier, contentScale = scale)
    else Box(modifier.background(C.Surface).heightIn(min = 120.dp), contentAlignment = Alignment.Center) { Spinner(18.dp) }
}

/**
 * Copies a gallery file into the phone's Pictures/Unsloth or Movies/Unsloth. Goes through the
 * on-disk copy (the one the video player already fetched) so a clip is never held in memory.
 */
private suspend fun saveToPhone(media: MediaStudio, context: Context, path: String, name: String, mime: String) {
    val app = context.applicationContext
    val video = mime.startsWith("video")
    val ok = runCatching {
        val file = media.fileOnDisk(path, name)
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Unsloth")
            }
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = app.contentResolver.insert(collection, values) ?: error("kaydedilemedi")
            app.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("kaydedilemedi")
        }
    }.isSuccess
    Toast.makeText(app, if (ok) "Telefona kaydedildi" else "Kaydedilemedi", Toast.LENGTH_SHORT).show()
}

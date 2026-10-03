package io.github.monkeyadluffy.studioremote.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.MediaStudio
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.GalleryItem
import io.github.monkeyadluffy.studioremote.data.galleryFilePath
import kotlinx.coroutines.launch
import java.io.File

/** Text to speech with the speech model loaded in Studio, plus the audio gallery. */
@Composable
fun SpeechScreen(vm: AppViewModel, state: UiState, onMenu: () -> Unit, onOpenModels: () -> Unit) {
    val speech by vm.media.speech.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val player = rememberAudioPlayer()
    LaunchedEffect(Unit) {
        vm.media.openSpeech()
        vm.refreshStatus()
    }
    val st = state.status
    val ready = st?.isAudio == true || st?.audioType != null

    MediaGrid(columns = 1) {
        fullRow { ScreenHeader("Metni sese çevir", onMenu) }
        fullRow {
            InfoCard("Ses modeli") {
                if (ready) {
                    Text(st?.label.orEmpty(), color = C.Text, fontSize = 16.sp)
                    return@InfoCard
                }
                Text(
                    "Yüklü model bir ses modeli değil. Bir ses modeli yüklediğinde sohbet modeli bellekten çıkar; " +
                        "sohbete dönünce tekrar sohbet modeli seçmen gerekir.",
                    color = C.TextDim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (speech.models.isEmpty()) {
                    Text("İndirilmiş ses modeli yok. Modeller sayfasından \"tts\" diye arayabilirsin.", color = C.TextFaint, fontSize = 13.sp)
                }
                speech.models.forEach { m ->
                    PickRow(m.repoId.substringAfter('/'), formatSize(m.sizeBytes)) {
                        vm.hub.openRepo(m.repoId)
                        onOpenModels()
                    }
                }
            }
        }
        fullRow {
            Column(Modifier.padding(horizontal = 16.dp)) {
                PillField(text, { text = it }, "Seslendirilecek metin", singleLine = false)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Seslendir", enabled = ready && text.isNotBlank(), loading = speech.generating) { vm.media.speak(text) }
                speech.lastAudio?.let { file ->
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayButton(player.playing == file.absolutePath) { player.toggle(file) }
                        Text("Son üretilen ses", color = C.Text, fontSize = 15.sp)
                    }
                }
            }
        }
        galleryHeader(loading = false, empty = speech.gallery.isEmpty(), emptyText = "Henüz ses yok")
        items(speech.gallery, key = { it.id }) { item ->
            AudioRow(vm.media, item, player) { vm.media.deleteAudio(item) }
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) =
    IconTap(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Durdur" else "Oynat", tint = C.Text, onClick = onClick)

@Composable
private fun AudioRow(media: MediaStudio, item: GalleryItem, player: AudioPlayer, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var fetching by remember { mutableStateOf(false) }
    val name = "audio-${item.id}.wav"
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (fetching) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Spinner(18.dp) }
        } else {
            PlayButton(player.playing?.endsWith(name) == true) {
                scope.launch {
                    fetching = true
                    val message = runCatching { media.fileOnDisk(galleryFilePath("audio", item.id), name) }
                        .fold({ if (player.toggle(it)) null else "Ses çalınamadı" }, { "Ses indirilemedi" })
                    fetching = false
                    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.prompt.orEmpty().ifBlank { "Ses" }, color = C.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.durationS?.let { Text(formatSeconds(it), color = C.TextFaint, fontSize = 12.5.sp) }
        }
        IconTap(Icons.Rounded.Delete, "Sil", size = 40.dp, iconSize = 20.dp, onClick = onDelete)
    }
}

/** Plays one file at a time; [playing] is the path of the file playing. */
class AudioPlayer {
    private var player: MediaPlayer? = null
    var playing by mutableStateOf<String?>(null)
        private set

    /** Starts [file], or stops it if it is the one playing; false when it can't be played. */
    fun toggle(file: File): Boolean {
        val wasThis = playing == file.absolutePath
        release()
        if (wasThis) return true
        // A file that isn't playable audio (e.g. an error page) must not take the app down.
        val p = MediaPlayer()
        try {
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { release() }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            p.release()
            file.delete()
            return false
        }
        player = p
        playing = file.absolutePath
        return true
    }

    fun release() {
        player?.release()
        player = null
        playing = null
    }
}

@Composable
private fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { AudioPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}

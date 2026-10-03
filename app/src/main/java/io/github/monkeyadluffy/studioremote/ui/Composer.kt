package io.github.monkeyadluffy.studioremote.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.R
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.PendingFile
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val DOCUMENT_TYPES = arrayOf(
    "application/pdf", "text/*", "application/json", "application/epub+zip", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "*/*",
)

private enum class SendMode { Voice, Send, Stop }

/** Records the microphone to an AAC file for Studio's Whisper. */
private class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private val file = File(context.cacheDir, "dictation.m4a")

    fun start() {
        file.delete()
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = r.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    /** Stops and returns the recording, or null when nothing usable was captured. */
    fun stop(): ByteArray? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess // throws when stopped right after starting
        r.release()
        return if (ok && file.length() > 0) file.readBytes() else null
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null
    }
}

/** The bottom input: attachments, active options, the text field and the send/stop button. */
@Composable
fun Composer(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val images = remember { mutableStateListOf<String>() }
    var showPlus by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    fun startRecording() {
        try {
            recorder.start()
            recording = true
        } catch (e: Exception) {
            Toast.makeText(context, res.getString(R.string.mic_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        recording = false
        val audio = recorder.stop() ?: return
        transcribing = true
        scope.launch {
            val spoken = vm.transcribe(audio)
            if (!spoken.isNullOrBlank()) text = if (text.isBlank()) spoken else "$text $spoken"
            transcribing = false
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, res.getString(R.string.mic_permission_needed), Toast.LENGTH_SHORT).show()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        scope.launch { uris.forEach { uri -> encodeImage(context, uri)?.let(images::add) } }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            uris.forEach { uri ->
                val file = readDocument(context, uri)
                if (file != null) vm.attachFile(file.name, file.mime, file.bytes)
                else Toast.makeText(context, res.getString(R.string.file_unreadable), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) text = if (text.isBlank()) spoken else "$text $spoken"
    }

    fun startSpeech() {
        if (state.settings.studioDictation) {
            when {
                transcribing -> {}
                recording -> stopRecording()
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startRecording()
                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        try {
            speech.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, res.getString(R.string.no_speech_recognizer), Toast.LENGTH_SHORT).show()
        }
    }

    fun send() {
        vm.send(text, images.toList())
        text = ""
        images.clear()
    }

    if (showPlus) {
        PlusSheet(
            vm = vm,
            state = state,
            onPickImage = {
                if (state.status?.isVision == false) {
                    Toast.makeText(context, res.getString(R.string.model_may_not_see_images), Toast.LENGTH_SHORT).show()
                }
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFile = { filePicker.launch(DOCUMENT_TYPES) },
            onDismiss = { showPlus = false },
        )
    }

    val mode = when {
        state.generating -> SendMode.Stop
        recording || transcribing -> SendMode.Voice
        text.isNotBlank() || images.isNotEmpty() -> SendMode.Send
        else -> SendMode.Voice
    }

    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 4.dp)) {
        ComposerChips(vm, state)
        if (recording || transcribing) {
            Row(
                Modifier.padding(start = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (transcribing) Spinner(14.dp) else Box(Modifier.size(10.dp).clip(CircleShape).background(C.Error))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(if (transcribing) R.string.whisper_transcribing else R.string.listening),
                    color = C.TextDim, fontSize = 14.sp,
                )
            }
        }
        if (images.isNotEmpty()) ImageStrip(images, onRemove = images::remove)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(C.Surface)
                .padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconTap(Icons.Rounded.Add, stringResource(R.string.attach_and_options), iconSize = 30.dp) { showPlus = true }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = C.Text, fontSize = 18.sp, lineHeight = 24.sp),
                cursorBrush = SolidColor(C.Text),
                maxLines = 7,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) Text(stringResource(R.string.ask_studio), color = C.TextFaint, fontSize = 19.sp)
                        inner()
                    }
                },
            )

            if (mode == SendMode.Voice && !recording && !transcribing) {
                IconTap(Icons.Rounded.Mic, stringResource(R.string.dictate), onClick = ::startSpeech)
                Spacer(Modifier.size(4.dp))
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (recording) C.Error else Color.White)
                    .clickable {
                        when (mode) {
                            SendMode.Stop -> vm.stop()
                            SendMode.Send -> send()
                            SendMode.Voice -> startSpeech()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "send") { m ->
                    val (icon, label) = when (m) {
                        SendMode.Stop -> Icons.Rounded.Stop to R.string.stop
                        SendMode.Send -> Icons.Rounded.ArrowUpward to R.string.send
                        SendMode.Voice -> if (recording) Icons.Rounded.Stop to R.string.finish_recording else Icons.Rounded.GraphicEq to R.string.dictate
                    }
                    Icon(icon, stringResource(label), tint = Color.Black, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun ImageStrip(images: List<String>, onRemove: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    ) {
        items(images) { img ->
            Box {
                DataImage(img, Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)), ContentScale.Crop)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { onRemove(img) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, stringResource(R.string.remove), tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}

private class Chip(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val busy: Boolean = false,
    val failed: Boolean = false,
    /** Null for informational chips that can't be dismissed. */
    val onRemove: (() -> Unit)? = null,
)

/** Active options and attached files, shown as dismissible chips above the input. */
@Composable
private fun ComposerChips(vm: AppViewModel, state: UiState) {
    val s = state.settings
    val chips = buildList {
        state.pendingFiles.forEach { f ->
            val label = when (f.status) {
                PendingFile.Status.Uploading -> stringResource(R.string.file_uploading, f.name)
                PendingFile.Status.Indexing ->
                    if (f.progress > 0) stringResource(R.string.file_indexing_progress, f.name, (f.progress * 100).toInt())
                    else stringResource(R.string.file_indexing, f.name)
                PendingFile.Status.Failed -> stringResource(R.string.file_failed, f.name)
                PendingFile.Status.Ready -> f.name
            }
            add(Chip(f.key, label, Icons.Outlined.Description, f.busy, f.status == PendingFile.Status.Failed) { vm.removePendingFile(f.key) })
        }
        if (state.threadDocCount > 0 && state.pendingFiles.isEmpty()) {
            add(Chip("docs", pluralStringResource(R.plurals.files_attached, state.threadDocCount, state.threadDocCount), Icons.Outlined.Description))
        }
        if (s.reasoningLevel != "auto") {
            add(Chip("reasoning", stringResource(R.string.thinking_chip, reasoningLabel(s.reasoningLevel)), Icons.Outlined.Lightbulb) { vm.setReasoningLevel("auto") })
        }
        if (s.webSearch) add(Chip("web", stringResource(R.string.web_chip), Icons.Outlined.Public) { vm.setWebSearch(false) })
        if (s.deepResearch) add(Chip("research", stringResource(R.string.deep_research), Icons.Outlined.TravelExplore) { vm.setDeepResearch(false) })
        if (s.codeTools) add(Chip("code", stringResource(R.string.code_chip), Icons.Outlined.Terminal) { vm.setCodeTools(false) })
    }
    if (chips.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(chips, key = { it.key }) { c ->
            val tint = if (c.failed) C.Error else C.TextDim
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (c.failed) C.Error.copy(alpha = 0.15f) else C.Surface)
                    .padding(start = 12.dp, end = if (c.onRemove != null) 6.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (c.busy) Spinner(size = 14.dp) else Icon(c.icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    c.label, color = if (c.failed) C.Error else C.Text, fontSize = 13.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp),
                )
                c.onRemove?.let { remove ->
                    Box(Modifier.padding(start = 2.dp)) {
                        IconTap(Icons.Rounded.Close, stringResource(R.string.remove), size = 24.dp, iconSize = 14.dp, onClick = remove)
                    }
                }
            }
        }
    }
}

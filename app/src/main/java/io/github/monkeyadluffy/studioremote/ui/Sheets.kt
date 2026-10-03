package io.github.monkeyadluffy.studioremote.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.BuildConfig
import io.github.monkeyadluffy.studioremote.R
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.normalizeServerUrl
import java.util.Locale

@Composable
fun ModelSheet(vm: AppViewModel, state: UiState, onManage: () -> Unit, onDismiss: () -> Unit) {
    AppSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            val variants = state.variants
            val active = state.status
            if (variants == null) {
                Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.models), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Text, modifier = Modifier.weight(1f))
                    PillButton(stringResource(R.string.download_manage), primary = false, onClick = onManage)
                }
                active?.label?.let { label ->
                    Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(C.Accent))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.active_model, label), color = C.TextDim, fontSize = 14.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(8.dp))
                        PillButton(stringResource(R.string.unload), primary = false) { vm.unloadModel(); onDismiss() }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    state.modelsLoading -> SheetLoading()
                    state.models.isEmpty() -> SheetEmpty(stringResource(R.string.no_downloaded_gguf))
                    else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        items(state.models, key = { it.repoId }) { m ->
                            SheetRow(
                                title = m.repoId.substringAfter('/'),
                                subtitle = m.repoId.substringBefore('/') + " · " + formatSize(m.sizeBytes),
                                active = active?.modelId.equals(m.repoId, ignoreCase = true),
                                vision = m.hasVision,
                            ) { vm.loadVariants(m.repoId) }
                        }
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), size = 44.dp, iconSize = 22.dp, background = C.SurfaceHigh) {
                        vm.clearVariants()
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        variants.repoId.substringAfter('/'), fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    state.variantsLoading -> SheetLoading()
                    variants.variants.isEmpty() -> SheetEmpty(stringResource(R.string.no_downloaded_variants))
                    else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        items(variants.variants, key = { it.quant }) { v ->
                            SheetRow(
                                title = v.displayLabel ?: v.quant,
                                subtitle = formatSize(v.sizeBytes),
                                active = active?.modelId.equals(variants.repoId, ignoreCase = true) && active?.ggufVariant == v.quant,
                            ) {
                                vm.switchModel(variants.repoId, v.quant)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSheet(vm: AppViewModel, state: UiState, onDismiss: () -> Unit) {
    val s = state.settings
    var url by remember { mutableStateOf(s.serverUrl) }
    var name by remember { mutableStateOf(s.displayName) }
    var temperature by remember { mutableFloatStateOf(s.temperature) }
    var systemPrompt by remember { mutableStateOf(s.systemPrompt) }

    // Closing the sheet in any way keeps the edits.
    fun saveAndClose() {
        vm.updatePreferences(temperature, systemPrompt, name)
        if (url.isNotBlank() && normalizeServerUrl(url) != s.serverUrl) vm.changeServer(url)
        onDismiss()
    }

    AppSheet(onDismiss = ::saveAndClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            val shown = name.ifBlank { s.username }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(shown, size = 52.dp)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(shown, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Text)
                    Text(s.username, color = C.TextDim, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.server_address_update))
            PillField(
                url, { url = it }, "https://…trycloudflare.com", container = C.SurfaceHigh,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.display_name))
            PillField(name, { name = it }, stringResource(R.string.your_name), container = C.SurfaceHigh)
            Spacer(Modifier.height(20.dp))

            Row {
                Text(stringResource(R.string.temperature), color = C.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%.2f", temperature), color = C.TextDim, fontSize = 15.sp)
            }
            Slider(
                value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = C.SurfaceHigh),
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.system_prompt))
            PillField(systemPrompt, { systemPrompt = it }, stringResource(R.string.optional), singleLine = false, container = C.SurfaceHigh)
            Spacer(Modifier.height(28.dp))

            DictationRow(state.settings.studioDictation, vm::setStudioDictation)
            if (BuildConfig.ASK_BATTERY_EXEMPTION) {
                Spacer(Modifier.height(12.dp))
                BackgroundAccessRow()
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.save), onClick = ::saveAndClose)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .clickable { onDismiss(); vm.logout() }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = C.Error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.log_out), color = C.Error, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * MIUI/HyperOS battery savers can still freeze the app with the screen off; this asks Android
 * to exempt it so long replies keep streaming in the background.
 */
@SuppressLint("BatteryLife") // github build only (BuildConfig.ASK_BATTERY_EXEMPTION); the exemption is the whole point of this row
@Composable
private fun BackgroundAccessRow() {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    fun isExempt() = power.isIgnoringBatteryOptimizations(context.packageName)
    var exempt by remember { mutableStateOf(isExempt()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { exempt = isExempt() }

    SettingRow(
        stringResource(R.string.background_title),
        stringResource(if (exempt) R.string.background_on else R.string.background_off),
        onClick = if (exempt) null else ({
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            runCatching { launcher.launch(request) }
                .onFailure { launcher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            Unit
        }),
    ) {
        if (exempt) Icon(Icons.Rounded.Check, null, tint = C.Accent, modifier = Modifier.size(22.dp))
        else Text(stringResource(R.string.allow), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A setting on the settings sheet: title, explanation and a trailing control; [onClick] null disables it. */
@Composable
private fun SettingRow(title: String, description: String, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.SurfaceHigh)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 17.sp)
            Text(description, color = C.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.size(8.dp))
        trailing()
    }
}

@Composable
private fun DictationRow(on: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        stringResource(R.string.dictation_title),
        stringResource(R.string.dictation_description),
        onClick = { onChange(!on) },
    ) {
        Switch(checked = on, onCheckedChange = onChange, colors = appSwitchColors())
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, active: Boolean, vision: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle, color = C.TextFaint, fontSize = 13.sp)
                if (vision) {
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.Rounded.Visibility, stringResource(R.string.supports_images), tint = C.TextFaint, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (active) Icon(Icons.Rounded.Check, stringResource(R.string.active), tint = C.Accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SheetLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Spinner(26.dp, stroke = 2.dp) }
}

@Composable
fun SheetEmpty(text: String) {
    Text(text, color = C.TextFaint, modifier = Modifier.padding(24.dp))
}


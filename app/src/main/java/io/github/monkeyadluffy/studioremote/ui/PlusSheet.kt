package io.github.monkeyadluffy.studioremote.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.R
import io.github.monkeyadluffy.studioremote.UiState
import io.github.monkeyadluffy.studioremote.data.InferenceStatus

/** Studio's tool permission modes, worded like its own picker. */
private class PermissionOption(val mode: String, @param:StringRes val title: Int, @param:StringRes val description: Int, val icon: ImageVector)

private val permissionOptions = listOf(
    PermissionOption("ask", R.string.perm_ask_title, R.string.perm_ask_description, Icons.Outlined.PanTool),
    PermissionOption("auto", R.string.perm_auto_title, R.string.perm_auto_description, Icons.Outlined.VerifiedUser),
    PermissionOption("off", R.string.perm_off_title, R.string.perm_off_description, Icons.Outlined.RadioButtonChecked),
    PermissionOption("full", R.string.perm_full_title, R.string.perm_full_description, Icons.Outlined.ErrorOutline),
)

@Composable
fun reasoningLabel(level: String): String = when (level) {
    "auto" -> stringResource(R.string.auto)
    "off", "none" -> stringResource(R.string.reasoning_off)
    "on" -> stringResource(R.string.reasoning_on)
    "minimal" -> stringResource(R.string.reasoning_minimal)
    "low" -> stringResource(R.string.reasoning_low)
    "medium" -> stringResource(R.string.reasoning_medium)
    "high" -> stringResource(R.string.reasoning_high)
    "max" -> stringResource(R.string.reasoning_max)
    "xhigh" -> stringResource(R.string.reasoning_xhigh)
    else -> level
}

/** Levels the loaded model accepts: always "auto", "off" unless forced on, then on/effort levels. */
private fun reasoningChoices(st: InferenceStatus?): List<String> = buildList {
    add("auto")
    if (st?.reasoningAlwaysOn != true) add("off")
    val levels = st?.reasoningEffortLevels.orEmpty().filter { it != "none" }
    when {
        st?.usesEffort != true -> add("on")
        levels.isNotEmpty() -> addAll(levels)
        else -> addAll(listOf("low", "medium", "high"))
    }
}

private enum class PlusPage { Main, Reasoning, Permissions }

/** The composer's "+" menu: attachments and per-chat generation options. */
@Composable
fun PlusSheet(vm: AppViewModel, state: UiState, onPickImage: () -> Unit, onPickFile: () -> Unit, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf(PlusPage.Main) }
    AppSheet(onDismiss) {
        AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "plus") { current ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                when (current) {
                    PlusPage.Main -> MainPage(
                        vm, state,
                        onPickImage = { onDismiss(); onPickImage() },
                        onPickFile = { onDismiss(); onPickFile() },
                        onOpen = { page = it },
                    )
                    PlusPage.Reasoning -> ReasoningPage(vm, state) { page = PlusPage.Main }
                    PlusPage.Permissions -> PermissionsPage(vm, state) { page = PlusPage.Main }
                }
            }
        }
    }
}

@Composable
private fun MainPage(vm: AppViewModel, state: UiState, onPickImage: () -> Unit, onPickFile: () -> Unit, onOpen: (PlusPage) -> Unit) {
    val s = state.settings
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigTile(Icons.Outlined.Image, stringResource(R.string.photo), Modifier.weight(1f), onPickImage)
        BigTile(Icons.Outlined.AttachFile, stringResource(R.string.file), Modifier.weight(1f), onPickFile)
    }
    Hint(stringResource(R.string.attach_hint), Modifier.padding(bottom = 6.dp))
    Spacer(Modifier.height(8.dp))

    Card {
        NavRow(Icons.Outlined.Lightbulb, stringResource(R.string.reasoning_level), reasoningLabel(s.reasoningLevel)) { onOpen(PlusPage.Reasoning) }
        Divider()
        ToggleRow(Icons.Outlined.Public, stringResource(R.string.web_search), stringResource(R.string.web_search_description), s.webSearch, vm::setWebSearch)
        Divider()
        ToggleRow(Icons.Outlined.TravelExplore, stringResource(R.string.deep_research), stringResource(R.string.deep_research_description), s.deepResearch, vm::setDeepResearch)
        Divider()
        ToggleRow(Icons.Outlined.Terminal, stringResource(R.string.code_execution), stringResource(R.string.code_execution_description), s.codeTools, vm::setCodeTools)
    }
    Spacer(Modifier.height(10.dp))
    Card {
        val current = permissionOptions.firstOrNull { it.mode == s.permissionMode } ?: permissionOptions[2]
        NavRow(Icons.Outlined.Shield, stringResource(R.string.tool_permissions), stringResource(current.title)) { onOpen(PlusPage.Permissions) }
    }
    if (state.status?.supportsTools == false) Hint(stringResource(R.string.model_may_not_use_tools))
}

@Composable
private fun ReasoningPage(vm: AppViewModel, state: UiState, onBack: () -> Unit) {
    SubHeader(stringResource(R.string.reasoning_level), onBack)
    Text(
        stringResource(R.string.reasoning_description),
        color = C.TextDim, fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    )
    val choices = reasoningChoices(state.status)
    val selected = state.settings.reasoningLevel.takeIf { it in choices } ?: "auto"
    Card {
        choices.forEachIndexed { i, level ->
            if (i > 0) Divider()
            CheckRow(reasoningLabel(level), selected == level) {
                vm.setReasoningLevel(level)
                onBack()
            }
        }
    }
}

@Composable
private fun PermissionsPage(vm: AppViewModel, state: UiState, onBack: () -> Unit) {
    SubHeader(stringResource(R.string.permissions_question), onBack)
    Spacer(Modifier.height(6.dp))
    Card {
        permissionOptions.forEachIndexed { i, option ->
            if (i > 0) Divider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.setPermissionMode(option.mode); onBack() }
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(option.icon, null, tint = C.Text, modifier = Modifier.padding(top = 2.dp).size(24.dp))
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(option.title), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(option.description), color = C.TextDim, fontSize = 14.5.sp, lineHeight = 20.sp)
                }
                if (state.settings.permissionMode == option.mode) {
                    Icon(Icons.Rounded.Check, stringResource(R.string.selected_item), tint = C.Text, modifier = Modifier.padding(start = 8.dp, top = 2.dp).size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.SurfaceHigh), content = content)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(0.5.dp).background(C.Border))
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.TextFaint, fontSize = 12.5.sp, modifier = modifier.padding(start = 6.dp, top = 8.dp))
}

@Composable
private fun BigTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(C.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(14.dp))
        Text(title, color = C.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.TextDim, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.TextDim, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 17.sp)
            Text(description, color = C.TextFaint, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = appSwitchColors())
    }
}

@Composable
private fun CheckRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = C.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, stringResource(R.string.selected_item), tint = C.Text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), size = 42.dp, iconSize = 22.dp, background = C.SurfaceHigh, onClick = onBack)
        Spacer(Modifier.size(12.dp))
        Text(title, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

package io.github.monkeyadluffy.studioremote.ui

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.monkeyadluffy.studioremote.AppLanguage
import io.github.monkeyadluffy.studioremote.R

/** Tabs of Studio's web settings dialog that the app has so far, in the same order. */
private enum class SettingsTab(@param:StringRes val title: Int, @param:StringRes val description: Int) {
    Appearance(R.string.settings_tab_appearance, R.string.appearance_description),
}

/** App settings, laid out like Studio's web settings: tabs on top, then titled sections of rows. */
@Composable
fun SettingsScreen(onMenu: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.Appearance) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.settings), onMenu)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsTab.entries.forEach { t -> TabChip(stringResource(t.title), t == tab) { tab = t } }
        }
        Text(
            stringResource(tab.description), color = C.TextDim, fontSize = 14.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )
        when (tab) {
            SettingsTab.Appearance -> SettingsSection(R.string.language_title) { LanguageRow() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Text(
        stringResource(title), color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
    )
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface),
        content = content,
    )
}

/** Display language; picking one recreates the activity so every screen redraws in it. */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val current = remember { AppLanguage.get(context) }
    var open by remember { mutableStateOf(false) }
    val autoLabel = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == AppLanguage.AUTO) autoLabel else AppLanguage.nativeName(tag)

    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.display_language), color = C.Text, fontSize = 17.sp)
                Text(stringResource(R.string.display_language_description), color = C.TextDim, fontSize = 13.sp)
            }
            Spacer(Modifier.size(8.dp))
            Text(label(current), color = C.Text, fontSize = 15.sp)
            Icon(Icons.Rounded.UnfoldMore, null, tint = C.TextDim, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = C.SurfaceHigh) {
            AppLanguage.choices.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(label(tag), color = C.Text) },
                    trailingIcon = { if (tag == current) Icon(Icons.Rounded.Check, stringResource(R.string.selected_item), tint = C.Text) },
                    onClick = {
                        open = false
                        if (tag != current) {
                            AppLanguage.set(context, tag)
                            activity?.recreate()
                        }
                    },
                )
            }
        }
    }
}

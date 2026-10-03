package io.github.monkeyadluffy.studioremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Width of the window the app is drawn in. */
@Composable
fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** The round dark buttons used across the top bars. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp = 52.dp,
    iconSize: Dp = 26.dp,
    background: Color = C.Surface,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = C.Text, modifier = Modifier.size(iconSize))
    }
}

/** Borderless round icon button, as used inside the composer and under replies. */
@Composable
fun IconTap(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp = 48.dp,
    iconSize: Dp = 26.dp,
    tint: Color = C.TextDim,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize)) }
}

@Composable
fun Avatar(name: String, size: Dp = 46.dp, onClick: (() -> Unit)? = null) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "U" }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(C.Avatar)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

/** Rounded text field; [password] adds a show/hide toggle. */
@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    singleLine: Boolean = true,
    container: Color = C.Surface,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var revealed by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = C.Text, fontSize = 17.sp),
        cursorBrush = SolidColor(C.Text),
        keyboardOptions = keyboardOptions,
        visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(start = 20.dp, end = if (password) 4.dp else 20.dp, top = 18.dp, bottom = 18.dp)) {
                    if (value.isEmpty()) Text(placeholder, color = C.TextFaint, fontSize = 17.sp)
                    inner()
                }
                if (password) {
                    Box(Modifier.padding(end = 8.dp)) {
                        IconTap(
                            if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            if (revealed) "Şifreyi gizle" else "Şifreyi göster",
                            size = 44.dp, iconSize = 22.dp,
                        ) { revealed = !revealed }
                    }
                }
            }
        },
    )
}

/** Full-width white call-to-action. */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Color.White else C.SurfaceHigh)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Spinner(size = 22.dp, color = Color.Black, stroke = 2.5.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.Black else C.TextFaint)
        }
    }
}

/** Compact inline action, e.g. "İzin ver" / "Reddet" on a tool card. */
@Composable
fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) Color.White else C.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(text, color = if (primary) Color.Black else C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Spinner(size: Dp = 16.dp, color: Color = C.TextDim, stroke: Dp = 1.5.dp) {
    CircularProgressIndicator(color = color, strokeWidth = stroke, modifier = Modifier.size(size))
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = C.TextDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun appSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedTrackColor = Color.White,
    checkedThumbColor = Color.Black,
    uncheckedTrackColor = C.Surface,
    uncheckedThumbColor = C.TextDim,
    uncheckedBorderColor = C.Border,
)

/** The app's bottom sheet: fully expanded, dark, with a slim drag handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(C.Border)
            )
        },
        content = content,
    )
}

/** Title row of the full-screen pages, with the drawer button. */
@Composable
fun ScreenHeader(title: String, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(Icons.Rounded.Menu, "Menü", onClick = onMenu)
        Spacer(Modifier.size(14.dp))
        Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** A selectable pill; white when selected. */
@Composable
fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else C.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.Black else C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One-of-many choice as a horizontally scrolling row of [TabChip]s. */
@Composable
fun <T> ChoiceChips(choices: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { c -> TabChip(label(c), c == selected) { onSelect(c) } }
    }
}

/** Rounded surface card with a small caption, used for "which model is loaded" panels. */
@Composable
fun InfoCard(caption: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .padding(16.dp)
    ) {
        Text(caption, color = C.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Slim determinate progress bar; [fraction] is 0..1. */
@Composable
fun ThinProgress(fraction: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.toFloat() },
        modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        color = C.Accent, trackColor = C.SurfaceHigh, drawStopIndicator = {},
    )
}

/** A tappable model row inside an [InfoCard]: name, optional size, and a trailing action. */
@Composable
fun PickRow(title: String, subtitle: String?, action: String = "Yükle ›", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = C.TextFaint, fontSize = 12.5.sp) }
        }
        Text(action, color = C.TextDim, fontSize = 14.sp)
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, action: String = "Sil", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.SurfaceHigh,
        title = { Text(title) },
        text = { Text(message, color = C.TextDim) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = C.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal", color = C.TextDim) } },
    )
}

/** A spinner followed by a status line, e.g. "Yükleniyor: …". */
@Composable
fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spinner(16.dp)
        Spacer(Modifier.size(8.dp))
        Text(text, color = C.Text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Hairline indeterminate bar shown while a list is loading. */
@Composable
fun LoadingBar() {
    LinearProgressIndicator(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(2.dp),
        color = C.TextDim, trackColor = C.Surface,
    )
}

/** One entry of an [ActionMenu]; [danger] paints it red. */
class MenuAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/** The dark long-press menu used on chats, folders and messages; picking an entry closes it. */
@Composable
fun ActionMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = C.SurfaceHigh) {
        actions.forEach { a ->
            val tint = if (a.danger) C.Error else C.Text
            DropdownMenuItem(
                text = { Text(a.label, color = tint) },
                leadingIcon = { Icon(a.icon, null, tint = tint) },
                onClick = { onDismiss(); a.onClick() },
            )
        }
    }
}

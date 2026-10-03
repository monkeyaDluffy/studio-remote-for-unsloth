package io.github.monkeyadluffy.studioremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.monkeyadluffy.studioremote.AppViewModel
import io.github.monkeyadluffy.studioremote.R
import io.github.monkeyadluffy.studioremote.data.ApiException
import io.github.monkeyadluffy.studioremote.data.Settings
import io.github.monkeyadluffy.studioremote.data.normalizeServerUrl
import kotlinx.coroutines.launch

/** Phone keyboards otherwise auto-capitalise, autocorrect or append spaces to credentials. */
private fun noAutoCorrect(type: KeyboardType) = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = type,
)

@Composable
fun SetupScreen(vm: AppViewModel, settings: Settings) {
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    var url by remember { mutableStateOf(settings.serverUrl) }
    var username by remember { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(settings.displayName) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var useKey by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    // Single-user Studio (the default) has exactly one account; its web login asks only for the
    // password and always sends the built-in username, so we do the same.
    var singleUser by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Color.Black, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineMedium, color = C.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.connect_subtitle),
                color = C.TextDim, fontSize = 16.sp,
            )
            Spacer(Modifier.height(32.dp))

            SectionLabel(stringResource(R.string.server_address))
            PillField(
                value = url,
                onValueChange = { url = it; connected = false; error = null },
                placeholder = stringResource(R.string.server_address_example),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            AnimatedVisibility(connected, enter = fadeIn() + expandVertically()) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    if (useKey) {
                        SectionLabel(stringResource(R.string.api_key))
                        PillField(apiKey, { apiKey = it; error = null }, "sk-unsloth-…", password = true,
                            keyboardOptions = noAutoCorrect(KeyboardType.Password))
                    } else {
                        if (!singleUser) {
                            SectionLabel(stringResource(R.string.username))
                            PillField(username, { username = it; error = null }, "unsloth",
                                keyboardOptions = noAutoCorrect(KeyboardType.Ascii))
                            Spacer(Modifier.height(16.dp))
                        }
                        SectionLabel(stringResource(R.string.password))
                        PillField(password, { password = it; error = null }, stringResource(R.string.password_placeholder), password = true,
                            keyboardOptions = noAutoCorrect(KeyboardType.Password))
                    }
                    Text(
                        stringResource(if (useKey) R.string.sign_in_with_password else R.string.sign_in_with_key),
                        color = C.TextDim, fontSize = 14.sp,
                        modifier = Modifier
                            .padding(top = 10.dp, start = 4.dp)
                            .clickable { useKey = !useKey; error = null },
                    )
                    Spacer(Modifier.height(16.dp))
                    SectionLabel(stringResource(R.string.name_for_greeting))
                    PillField(name, { name = it }, stringResource(R.string.name_example))
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = C.Error, fontSize = 15.sp)
            }

            Spacer(Modifier.height(28.dp))
            PrimaryButton(
                text = stringResource(if (connected) R.string.sign_in else R.string.connect),
                enabled = url.isNotBlank() && (!connected ||
                    (if (useKey) apiKey.isNotBlank() else (singleUser || username.isNotBlank()) && password.isNotEmpty())),
                loading = busy,
            ) {
                scope.launch {
                    busy = true
                    error = null
                    if (!connected) {
                        vm.checkServer(url)
                            .onSuccess { st ->
                                connected = true
                                url = normalizeServerUrl(url)
                                singleUser = st.singleUser
                                if (username.isBlank() || singleUser) username = st.defaultUsername ?: "unsloth"
                            }
                            .onFailure { error = res.getString(R.string.connect_failed, it.message ?: it.javaClass.simpleName) }
                    } else {
                        val result = if (useKey) vm.loginWithKey(url, apiKey, name)
                        else vm.login(url, username, password, name)
                        result.onFailure { e ->
                            error = when ((e as? ApiException)?.code) {
                                401 -> res.getString(if (useKey) R.string.invalid_api_key else R.string.wrong_credentials)
                                429 -> res.getString(R.string.too_many_attempts)
                                else -> e.message ?: res.getString(R.string.sign_in_failed)
                            }
                        }
                    }
                    busy = false
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

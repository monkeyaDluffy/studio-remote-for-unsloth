package io.github.monkeyadluffy.studioremote.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore("unsloth")

data class Settings(
    val serverUrl: String = "",
    val username: String = "",
    val displayName: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val temperature: Float = 0.6f,
    val systemPrompt: String = "",
    /** "auto" (model default), "off", "on", or an effort level such as "low"/"high". */
    val reasoningLevel: String = "auto",
    val webSearch: Boolean = false,
    val deepResearch: Boolean = false,
    val codeTools: Boolean = false,
    /** Studio permission modes: ask | auto (approve for me) | off (run automatically) | full. */
    val permissionMode: String = "off",
    /** Dictate with Studio's Whisper instead of the phone's speech recogniser. */
    val studioDictation: Boolean = false,
) {
    val loggedIn: Boolean get() = serverUrl.isNotBlank() && accessToken.isNotBlank()

    /** Name used for the greeting and the avatar. */
    val shownName: String get() = displayName.ifBlank { username }
}

class Prefs(private val context: Context) {
    private object K {
        val url = stringPreferencesKey("server_url")
        val user = stringPreferencesKey("username")
        val name = stringPreferencesKey("display_name")
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val temp = floatPreferencesKey("temperature")
        val system = stringPreferencesKey("system_prompt")
        val reasoning = stringPreferencesKey("reasoning_level")
        val web = booleanPreferencesKey("web_search")
        val research = booleanPreferencesKey("deep_research")
        val code = booleanPreferencesKey("code_tools")
        val permission = stringPreferencesKey("permission_mode")
        val dictation = booleanPreferencesKey("studio_dictation")
    }

    /** Tokens are stored encrypted; plain ones left by 1.11 and older are encrypted on first load. */
    suspend fun load(): Settings {
        val stored = context.store.data.first()
        val rawTokens = listOfNotNull(stored[K.access], stored[K.refresh]).filter { it.isNotEmpty() }
        val s = stored.toSettings()
        if (rawTokens.any { !SecretBox.isEncrypted(it) }) save(s)
        return s
    }

    private fun Preferences.toSettings(): Settings {
        val access = readSecret(this[K.access].orEmpty())
        val refresh = readSecret(this[K.refresh].orEmpty())
        // One unreadable token means the session can't be renewed: drop both and sign in again.
        val tokens = if (access != null && refresh != null) access to refresh else "" to ""
        return Settings(
            serverUrl = this[K.url] ?: "",
            username = this[K.user] ?: "",
            displayName = this[K.name] ?: "",
            accessToken = tokens.first,
            refreshToken = tokens.second,
        temperature = this[K.temp] ?: 0.6f,
        systemPrompt = this[K.system] ?: "",
        reasoningLevel = this[K.reasoning] ?: "auto",
        webSearch = this[K.web] ?: false,
        deepResearch = this[K.research] ?: false,
        codeTools = this[K.code] ?: false,
        permissionMode = this[K.permission] ?: "off",
            studioDictation = this[K.dictation] ?: false,
        )
    }

    private fun readSecret(stored: String): String? = when {
        stored.isEmpty() -> ""
        SecretBox.isEncrypted(stored) -> SecretBox.decrypt(stored)
        else -> stored // pre-1.12 plain text, re-saved encrypted by load()
    }

    suspend fun save(s: Settings) {
        val access = SecretBox.encrypt(s.accessToken)
        val refresh = SecretBox.encrypt(s.refreshToken)
        context.store.edit {
            it[K.url] = s.serverUrl
            it[K.user] = s.username
            it[K.name] = s.displayName
            it[K.access] = access
            it[K.refresh] = refresh
            it[K.temp] = s.temperature
            it[K.system] = s.systemPrompt
            it[K.reasoning] = s.reasoningLevel
            it[K.web] = s.webSearch
            it[K.research] = s.deepResearch
            it[K.code] = s.codeTools
            it[K.permission] = s.permissionMode
            it[K.dictation] = s.studioDictation
        }
    }
}

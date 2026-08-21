package studio.airpen.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("airpen", Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val current: AppState get() = _state.value

    fun update(block: (AppState) -> AppState) {
        _state.update { prev ->
            val next = block(prev)
            persist(next)
            next
        }
    }

    fun activeProfile(): Profile {
        val s = current
        return s.profiles.firstOrNull { it.id == s.activeProfileId } ?: s.profiles.first()
    }

    fun actionFor(gesture: GestureId): BoundAction {
        val profile = activeProfile()
        return profile.map[gesture]
            ?: current.profiles.firstOrNull { it.id == "system" }?.map?.get(gesture)
            ?: BoundAction()
    }

    fun setBinding(gesture: GestureId, action: BoundAction) {
        update { s ->
            val pid = s.activeProfileId
            s.copy(
                profiles = s.profiles.map { p ->
                    if (p.id != pid) p else p.copy(map = p.map.toMutableMap().apply { put(gesture, action) })
                },
            )
        }
    }

    fun exportJson(): String = gson.toJson(current)

    fun importJson(json: String) {
        val parsed = gson.fromJson(json, AppState::class.java) ?: return
        update { parsed.copy(version = 1) }
    }

    fun resetDefaults() {
        update { AppState() }
    }

    private fun load(): AppState {
        val raw = prefs.getString(KEY, null) ?: return AppState()
        return runCatching { gson.fromJson(raw, AppState::class.java) }.getOrElse { AppState() }
    }

    private fun persist(state: AppState) {
        prefs.edit().putString(KEY, gson.toJson(state)).apply()
    }

    companion object {
        private const val KEY = "app_state_v1"
    }
}

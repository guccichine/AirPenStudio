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

    fun saveNow(): Boolean {
        return persistSync(current)
    }

    fun exportJson(): String = gson.toJson(current)

    fun importJson(json: String) {
        val parsed = gson.fromJson(json, AppState::class.java) ?: return
        update { migrate(parsed) }
    }

    fun resetDefaults() {
        update { AppState() }
    }

    private fun load(): AppState {
        val raw = prefs.getString(KEY, null) ?: return AppState()
        val parsed = runCatching { gson.fromJson(raw, AppState::class.java) }.getOrElse { return AppState() }
        val migrated = migrate(parsed)
        if (migrated !== parsed && migrated.version != parsed.version) persist(migrated)
        return migrated
    }

    private fun migrate(parsed: AppState): AppState {
        var next = parsed
        if (next.version < 2) {
            next = next.copy(
                version = 2,
                mouse = next.mouse.copy(alwaysShowCursor = true, cursorSizeDp = kotlin.math.max(next.mouse.cursorSizeDp, 44f)),
                gesture = next.gesture.copy(
                    flickStraightness = kotlin.math.min(next.gesture.flickStraightness, 0.62f),
                    minFlickLength = kotlin.math.min(next.gesture.minFlickLength, 0.12f),
                    deadZone = kotlin.math.min(next.gesture.deadZone, 0.008f),
                ),
            )
        }
        if (next.version < 3) {
            val hasReading = next.profiles.any { it.id == "reading" }
            next = next.copy(
                version = 3,
                profiles = next.profiles.map { p ->
                    if (p.id != "system") p
                    else {
                        val map = p.map.toMutableMap()
                        val up = map[GestureId.FLICK_UP]
                        val down = map[GestureId.FLICK_DOWN]
                        if (up == null || up.id == ActionId.HOME) {
                            map[GestureId.FLICK_UP] = BoundAction(ActionId.SCROLL_UP)
                        }
                        if (down == null || down.id == ActionId.NOTIFICATIONS) {
                            map[GestureId.FLICK_DOWN] = BoundAction(ActionId.SCROLL_DOWN)
                        }
                        if (map[GestureId.CIRCLE_CCW]?.id == ActionId.MEDIA_PREV && map[GestureId.FLICK_UP]?.id == ActionId.SCROLL_UP) {
                            map[GestureId.CIRCLE_CCW] = BoundAction(ActionId.HOME)
                        }
                        p.copy(map = map)
                    }
                } + if (hasReading) emptyList() else defaultProfiles().filter { it.id == "reading" },
            )
        }
        return next
    }

    private fun persist(state: AppState) {
        prefs.edit().putString(KEY, gson.toJson(state)).apply()
    }

    private fun persistSync(state: AppState): Boolean {
        return try {
            prefs.edit().putString(KEY, gson.toJson(state)).commit()
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        private const val KEY = "app_state_v1"
    }
}

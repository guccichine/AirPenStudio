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

    fun addLetterSample(letter: String, points: List<studio.airpen.app.gesture.Pt>) {
        val key = letter.lowercase().trim().ifBlank { return }
        if (points.size < 4) return
        val resampled = studio.airpen.app.gesture.Unistroke.resample(points, 48)
        val sample = LetterSample(key.take(2), resampled.map { it.x }, resampled.map { it.y })
        update { s ->
            val existing = s.letterSamples.filter { it.letter == key }
            val kept = if (existing.size >= 8) s.letterSamples.filterNot { it.letter == key }.let {
                it + existing.takeLast(7)
            } else s.letterSamples
            s.copy(letterSamples = (kept + sample).takeLast(80))
        }
        saveNow()
    }

    fun clearLetterSamples() {
        update { it.copy(letterSamples = emptyList()) }
        saveNow()
    }

    fun addGestureSample(id: GestureId, points: List<studio.airpen.app.gesture.Pt>) {
        if (points.size < 4) return
        val resampled = studio.airpen.app.gesture.Unistroke.resample(points, 48)
        val sample = GestureSample(id.name, resampled.map { it.x }, resampled.map { it.y })
        update { s ->
            val existing = s.gestureSamples.filter { it.gesture == id.name }
            val kept = if (existing.size >= 8) {
                s.gestureSamples.filterNot { it.gesture == id.name } + existing.takeLast(7)
            } else s.gestureSamples
            s.copy(gestureSamples = (kept + sample).takeLast(96))
        }
        saveNow()
    }

    fun clearGestureSamples() {
        update { it.copy(gestureSamples = emptyList()) }
        saveNow()
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
        if (next.version < 4) {
            next = next.copy(
                version = 4,
                type = next.type.copy(
                    minConfidence = kotlin.math.min(next.type.minConfidence, 0.40f),
                    autoSpace = false,
                    invertAirY = true,
                ),
                profiles = next.profiles.map { p ->
                    if (p.id != "system") p
                    else {
                        val map = p.map.toMutableMap()
                        if (map[GestureId.WAVE] == null) map[GestureId.WAVE] = BoundAction(ActionId.SHARE)
                        if (map[GestureId.DIAMOND] == null) map[GestureId.DIAMOND] = BoundAction(ActionId.SCROLL_TOP)
                        if (map[GestureId.HOOK] == null) map[GestureId.HOOK] = BoundAction(ActionId.CLOSE_APP)
                        p.copy(map = map)
                    }
                },
            )
        }
        if (next.version < 5) {
            val g = next.gesture
            next = next.copy(
                version = 5,
                gesture = g.copy(
                    flickStraightness = kotlin.math.max(g.flickStraightness, 0.82f),
                    motionSmoothing = if (g.motionSmoothing == 0f) 0.42f else g.motionSmoothing,
                    cardinalBias = if (g.cardinalBias == 0f) 0.72f else g.cardinalBias,
                    settleTrim = if (g.settleTrim == 0f) 0.12f else g.settleTrim,
                    flickMinVelocity = if (g.flickMinVelocity == 0f) 1.35f else g.flickMinVelocity,
                    templateMargin = if (g.templateMargin == 0f) 0.06f else g.templateMargin,
                    adaptiveDeadZone = true,
                    gainX = if (g.gainX == 0f) 1f else g.gainX,
                    gainY = if (g.gainY == 0f) 1f else g.gainY,
                ),
            )
        }
        if (next.version >= 6 && next.version < 7) {
            // 1.1.0 wrote version 6 with auto-arm / requireButton=false, which
            // kept the BLE S Pen session hogged so the hardware pen died.
            next = next.copy(
                version = 5,
                gesture = GestureSettings(),
            )
        }
        if (next.version < 7) {
            next = next.copy(version = 7)
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

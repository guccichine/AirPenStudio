package studio.airpen.app.ui

import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.airpen.app.AirPen
import studio.airpen.app.MainActivity
import studio.airpen.app.data.*
import studio.airpen.app.gesture.Pt
import studio.airpen.app.service.AirPenAccessibilityService
import studio.airpen.app.ui.theme.Gold
import java.util.UUID

private enum class Tab { Home, Gestures, Mouse, Type, More }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirPenAppUi(activity: MainActivity) {
    var tab by remember { mutableStateOf(Tab.Home) }
    val mode by AirPen.engine.modeFlow.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AirPen Studio", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = { Text(mode.name, color = Gold, modifier = Modifier.padding(end = 16.dp), fontSize = 12.sp) },
            )
        },
        bottomBar = {
            NavigationBar {
                data class Item(val t: Tab, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
                listOf(
                    Item(Tab.Home, "Home", Icons.Outlined.Home),
                    Item(Tab.Gestures, "Gestures", Icons.Outlined.Gesture),
                    Item(Tab.Mouse, "Mouse", Icons.Outlined.Mouse),
                    Item(Tab.Type, "Type", Icons.Outlined.Keyboard),
                    Item(Tab.More, "More", Icons.Outlined.MoreHoriz),
                ).forEach { it ->
                    NavigationBarItem(selected = tab == it.t, onClick = { tab = it.t }, icon = { Icon(it.icon, it.label) }, label = { Text(it.label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Home -> HomeScreen(activity)
                Tab.Gestures -> GestureSettingsScreen()
                Tab.Mouse -> MouseScreen()
                Tab.Type -> TypeScreen()
                Tab.More -> MoreScreen(activity)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(activity: MainActivity) {
    val status by AirPen.hub.status.collectAsState()
    val live by AirPen.engine.live.collectAsState()
    val last by AirPen.engine.lastRecognition.collectAsState()
    val mode by AirPen.engine.modeFlow.collectAsState()
    val acc = AirPenAccessibilityService.isEnabled()
    val overlay = Settings.canDrawOverlays(activity)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("S Pen", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(when (status.name) { "CONNECTED" -> Color(0xFF3DDC97); "UNSUPPORTED" -> Gold; else -> Color(0xFFE85D5D) }))
                    Spacer(Modifier.width(8.dp))
                    Text("$status  ·  $live")
                }
                Text(last.gesture?.let { "${it.symbol} ${it.label}  ${(last.score * 100).toInt()}%" } ?: last.letter?.let { "Letter $it  ${(last.score * 100).toInt()}%" } ?: "Draw a gesture to test recognition", color = Gold)
            }
        }
        Text("Mode", fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { AirPen.engine.setMode(m) }, label = { Text(m.name) }) }
        }
        Text("Setup", fontWeight = FontWeight.Medium)
        PermRow("Accessibility (clicks, back, home, typing)", acc) { activity.openAccessibilitySettings() }
        PermRow("Display over other apps (cursor + HUD)", overlay) { activity.openOverlaySettings() }
        PermRow("Write settings (brightness)", Settings.System.canWrite(activity)) { activity.openWriteSettings() }
        PermRow("Air Type keyboard (optional IME)", false) { activity.openImeSettings() }
        Text("This build does not connect the S Pen until you tap Connect. That is what was crashing on S22 Ultra.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Button(onClick = { activity.requestConnect() }, modifier = Modifier.fillMaxWidth()) { Text("Connect S Pen") }
        val bg by studio.airpen.app.service.AirPenBackground.runningFlow.collectAsState()
        Button(onClick = { if (bg) activity.stopBackground() else activity.startBackground() }, modifier = Modifier.fillMaxWidth()) { Text(if (bg) "Background ON — tap to stop" else "Work in background") }
        OutlinedButton(onClick = { activity.openBatterySettings() }, modifier = Modifier.fillMaxWidth()) { Text("Allow background battery") }
        Text("Connect starts a persistent notification so gestures keep working after you leave this screen. Unrestrict battery for AirPen Studio on Samsung.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Text("Samsung Settings → Advanced features → S Pen → Air actions: turn Air actions OFF for other apps. Pull the S Pen out, tap Connect, hold the side button and draw.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        val crash = remember { studio.airpen.app.CrashLog.read(activity) }
        if (!crash.isNullOrBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last crash (screenshot this if it still closes)", fontWeight = FontWeight.Medium, color = Color(0xFFFF8A8A))
                    Text(crash.take(4000), fontSize = 11.sp)
                    OutlinedButton(onClick = { studio.airpen.app.CrashLog.clear(activity) }) { Text("Dismiss") }
                }
            }
        }
        Text("Practice pad", fontWeight = FontWeight.Medium)
        PracticePad()
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "ON" else "OFF", color = if (ok) Color(0xFF3DDC97) else Gold, modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        Text(label, modifier = Modifier.weight(1f))
        Text("Open", color = Gold, fontSize = 13.sp)
    }
}

@Composable
private fun PracticePad() {
    val pts = remember { mutableStateListOf<Pt>() }
    var typeMode by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recognize as letter", modifier = Modifier.weight(1f))
            Switch(checked = typeMode, onCheckedChange = { typeMode = it })
        }
        Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF101217)).border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).pointerInput(typeMode) {
            detectDragGestures(
                onDragStart = { o -> pts.clear(); pts += Pt(o.x, -o.y, System.currentTimeMillis()) },
                onDrag = { change, _ -> val p = change.position; pts += Pt(p.x, -p.y, System.currentTimeMillis()) },
                onDragEnd = { AirPen.engine.feedPractice(pts.toList(), typeMode) },
            )
        }) {
            Canvas(Modifier.fillMaxSize()) {
                if (pts.size > 1) {
                    val path = Path(); path.moveTo(pts.first().x, -pts.first().y)
                    for (i in 1 until pts.size) path.lineTo(pts[i].x, -pts[i].y)
                    drawPath(path, Gold, style = Stroke(width = 6f, cap = StrokeCap.Round))
                }
            }
            Text("Draw here", color = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun MouseScreen() {
    val state by AirPen.store.state.collectAsState()
    val m = state.mouse
    fun upd(block: MouseSettings.() -> MouseSettings) {
        AirPen.store.update { it.copy(mouse = it.mouse.block()) }
        AirPen.engine.mouse.settings = AirPen.store.current.mouse
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { AirPen.engine.setMode(AppMode.MOUSE) }, modifier = Modifier.fillMaxWidth()) { Text("Start air mouse") }
        SliderRow("Sensitivity", m.sensitivity, 0.3f..4f) { upd { copy(sensitivity = it) } }
        SliderRow("Acceleration", m.acceleration, 0.5f..4f) { upd { copy(acceleration = it) } }
        SliderRow("Smoothing", m.smoothing, 0f..0.9f) { upd { copy(smoothing = it) } }
        SliderRow("Cursor size", m.cursorSizeDp, 18f..72f) { upd { copy(cursorSizeDp = it) } }
        SwitchRow("Invert X", m.invertX) { upd { copy(invertX = it) } }
        SwitchRow("Invert Y", m.invertY) { upd { copy(invertY = it) } }
        SwitchRow("Show motion trail", m.showTrail) { upd { copy(showTrail = it) } }
        Text("Wave the S Pen to move the cursor. Button click taps. Hold for long-press.")
    }
}

@Composable
private fun TypeScreen() {
    val state by AirPen.store.state.collectAsState()
    val t = state.type
    fun upd(block: TypeSettings.() -> TypeSettings) {
        AirPen.store.update { it.copy(type = it.type.block()) }
        AirPen.engine.typer.settings = AirPen.store.current.type
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { AirPen.engine.setMode(AppMode.TYPE) }, modifier = Modifier.fillMaxWidth()) { Text("Start Air Type") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hybrid", "write", "keyboard").forEach { s -> FilterChip(selected = t.engine == s, onClick = { upd { copy(engine = s) } }, label = { Text(s) }) }
        }
        SwitchRow("Word suggestions", t.suggestions) { upd { copy(suggestions = it) } }
        Text("Hold the button and write a letter. Flicks: ← backspace  → space  ↓ enter  ↑ shift")
    }
}

@Composable
private fun MoreScreen(activity: MainActivity) {
    var page by remember { mutableStateOf("settings") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("settings", "profiles", "macros", "apps", "about").forEach { p -> FilterChip(selected = page == p, onClick = { page = p }, label = { Text(p) }) }
        }
        when (page) {
            "settings" -> SettingsPage()
            "profiles" -> ProfilesPage()
            "macros" -> MacrosPage()
            "apps" -> AppsPage(activity)
            else -> AboutPage()
        }
    }
}

@Composable
private fun SettingsPage() {
    val state by AirPen.store.state.collectAsState()
    val g = state.gesture
    fun upd(block: GestureSettings.() -> GestureSettings) {
        AirPen.store.update { it.copy(gesture = it.gesture.block()) }
        AirPen.hub.settings = AirPen.store.current.gesture
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow("Hold button to draw", g.holdToDraw) { upd { copy(holdToDraw = it) } }
        SwitchRow("Require button for gestures", g.requireButton) { upd { copy(requireButton = it) } }
        SwitchRow("Show HUD overlay", g.showHud) { upd { copy(showHud = it) }; AirPen.engine.hud.enabled = it }
        SwitchRow("Haptic feedback", g.haptic) { upd { copy(haptic = it) } }
        SwitchRow("Battery saver (sleeps air motion when idle)", g.batterySaver) { upd { copy(batterySaver = it) } }
        SliderRow("Dead zone", g.deadZone, 0f..0.08f) { upd { copy(deadZone = it) } }
        SliderRow("Flick straightness", g.flickStraightness, 0.5f..0.95f) { upd { copy(flickStraightness = it) } }
        Button(onClick = { AirPen.store.saveNow() }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
        Button(onClick = { AirPen.store.resetDefaults() }, modifier = Modifier.fillMaxWidth()) { Text("Reset all defaults") }
    }
}

@Composable
private fun ProfilesPage() {
    val state by AirPen.store.state.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.profiles.forEach { p ->
            Card(modifier = Modifier.fillMaxWidth().clickable { AirPen.store.update { it.copy(activeProfileId = p.id) } }, colors = CardDefaults.cardColors(containerColor = if (p.id == state.activeProfileId) Gold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text(p.name, fontWeight = FontWeight.Medium)
                    Text("${p.map.size} bindings" + if (p.builtIn) " · built-in" else "", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MacrosPage() {
    val state by AirPen.store.state.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.macros.forEach { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(m.name, fontWeight = FontWeight.Medium)
                    Text(m.steps.joinToString(" → ") { it.action.id.label }, fontSize = 13.sp)
                    OutlinedButton(onClick = { AirPen.executor.runMacro(m) }) { Text("Run") }
                }
            }
        }
    }
}

@Composable
private fun AppsPage(activity: MainActivity) {
    val state by AirPen.store.state.collectAsState()
    val pm = activity.packageManager
    val apps = remember {
        pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_DEFAULT_ONLY)
            .map { val a = it.activityInfo; a.packageName to a.loadLabel(pm).toString() }.sortedBy { it.second.lowercase() }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Assign a profile when an app comes to the foreground (needs Accessibility).") }
        items(apps, key = { it.first }) { (pkg, label) ->
            var expanded by remember { mutableStateOf(false) }
            val current = state.appProfileMap[pkg]
            Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(label); Text(pkg, fontSize = 11.sp) }
                Text(state.profiles.firstOrNull { it.id == current }?.name ?: "default", color = Gold)
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Default") }, onClick = { AirPen.store.update { s -> s.copy(appProfileMap = s.appProfileMap - pkg) }; expanded = false })
                    state.profiles.forEach { p ->
                        DropdownMenuItem(text = { Text(p.name) }, onClick = { AirPen.store.update { s -> s.copy(appProfileMap = s.appProfileMap + (pkg to p.id)) }; expanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPage() {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AirPen Studio", style = MaterialTheme.typography.headlineMedium)
        Text("S Pen customisation for air gestures, distant air-mouse, and air typing.")
        Text("On S22 Ultra: enable Accessibility + Appear on top, disable Samsung Air actions for other apps, pull the S Pen out.")
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, modifier = Modifier.weight(1f)); Text("%.2f".format(value), color = Gold) }; Slider(value = value, onValueChange = onChange, valueRange = range) }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = value, onCheckedChange = onChange) }
}

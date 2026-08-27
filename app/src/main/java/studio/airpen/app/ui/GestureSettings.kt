package studio.airpen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.airpen.app.AirPen
import studio.airpen.app.data.ActionGroup
import studio.airpen.app.data.ActionId
import studio.airpen.app.data.BoundAction
import studio.airpen.app.data.GestureCategory
import studio.airpen.app.data.GestureId
import studio.airpen.app.ui.theme.Gold

@Composable
fun GestureSettingsScreen() {
    var filter by remember { mutableStateOf(GestureCategory.DIRECTION) }
    var editing by remember { mutableStateOf<GestureId?>(null) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    val state by AirPen.store.state.collectAsState()
    val profile = state.profiles.firstOrNull { it.id == state.activeProfileId } ?: state.profiles.first()
    val sampleCounts = remember(state.gestureSamples) {
        state.gestureSamples.groupingBy { it.gesture }.eachCount()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GestureCategory.entries.forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c.name) })
            }
        }
        Text("Profile: ${profile.name}", modifier = Modifier.padding(horizontal = 16.dp), color = Gold)
        Text("Tap a gesture, pick an action, then tap Save. Hold a row after the next build for a clip preview.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TrailPicker()
        }
        Text(
            "To teach a gesture: Home practice pad → draw it → tap that button. Trained: ${state.gestureSamples.size}",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 13.sp,
        )
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GestureId.entries.filter { it.category == filter }, key = { it.name }) { g ->
                val bound = profile.map[g] ?: BoundAction()
                Card(
                    modifier = Modifier.fillMaxWidth().goldHoverGlow().clickable { editing = g; savedMsg = null },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.symbol, fontSize = 22.sp, modifier = Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.label, fontWeight = FontWeight.Medium)
                            val trained = sampleCounts[g.name] ?: 0
                            Text(
                                bound.id.label + bound.arg.let { if (it.isBlank()) "" else " · $it" } +
                                    if (trained > 0) " · $trained trained" else "",
                                fontSize = 13.sp,
                                color = Gold,
                            )
                        }
                    }
                }
            }
        }
        if (savedMsg != null) {
            Text(savedMsg!!, color = Gold, modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Medium)
        }
        Button(
            onClick = { savedMsg = if (AirPen.store.saveNow()) "Gestures saved" else "Save failed" },
            modifier = Modifier.fillMaxWidth().padding(16.dp).goldHoverGlow(),
        ) { Text("Save gestures") }
    }
    editing?.let { g ->
        GestureActionPicker(g, onSaved = { savedMsg = "Saved ${g.label}"; editing = null }, onClose = { editing = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureActionPicker(gesture: GestureId, onSaved: () -> Unit, onClose: () -> Unit) {
    val current = AirPen.store.actionFor(gesture)
    var group by remember { mutableStateOf(current.id.group) }
    var arg by remember { mutableStateOf(current.arg) }
    var selected by remember { mutableStateOf(current.id) }
    var query by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { onClose() }) {
        Card(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(560.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Map ${gesture.label}", style = MaterialTheme.typography.titleLarge)
                Text(destCopy(BoundAction(selected, arg)), fontSize = 13.sp, color = Gold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionGroup.entries.forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.name) })
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search actions") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = arg,
                    onValueChange = { arg = it },
                    label = { Text("Argument (app package, URL, text, combo)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    val q = query.trim()
                    val list = if (q.isEmpty()) {
                        ActionId.entries.filter { it.group == group }
                    } else {
                        ActionId.entries.filter {
                            it.label.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true)
                        }
                    }
                    items(list) { a ->
                        Text(
                            a.label,
                            color = if (selected == a) Gold else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected == a) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { selected = a }.padding(vertical = 10.dp),
                        )
                    }
                }
                Button(
                    onClick = {
                        AirPen.store.setBinding(gesture, BoundAction(selected, arg))
                        AirPen.store.saveNow()
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

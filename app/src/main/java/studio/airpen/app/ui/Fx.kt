package studio.airpen.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.airpen.app.data.BoundAction
import studio.airpen.app.data.TrailPrefs
import studio.airpen.app.data.TrailStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailPicker() {
    var open by remember { mutableStateOf(false) }
    val current = TrailStyle.fromId(TrailPrefs.style)
    Text("Air-mouse glow trail", fontWeight = FontWeight.Medium)
    Text("Opens a list of 28 styles. Does not replace the gesture list below.", fontSize = 12.sp)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text("Trail style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TrailStyle.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = {
                        TrailPrefs.style = s.id
                        TrailPrefs.show = s != TrailStyle.OFF
                        open = false
                    },
                )
            }
        }
    }
    Text("Thickness", modifier = Modifier.padding(top = 6.dp), fontSize = 12.sp)
    Slider(value = TrailPrefs.thickness, onValueChange = { TrailPrefs.thickness = it }, valueRange = 0.4f..2.2f)
    Text("Length", fontSize = 12.sp)
    Slider(value = TrailPrefs.length, onValueChange = { TrailPrefs.length = it }, valueRange = 0.4f..2f)
}

fun destCopy(action: BoundAction): String {
    val extra = action.arg.trim().let { if (it.isBlank()) "" else "\n$it" }
    return "Opens / runs: ${action.id.label}$extra"
}

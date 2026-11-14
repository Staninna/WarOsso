package io.github.staninna.warosso.ui.settings

import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.sensors.PositionSimulator
import io.github.staninna.warosso.ui.components.ConnectedItem
import io.github.staninna.warosso.ui.components.ConnectedSection
import kotlin.math.roundToInt

/**
 * The debug menu (debug builds only). With simulation on, a long-press on the
 * Find map sets the position and the dot can be dragged; the sliders here fake the rest.
 */
@Composable
fun DebugSection(simulator: PositionSimulator) {
    val simulation by simulator.state.collectAsStateWithLifecycle()

    ConnectedSection(title = stringResource(R.string.debug_title)) {
        ConnectedItem(
            title = stringResource(R.string.debug_simulate),
            subtitle = stringResource(R.string.debug_simulate_hint),
            trailing = { Switch(checked = simulation.enabled, onCheckedChange = null) },
            onClick = { simulator.update { it.copy(enabled = !it.enabled) } },
        )
        if (simulation.enabled) {
            ConnectedItem(title = stringResource(R.string.debug_accuracy, simulation.accuracyM.roundToInt())) {
                Slider(
                    value = simulation.accuracyM,
                    onValueChange = { metres -> simulator.update { it.copy(accuracyM = metres) } },
                    valueRange = 1f..60f,
                )
            }
            OptionalSlider(
                title = stringResource(R.string.debug_heading),
                value = simulation.headingDeg,
                range = 0f..359f,
                whenEnabled = 0f,
                onChange = { degrees -> simulator.update { it.copy(headingDeg = degrees) } },
            )
            OptionalSlider(
                title = stringResource(R.string.debug_tilt),
                value = simulation.tiltDeg,
                range = 0f..90f,
                whenEnabled = 90f,
                onChange = { degrees -> simulator.update { it.copy(tiltDeg = degrees) } },
            )
            ConnectedItem(
                title = stringResource(R.string.debug_compass),
                trailing = { Switch(checked = simulation.compassReliable, onCheckedChange = null) },
                onClick = { simulator.update { it.copy(compassReliable = !it.compassReliable) } },
            )
        }
    }
}

/** A sensor override: off leaves the real sensor in charge (null), on shows a slider for the fake value. */
@Composable
private fun OptionalSlider(
    title: String,
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    whenEnabled: Float,
    onChange: (Float?) -> Unit,
) {
    ConnectedItem(
        title = if (value == null) title else "$title: ${value.roundToInt()}°",
        trailing = { Switch(checked = value != null, onCheckedChange = null) },
        onClick = { onChange(if (value == null) whenEnabled else null) },
    ) {
        if (value != null) Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

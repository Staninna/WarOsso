package io.github.staninna.warosso.ui.find

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Fix
import io.github.staninna.warosso.domain.Guidance
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.distanceTo
import io.github.staninna.warosso.ui.components.TonalIconButton
import io.github.staninna.warosso.ui.map.Framing
import io.github.staninna.warosso.ui.map.MapCanvas
import io.github.staninna.warosso.ui.map.MapEvent
import io.github.staninna.warosso.ui.map.MapUser
import io.github.staninna.warosso.ui.map.toScene
import kotlinx.coroutines.flow.StateFlow

/** The camera only chases the user once they have moved this far, so GPS jitter does not shake the map. */
private const val REFRAME_AFTER_M = 4.0
private val TopOverlayHeight = 140.dp

/**
 * Map mode: the user's dot with its accuracy circle, every entrance, the target highlighted and
 * a straight line to it (SPEC.md 5.1).
 */
@Composable
fun FindMap(
    data: ComplexData,
    guidance: Guidance,
    fix: Fix?,
    heading: StateFlow<Float?>,
    canMoveUser: Boolean,
    onMoveUser: (LatLng) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val headingDeg by heading.collectAsStateWithLifecycle()
    val target = guidance.route.entrance

    val base = remember(data, resources, target.id) { data.toScene(resources, targetId = target.id) }
    val scene = remember(base, fix, headingDeg, canMoveUser) {
        base.copy(
            user = fix?.let { MapUser(it.latLng, it.accuracyM, headingDeg, draggable = canMoveUser) },
            route = fix?.let { it.latLng to target.latLng },
        )
    }

    // The camera frames the user and the target until the user pans away; the button brings it back.
    var following by remember { mutableStateOf(true) }
    var framedFrom by remember { mutableStateOf<LatLng?>(null) }
    var framingGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(fix?.latLng, following) {
        val position = fix?.latLng ?: return@LaunchedEffect
        if (following && (framedFrom?.distanceTo(position) ?: Double.MAX_VALUE) > REFRAME_AFTER_M) framedFrom = position
    }
    val framing = remember(framedFrom, target.id, framingGeneration) {
        Framing(framedFrom?.let { listOf(it, target.latLng) } ?: data.extent, framingGeneration)
    }

    Box(modifier.fillMaxSize()) {
        MapCanvas(
            scene = scene,
            framing = framing,
            contentPadding = PaddingValues(top = TopOverlayHeight, bottom = bottomInset),
            onEvent = { event ->
                when (event) {
                    MapEvent.CameraMovedByUser -> following = false
                    // Debug position simulation: a long-press drops the fake position, the dot can be dragged.
                    is MapEvent.LongClick -> onMoveUser(event.at)
                    is MapEvent.MarkerDrag -> onMoveUser(event.to)
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = !following,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomInset + 16.dp),
        ) {
            TonalIconButton(
                icon = R.drawable.ic_my_location_fill,
                contentDescription = stringResource(R.string.action_recenter),
                onClick = {
                    following = true
                    framedFrom = fix?.latLng
                    framingGeneration++
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

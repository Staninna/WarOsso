package io.github.staninna.warosso.ui.find

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.ViewMode
import io.github.staninna.warosso.sensors.LocationState
import io.github.staninna.warosso.sensors.fix
import io.github.staninna.warosso.ui.components.PermissionState
import io.github.staninna.warosso.ui.components.TonalIconButton
import io.github.staninna.warosso.ui.components.rememberPermissionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Guidance to a door: camera or map behind, the number on top, directions at the bottom. */
@Composable
fun GuidancePane(
    state: FindUiState.Guiding,
    locationPermission: PermissionState,
    viewMode: ViewMode,
    pose: StateFlow<Orientation?>,
    mapHeading: StateFlow<Float?>,
    canMoveUser: Boolean,
    onMoveUser: (LatLng) -> Unit,
    onToggleViewMode: () -> Unit,
    onStop: () -> Unit,
) {
    val (data, guidance, location) = state
    val fix = location.fix
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    val compassReliable by remember(pose) { pose.map { it?.reliable != false }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = true)

    var expandedByUser by rememberSaveable(guidance.number) { mutableStateOf(false) }
    val expanded = guidance.arrived || expandedByUser
    // Height of the collapsed card: the map and the arrow keep clear of it.
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val cardHeight = with(LocalDensity.current) { cardHeightPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = viewMode,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
            label = "viewMode",
        ) { mode ->
            when (mode) {
                ViewMode.AR -> ArView(data, guidance, fix, pose, cameraGranted = camera.granted, bottomInset = cardHeight)
                ViewMode.MAP -> FindMap(data, guidance, fix, mapHeading, canMoveUser, onMoveUser, bottomInset = cardHeight)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberPill(guidance.number, onStop)
                Spacer(Modifier.weight(1f))
                if (state.manualSwitch) {
                    val toMap = viewMode == ViewMode.AR
                    TonalIconButton(
                        icon = if (toMap) R.drawable.ic_map_fill else R.drawable.ic_view_in_ar_fill,
                        contentDescription = stringResource(if (toMap) R.string.action_show_map else R.string.action_show_camera),
                        onClick = onToggleViewMode,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            LocationBanner(location, locationPermission)
            ToggledBanner(visible = viewMode == ViewMode.AR && !camera.granted) {
                Banner(
                    text = stringResource(R.string.banner_camera_off),
                    warning = false,
                    icon = R.drawable.ic_no_photography,
                    action = stringResource(R.string.action_allow) to camera.request,
                )
            }
            ToggledBanner(visible = !compassReliable) {
                Banner(stringResource(R.string.banner_compass), warning = true) { FigureEight() }
            }
        }

        DirectionsCard(
            data = data,
            guidance = guidance,
            expanded = expanded,
            onToggle = { expandedByUser = !expandedByUser },
            onDone = onStop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { if (!expanded) cardHeightPx = it.height },
        )
    }
}

/** The number being found, with the way out. Solid colour, so it reads over any camera image. */
@Composable
private fun NumberPill(number: Int, onStop: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.action_stop_guidance),
                onClick = onStop,
                size = 48.dp,
                containerColor = MaterialTheme.colorScheme.onPrimary,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 12.dp, end = 20.dp),
            )
        }
    }
}

/** What is wrong with the position, if anything. Each case carries what its banner shows. */
private sealed interface LocationProblem {
    data object Permission : LocationProblem
    data object Off : LocationProblem
    data object Searching : LocationProblem
    data class Weak(val accuracyM: Int) : LocationProblem
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocationBanner(location: LocationState, permission: PermissionState) {
    val context = LocalContext.current
    val fix = location.fix
    val problem = when {
        !permission.granted -> LocationProblem.Permission
        location is LocationState.Off -> LocationProblem.Off
        fix == null -> LocationProblem.Searching
        fix.isWeak -> LocationProblem.Weak(fix.accuracyM.roundToInt())
        else -> null
    }
    // Keyed on the kind of problem, so a changing accuracy updates the banner in place.
    AnimatedContent(targetState = problem, contentKey = { it?.let { shown -> shown::class } }, label = "locationBanner") { shown ->
        when (shown) {
            LocationProblem.Permission -> Banner(
                text = stringResource(R.string.banner_location_permission),
                warning = true,
                icon = R.drawable.ic_location_off,
                action = stringResource(R.string.action_allow) to permission.request,
            )
            LocationProblem.Off -> Banner(
                text = stringResource(R.string.banner_location_off),
                warning = true,
                icon = R.drawable.ic_location_off,
                action = stringResource(R.string.action_turn_on) to {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
            )
            LocationProblem.Searching -> Banner(stringResource(R.string.banner_gps_searching), warning = false) {
                LoadingIndicator(Modifier.size(32.dp))
            }
            is LocationProblem.Weak -> Banner(
                text = stringResource(R.string.banner_gps_weak, shown.accuracyM),
                warning = true,
                icon = R.drawable.ic_location_searching,
            )
            null -> Spacer(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ToggledBanner(visible: Boolean, banner: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) { banner() }
}

/** A one-line status over the camera or map. Warnings take the error colours to stand out in daylight. */
@Composable
private fun Banner(
    text: String,
    warning: Boolean,
    @DrawableRes icon: Int? = null,
    action: Pair<String, () -> Unit>? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (warning) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (warning) colors.onErrorContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            leading?.invoke()
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = action.second) { Text(action.first) }
            }
        }
    }
}

/** A dot tracing the figure 8 the user should wave the phone in to calibrate the compass. */
@Composable
private fun FigureEight(color: Color = MaterialTheme.colorScheme.onErrorContainer) {
    val phase by rememberInfiniteTransition(label = "figureEight").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(Modifier.size(width = 44.dp, height = 28.dp)) {
        // A Lissajous figure: one loop sideways for every two up and down.
        fun pointAt(t: Float) = Offset(
            x = center.x + sin(t) * size.width * 0.42f,
            y = center.y + sin(t) * cos(t) * size.height * 0.8f,
        )
        val track = Path().apply {
            moveTo(pointAt(0f).x, pointAt(0f).y)
            for (step in 1..64) pointAt(step / 64f * (2 * PI).toFloat()).let { lineTo(it.x, it.y) }
        }
        drawPath(track, color.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 4.5.dp.toPx(), center = pointAt(phase))
    }
}

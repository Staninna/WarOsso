package io.github.staninna.warosso.ui.find

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.ArLabel
import io.github.staninna.warosso.domain.ArSubject
import io.github.staninna.warosso.domain.CameraLens
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.FieldOfView
import io.github.staninna.warosso.domain.Fix
import io.github.staninna.warosso.domain.Guidance
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.Orientation
import io.github.staninna.warosso.domain.Route
import io.github.staninna.warosso.domain.Side
import io.github.staninna.warosso.domain.angleDelta
import io.github.staninna.warosso.domain.bearingTo
import io.github.staninna.warosso.domain.distanceTo
import io.github.staninna.warosso.domain.layoutArLabels
import io.github.staninna.warosso.ui.distance
import io.github.staninna.warosso.ui.entranceBadge
import io.github.staninna.warosso.ui.theme.LocalCameraBackdrops
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow

/** Width of an entrance label, as a fraction of the view. The layout maths and the chips share it. */
private const val LABEL_WIDTH = 0.3f
private val LabelRowHeight = 68.dp
private val ArrowSize = 172.dp

/**
 * The camera with the complex drawn over it: a label on every entrance and an arrow on the
 * ground towards the target (SPEC.md 5.1). Without the camera permission the labels and the
 * arrow still work, on a plain background.
 */
@Composable
fun ArView(
    data: ComplexData,
    guidance: Guidance,
    fix: Fix?,
    pose: StateFlow<Orientation?>,
    cameraGranted: Boolean,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    var lens by remember { mutableStateOf(CameraLens.TYPICAL) }

    BoxWithConstraints(modifier.fillMaxSize().background(if (cameraGranted) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow)) {
        val fieldOfView = remember(lens, maxWidth, maxHeight) { lens.fieldOfView(maxWidth / maxHeight) }

        if (cameraGranted) {
            CameraPreview(onLens = { lens = it }, modifier = Modifier.fillMaxSize())
            // Keeps the status bar and the banners readable against a bright sky.
            Box(Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
            WhiteStatusBarIcons()
        }

        if (fix != null) {
            ArOverlay(
                data = data,
                guidance = guidance,
                fix = fix,
                pose = pose.collectAsStateWithLifecycle(),
                fieldOfView = fieldOfView,
                width = maxWidth,
                height = maxHeight,
                bottomInset = bottomInset,
            )
        }
    }
}

/** While this is on screen the status bar icons are white, whatever the theme: they sit on the camera image. */
@Composable
private fun WhiteStatusBarIcons() {
    val backdrops = LocalCameraBackdrops.current
    DisposableEffect(backdrops) {
        backdrops.count++
        onDispose { backdrops.count-- }
    }
}

/**
 * Everything that moves with the compass. The pose is read inside graphics-layer blocks only, so
 * a new sensor reading redraws the overlay without recomposing it.
 */
@Composable
private fun ArOverlay(
    data: ComplexData,
    guidance: Guidance,
    fix: Fix,
    pose: State<Orientation?>,
    fieldOfView: FieldOfView,
    width: Dp,
    height: Dp,
    bottomInset: Dp,
) {
    val resources = LocalResources.current
    val route = guidance.route
    val targetId = route.targetId
    // Every entrance with its label text, far to near and the target last: composed in that
    // order, nearer labels are drawn over farther ones and nothing ever covers the target.
    val labelled = remember(data, fix.latLng, route, guidance.number, resources) {
        fun subject(id: Long, at: LatLng) = ArSubject(id, fix.latLng.bearingTo(at), fix.latLng.distanceTo(at), id == targetId)
        val entrances = data.entrances.map { subject(it.id, it.latLng) to resources.entranceBadge(data, it) }
        // A front door on the street is no entrance, so it brings its own label: the number.
        val ownDoor = (route as? Route.StreetDoor)?.let { subject(targetId, it.target) to guidance.number.toString() }
        (entrances + listOfNotNull(ownDoor))
            .sortedWith(compareBy<Pair<ArSubject, String>> { (subject, _) -> subject.isTarget }.thenByDescending { (subject, _) -> subject.distanceM })
    }
    val subjects = remember(labelled) { labelled.map { it.first } }
    val labels by remember(subjects, fieldOfView) {
        derivedStateOf {
            pose.value
                ?.let { layoutArLabels(subjects, it.headingDeg.toDouble(), fieldOfView.horizontalDeg, LABEL_WIDTH) }
                .orEmpty()
                .associateBy(ArLabel::id)
        }
    }
    val targetSide by remember { derivedStateOf { labels[targetId]?.offScreen } }

    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val rowPx = with(density) { LabelRowHeight.toPx() }
    // Labels sit on the horizon, kept between the banners and the arrow.
    val horizonRange = with(density) { 260.dp.toPx()..(heightPx - (bottomInset + ArrowSize + 24.dp).toPx()).coerceAtLeast(261.dp.toPx()) }

    Box(Modifier.fillMaxSize()) {
        labelled.forEach { (subject, text) ->
            key(subject.id) {
                ArLabelChip(
                    text = text,
                    distance = resources.distance(subject.distanceM),
                    isTarget = subject.isTarget,
                    pointsTo = targetSide.takeIf { subject.isTarget },
                    modifier = Modifier
                        .width(width * LABEL_WIDTH)
                        .graphicsLayer {
                            val label = labels[subject.id]
                            val elevation = pose.value?.elevationDeg
                            if (label == null || elevation == null) {
                                alpha = 0f
                                return@graphicsLayer
                            }
                            val horizon = (fieldOfView.screenY(0.0, elevation.toDouble()) * heightPx).coerceIn(horizonRange)
                            alpha = label.alpha
                            scaleX = label.scale
                            scaleY = label.scale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationX = label.x * widthPx - size.width / 2
                            translationY = horizon - size.height - label.row * rowPx
                        },
                )
            }
        }

        guidance.bearingDeg?.let { bearing ->
            GroundArrow(
                turnDeg = { pose.value?.let { angleDelta(it.headingDeg.toDouble(), bearing).toFloat() } },
                faded = fix.isWeak,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 12.dp),
            )
        }
    }
}

@Composable
private fun ArLabelChip(text: String, distance: String, isTarget: Boolean, pointsTo: Side?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isTarget) colors.tertiary else colors.surface,
        contentColor = if (isTarget) colors.onTertiary else colors.onSurface,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pointsTo == Side.LEFT) Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    style = if (isTarget) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(distance, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            if (pointsTo == Side.RIGHT) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

/**
 * The arrow towards the target, laid back as if painted on the ground. [turnDeg] is how far the
 * target is to the right of where the camera looks; null hides the arrow. It fades with weak GPS.
 */
@Composable
private fun GroundArrow(turnDeg: () -> Float?, faded: Boolean, modifier: Modifier = Modifier) {
    val strength by animateFloatAsState(if (faded) 0.3f else 1f, label = "arrowStrength")
    val fill = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(ArrowSize)
            .graphicsLayer {
                rotationX = 58f
                cameraDistance = 10f * density
                alpha = if (turnDeg() == null) 0f else strength
            },
    ) {
        // Two layers: the tilt above lays the plane back, this one turns the arrow within it.
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = turnDeg() ?: 0f }) {
            val arrow = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.08f)
                lineTo(size.width * 0.86f, size.height * 0.9f)
                lineTo(size.width * 0.5f, size.height * 0.7f)
                lineTo(size.width * 0.14f, size.height * 0.9f)
                close()
            }
            // Round joins soften the corners; the wide pass underneath is the outline.
            drawPath(arrow, outline, style = Stroke(width = 22.dp.toPx(), join = StrokeJoin.Round))
            drawPath(arrow, fill, style = Stroke(width = 8.dp.toPx(), join = StrokeJoin.Round))
            drawPath(arrow, fill)
        }
    }
}

/** The back camera, preview only. Reports its lens once bound, for the overlay's field of view. */
@Composable
private fun CameraPreview(onLens: (CameraLens) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            camera.cameraInfo.lens()?.let(onLens)
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            // No back camera on this device. The overlay still works on a black background.
        } finally {
            provider.unbind(preview)
        }
    }
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun CameraInfo.lens(): CameraLens? {
    val camera2 = Camera2CameraInfo.from(this)
    val sensor = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    val focalLength = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
    return CameraLens(maxOf(sensor.width, sensor.height), minOf(sensor.width, sensor.height), focalLength)
}

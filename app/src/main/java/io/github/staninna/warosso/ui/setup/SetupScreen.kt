package io.github.staninna.warosso.ui.setup

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.edgeMidpoints
import io.github.staninna.warosso.ui.appViewModel
import io.github.staninna.warosso.ui.components.BigButton
import io.github.staninna.warosso.ui.components.Placeholder
import io.github.staninna.warosso.ui.components.TonalIconButton
import io.github.staninna.warosso.ui.entranceBadge
import io.github.staninna.warosso.ui.entranceName
import io.github.staninna.warosso.ui.icon
import io.github.staninna.warosso.ui.map.Framing
import io.github.staninna.warosso.ui.map.MapCanvas
import io.github.staninna.warosso.ui.map.MapEvent
import io.github.staninna.warosso.ui.map.MapHandle
import io.github.staninna.warosso.ui.map.MapMarker
import io.github.staninna.warosso.ui.map.MapScene
import io.github.staninna.warosso.ui.map.MarkerKind
import io.github.staninna.warosso.ui.map.toScene
import kotlinx.coroutines.launch

private enum class SetupSheet { BUILDINGS, MISSING, STREET }

/**
 * Setup happens on the map: tap to place an entrance, drag to move it, redraw the complex
 * outline. Buildings and the list of numbers still missing open as sheets.
 */
@Composable
fun SetupScreen(onBack: () -> Unit, onOpenEntrance: (Long) -> Unit) {
    val viewModel = appViewModel { SetupViewModel(it.complex) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data
    if (data == null) {
        Placeholder(stringResource(R.string.loading), loading = true)
        return
    }

    val resources = LocalResources.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf<SetupSheet?>(null) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val panelHeight = with(LocalDensity.current) { panelHeightPx.toDp() }
    val areaDraft = state.areaDraft

    BackHandler(enabled = areaDraft != null || state.pending != null) {
        if (areaDraft != null) viewModel.cancelAreaEdit() else viewModel.cancelPending()
    }

    val scene = remember(data, resources, state.pending, areaDraft) { setupScene(data.toScene(resources, interactiveEntrances = areaDraft == null), state.pending, areaDraft) }
    // Framed on the buildings, or on the whole outline while that is being redrawn. In between the camera is the user's.
    val framing = remember(areaDraft != null) { Framing(if (areaDraft != null) data.complex.polygon else data.extent) }

    Box(Modifier.fillMaxSize()) {
        MapCanvas(
            scene = scene,
            framing = framing,
            contentPadding = PaddingValues(top = 96.dp, bottom = panelHeight),
            onEvent = { event ->
                when (event) {
                    is MapEvent.Click -> viewModel.onMapClick(event.at)
                    is MapEvent.MarkerClick ->
                        if (event.handle.kind == MarkerKind.ENTRANCE) onOpenEntrance(event.handle.id) else viewModel.onMarkerClick(event.handle)
                    is MapEvent.MarkerDrag -> viewModel.onMarkerDrag(event.handle, event.to, event.done)
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(Modifier.statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text = stringResource(if (areaDraft != null) R.string.setup_area_title else R.string.setup_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            SnackbarHost(snackbars)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().onSizeChanged { panelHeightPx = it.height },
            ) {
                val mode = when {
                    areaDraft != null -> PanelMode.AREA
                    state.pending != null -> PanelMode.PENDING
                    else -> PanelMode.OVERVIEW
                }
                AnimatedContent(mode, label = "setupPanel", modifier = Modifier.navigationBarsPadding().padding(20.dp)) { shown ->
                    when (shown) {
                        PanelMode.AREA -> AreaPanel(
                            street = data.complex.street,
                            onPickStreet = { sheet = SetupSheet.STREET },
                            onReset = viewModel::resetArea,
                            onDone = viewModel::saveArea,
                        )
                        PanelMode.PENDING -> PendingPanel(
                            onCancel = viewModel::cancelPending,
                            onAdd = {
                                viewModel.confirmPending { id ->
                                    if (id != null) onOpenEntrance(id) else scope.launch { snackbars.showSnackbar(resources.getString(R.string.setup_no_buildings)) }
                                }
                            },
                        )
                        PanelMode.OVERVIEW -> OverviewPanel(
                            data = data,
                            onOpenEntrance = onOpenEntrance,
                            onEditArea = viewModel::startAreaEdit,
                            onShowBuildings = { sheet = SetupSheet.BUILDINGS },
                            onShowMissing = { sheet = SetupSheet.MISSING },
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        SetupSheet.BUILDINGS -> BuildingsSheet(data, onChange = viewModel::updateBuilding, onDismiss = { sheet = null })
        SetupSheet.MISSING -> MissingNumbersSheet(data, onDismiss = { sheet = null })
        SetupSheet.STREET -> StreetDialog(data, onPick = viewModel::setStreet, onDismiss = { sheet = null })
        null -> Unit
    }
}

private enum class PanelMode { OVERVIEW, PENDING, AREA }

/** The shared scene plus what Setup is busy with: a spot for a new entrance, or the outline's corners. */
private fun setupScene(base: MapScene, pending: LatLng?, areaDraft: List<LatLng>?): MapScene = when {
    areaDraft != null -> base.copy(
        area = areaDraft,
        markers = base.markers +
            areaDraft.edgeMidpoints().mapIndexed { i, point -> MapMarker(MapHandle(MarkerKind.MIDPOINT, i.toLong()), point, interactive = true) } +
            areaDraft.mapIndexed { i, point -> MapMarker(MapHandle(MarkerKind.VERTEX, i.toLong()), point, interactive = true) },
    )
    pending != null -> base.copy(markers = base.markers + MapMarker(MapHandle(MarkerKind.PENDING, 0), pending, interactive = true))
    else -> base
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverviewPanel(
    data: ComplexData,
    onOpenEntrance: (Long) -> Unit,
    onEditArea: () -> Unit,
    onShowBuildings: () -> Unit,
    onShowMissing: () -> Unit,
) {
    val total = data.bagNumbers.size
    val missing = data.unservedNumbers.size
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.setup_progress, total - missing, total),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(data.complex.street, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearWavyProgressIndicator(
            progress = { (total - missing).toFloat() / total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (data.entrances.isEmpty()) {
            Text(
                text = stringResource(R.string.setup_hint_first),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.entrances, key = Entrance::id) { entrance -> EntranceChip(data, entrance, onClick = { onOpenEntrance(entrance.id) }) }
            }
            Text(
                text = stringResource(R.string.setup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelAction(R.drawable.ic_polyline, stringResource(R.string.setup_area), onEditArea, Modifier.weight(1f))
            PanelAction(R.drawable.ic_domain, stringResource(R.string.setup_buildings), onShowBuildings, Modifier.weight(1f))
            PanelAction(R.drawable.ic_fact_check, pluralStringResource(R.plurals.setup_missing, missing, missing), onShowMissing, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EntranceChip(data: ComplexData, entrance: Entrance, onClick: () -> Unit) {
    val resources = LocalResources.current
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (entrance.floors.isEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (entrance.floors.isEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Icon(
            painterResource(entrance.type.icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(resources.entranceName(data, entrance), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                text = if (entrance.floors.isEmpty()) stringResource(R.string.setup_entrance_empty) else resources.entranceBadge(data, entrance),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PanelAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.heightIn(min = 72.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(icon), contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PendingPanel(onCancel: () -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.setup_pending_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.setup_pending_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                colors = ButtonDefaults.filledTonalButtonColors(),
                modifier = Modifier.weight(1f),
            )
            BigButton(stringResource(R.string.setup_pending_add), onAdd, icon = R.drawable.ic_add_location_alt, modifier = Modifier.weight(1.4f))
        }
    }
}

@Composable
private fun AreaPanel(street: String, onPickStreet: () -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.setup_area_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelAction(R.drawable.ic_pin_drop, street, onPickStreet, Modifier.weight(1f))
            PanelAction(R.drawable.ic_restart_alt, stringResource(R.string.setup_area_reset), onReset, Modifier.weight(1f))
        }
        BigButton(stringResource(R.string.action_done), onDone, icon = R.drawable.ic_check)
    }
}

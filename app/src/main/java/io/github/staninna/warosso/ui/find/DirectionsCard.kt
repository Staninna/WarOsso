package io.github.staninna.warosso.ui.find

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.EntranceType
import io.github.staninna.warosso.domain.Guidance
import io.github.staninna.warosso.ui.buildingName
import io.github.staninna.warosso.ui.components.BigButton
import io.github.staninna.warosso.ui.directionsLine
import io.github.staninna.warosso.ui.distance
import io.github.staninna.warosso.ui.doorHintTitle
import io.github.staninna.warosso.ui.entranceName
import io.github.staninna.warosso.ui.entranceTypeName
import io.github.staninna.warosso.ui.floorStepTitle

/**
 * Where to go, in the order you walk it: building, entrance, floor, door. Collapsed it is a
 * summary with the distance. Expanded, which arrival does by itself, it fills the screen with
 * one large step per line.
 */
@Composable
fun DirectionsCard(
    data: ComplexData,
    guidance: Guidance,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val route = guidance.route
    val building = resources.buildingName(data, route.entrance)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(if (expanded) Modifier.fillMaxHeight() else Modifier),
    ) {
        Column(
            modifier = Modifier
                .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // The handle and summary toggle the card; arrival keeps it open.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !guidance.arrived, onClickLabel = stringResource(R.string.action_toggle_directions), onClick = onToggle),
            ) {
                Box(
                    Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .align(Alignment.CenterHorizontally),
                )
                AnimatedVisibility(guidance.arrived) { ArrivalHeader() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(building, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!expanded) {
                            Text(
                                text = resources.directionsLine(data, guidance.number, route),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    DistanceBadge(guidance.distanceM)
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val entranceName = resources.entranceName(data, route.entrance)
                    when (route.entrance.type) {
                        EntranceType.STAIRWELL -> Step(R.drawable.ic_stairs, entranceName, resources.entranceTypeName(EntranceType.STAIRWELL))
                        EntranceType.INTERCOM -> Step(R.drawable.ic_doorbell, stringResource(R.string.step_ring, guidance.number), entranceName)
                    }
                    Step(R.drawable.ic_floor, resources.floorStepTitle(route))
                    Step(R.drawable.ic_door_front, resources.doorHintTitle(route.door.hint), stringResource(R.string.step_number, guidance.number))
                    route.entrance.note?.let { Step(R.drawable.ic_info, it) }
                }
                BigButton(stringResource(R.string.action_done), onDone, icon = R.drawable.ic_check)
            } else {
                route.entrance.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DistanceBadge(distanceM: Double?) {
    val resources = LocalResources.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            if (distanceM == null) {
                LoadingIndicator(Modifier.size(36.dp))
            } else {
                Text(
                    text = resources.distance(distanceM),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** "You're here", with a slowly turning shape so the change is noticed in the corner of an eye. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArrivalHeader() {
    val turn by rememberInfiniteTransition(label = "arrival").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "turn",
    )
    Row(
        modifier = Modifier.padding(bottom = 20.dp).semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .graphicsLayer { rotationZ = turn }
                    .background(MaterialTheme.colorScheme.tertiary, MaterialShapes.Sunny.toShape()),
            )
            Icon(
                painterResource(R.drawable.ic_flag_fill),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.arrived_title), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun Step(@DrawableRes icon: Int, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

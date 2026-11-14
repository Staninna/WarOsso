package io.github.staninna.warosso.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GroupRadius = 28.dp
private val SeamRadius = 8.dp

/** A titled group of [ConnectedItem]s: one rounded block, split by thin seams. */
@Composable
fun ConnectedSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier.clip(RoundedCornerShape(GroupRadius)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * A row in a [ConnectedSection]. Its corners are square-ish where it meets its neighbours and
 * swell to fully round while pressed.
 */
@Composable
fun ConnectedItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val radius: Dp by animateDpAsState(
        targetValue = if (pressed && onClick != null) GroupRadius else SeamRadius,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "connectedItemCorner",
    )
    val shape = RoundedCornerShape(radius)

    Surface(
        color = containerColor,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource, ripple(), onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.padding(PaddingValues(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Box(
                        modifier = Modifier.padding(end = 16.dp).defaultMinSize(24.dp, 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { leading() }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (trailing != null) {
                    Box(
                        modifier = Modifier.padding(start = 16.dp).defaultMinSize(40.dp, 40.dp),
                        contentAlignment = Alignment.Center,
                    ) { trailing() }
                }
            }
            below?.invoke(this)
        }
    }
}

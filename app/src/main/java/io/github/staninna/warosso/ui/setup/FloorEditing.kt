package io.github.staninna.warosso.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.fillRange
import io.github.staninna.warosso.domain.inserted
import io.github.staninna.warosso.domain.moved
import io.github.staninna.warosso.domain.without
import io.github.staninna.warosso.ui.components.BigButton
import io.github.staninna.warosso.ui.components.TonalIconButton
import io.github.staninna.warosso.ui.floorTitle

private val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)

private fun digits(text: String) = text.filter(Char::isDigit).take(4)

/**
 * One floor's doors in walking order. Tap a door to select it, then move it a place left or
 * right or take it out; "+" inserts a number after the selected door, or at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloorCard(
    floor: FloorDoors,
    bagNumbers: List<Int>,
    onDoorsChange: ((List<Int>) -> List<Int>) -> Unit,
    onDelete: () -> Unit,
) {
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    var selected by remember(floor.floor) { mutableStateOf<Int?>(null) }
    var adding by rememberSaveable(floor.floor) { mutableStateOf(false) }
    val selectedIndex = selected?.takeIf { it in floor.doors.indices }

    fun move(by: Int) {
        val index = selectedIndex ?: return
        if (index + by !in floor.doors.indices) return
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onDoorsChange { it.moved(index, by) }
        selected = index + by
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(resources.floorTitle(floor.floor), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = pluralStringResource(R.plurals.count_doors, floor.doors.size, floor.doors.size) + " · " + stringResource(R.string.floor_order_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TonalIconButton(R.drawable.ic_swap_horiz, stringResource(R.string.floor_reverse), onClick = { selected = null; onDoorsChange { it.reversed() } }, size = 48.dp)
                Spacer(Modifier.width(8.dp))
                TonalIconButton(R.drawable.ic_delete, stringResource(R.string.floor_delete), onDelete, size = 48.dp)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                floor.doors.forEachIndexed { index, door ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { selected = index.takeIf { it != selectedIndex } },
                        label = { Text(door.toString(), style = MaterialTheme.typography.titleMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            // A door BAG does not know stands out, so typos are easy to spot.
                            containerColor = if (door in bagNumbers) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { adding = true },
                    label = { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.door_add)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            AnimatedVisibility(selectedIndex != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TonalIconButton(R.drawable.ic_chevron_left, stringResource(R.string.door_move_left), onClick = { move(-1) })
                    TonalIconButton(R.drawable.ic_chevron_right, stringResource(R.string.door_move_right), onClick = { move(1) })
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            selectedIndex?.let { index -> onDoorsChange { it.without(index) } }
                            selected = null
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_remove), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.door_remove))
                    }
                }
            }
        }
    }

    if (adding) {
        AddDoorDialog(
            bagNumbers = bagNumbers,
            onAdd = { number -> onDoorsChange { it.inserted(number, after = selectedIndex) } },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun AddDoorDialog(bagNumbers: List<Int>, onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val number = text.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.door_add)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = digits(it) },
                singleLine = true,
                label = { Text(stringResource(R.string.door_number)) },
                supportingText = {
                    if (number != null && number !in bagNumbers) Text(stringResource(R.string.door_not_in_bag))
                },
                keyboardOptions = numberKeyboard.copy(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = number != null, onClick = { number?.let(onAdd); onDismiss() }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A new floor from two numbers: "from" is the leftmost door, "to" the rightmost. The list in
 * between is filled with the numbers BAG has, in the typed direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFloorSheet(
    initialFloor: Int,
    existingFloors: List<Int>,
    bagNumbers: List<Int>,
    onAdd: (FloorDoors) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var floor by rememberSaveable { mutableIntStateOf(initialFloor) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    val doors = remember(from, to, bagNumbers) {
        val first = from.toIntOrNull()
        val last = to.toIntOrNull()
        if (first == null || last == null) emptyList() else fillRange(first, last, bagNumbers)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = resources.floorTitle(floor),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TonalIconButton(R.drawable.ic_remove, stringResource(R.string.floor_lower), onClick = { if (floor > 0) floor-- })
                Spacer(Modifier.width(8.dp))
                TonalIconButton(R.drawable.ic_add, stringResource(R.string.floor_higher), onClick = { floor++ })
            }
            Text(
                text = stringResource(R.string.floor_range_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = digits(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.floor_from)) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = numberKeyboard,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = digits(it) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.floor_to)) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = numberKeyboard.copy(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = when {
                    from.isEmpty() || to.isEmpty() -> stringResource(R.string.floor_preview_empty)
                    doors.isEmpty() -> stringResource(R.string.floor_preview_none)
                    else -> pluralStringResource(R.plurals.count_doors, doors.size, doors.size) + ": " + doors.joinToString(" · ")
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (doors.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            BigButton(
                text = stringResource(if (floor in existingFloors) R.string.floor_replace else R.string.floor_add),
                onClick = { onAdd(FloorDoors(floor, doors)); onDismiss() },
                enabled = doors.isNotEmpty(),
                icon = R.drawable.ic_check,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

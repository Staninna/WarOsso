package io.github.staninna.warosso.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.EntranceType
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.nextFloor
import io.github.staninna.warosso.domain.suggestFloorsFor
import io.github.staninna.warosso.domain.withDoors
import io.github.staninna.warosso.domain.withFloor
import io.github.staninna.warosso.domain.withoutFloor
import io.github.staninna.warosso.ui.appViewModel
import io.github.staninna.warosso.ui.buildingName
import io.github.staninna.warosso.ui.buildingNumbers
import io.github.staninna.warosso.ui.components.BigButton
import io.github.staninna.warosso.ui.components.ConnectedChoice
import io.github.staninna.warosso.ui.components.ConnectedItem
import io.github.staninna.warosso.ui.components.ConnectedSection
import io.github.staninna.warosso.ui.components.ScreenTopBar
import io.github.staninna.warosso.ui.components.TonalIconButton
import io.github.staninna.warosso.ui.entranceName
import io.github.staninna.warosso.ui.entranceTypeName
import io.github.staninna.warosso.ui.floorTitle
import io.github.staninna.warosso.ui.numberRange

private enum class EditorDialog { DELETE, BUILDING, ADD_FLOOR }

@Composable
fun EntranceEditorScreen(entranceId: Long, onBack: () -> Unit) {
    val viewModel = appViewModel { EntranceEditorViewModel(it.complex, entranceId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        // Deleted, here or by an import.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val (data, entrance) = current
    val resources = LocalResources.current
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    val suggestions = remember(data, entrance) { data.suggestFloorsFor(entrance) }

    Scaffold(
        topBar = {
            ScreenTopBar(resources.entranceName(data, entrance), onBack) {
                TonalIconButton(
                    icon = R.drawable.ic_delete,
                    contentDescription = stringResource(R.string.entrance_delete),
                    onClick = { dialog = EditorDialog.DELETE },
                    size = 48.dp,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
        ) {
            DetailsSection(data, entrance, viewModel::update, onPickBuilding = { dialog = EditorDialog.BUILDING })

            if (suggestions.isNotEmpty()) {
                SuggestionsSection(
                    suggestions = suggestions,
                    onAdd = { floor -> viewModel.update { it.withFloor(floor) } },
                    onAddAll = { viewModel.update { suggestions.fold(it, Entrance::withFloor) } },
                )
            }

            entrance.floors.forEach { floor ->
                FloorCard(
                    floor = floor,
                    bagNumbers = data.bagNumbers,
                    onDoorsChange = { change -> viewModel.update { it.withDoors(floor.floor, change) } },
                    onDelete = { viewModel.update { it.withoutFloor(floor.floor) } },
                )
            }

            BigButton(
                text = stringResource(R.string.floor_add),
                onClick = { dialog = EditorDialog.ADD_FLOOR },
                icon = R.drawable.ic_add,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        EditorDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.entrance_delete_title)) },
            text = { Text(stringResource(R.string.entrance_delete_body)) },
            confirmButton = { TextButton(onClick = viewModel::delete) { Text(stringResource(R.string.entrance_delete)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        EditorDialog.BUILDING -> BuildingPicker(
            data = data,
            selected = entrance.buildingOsmId,
            onPick = { osmId -> viewModel.update { it.copy(buildingOsmId = osmId) } },
            onDismiss = { dialog = null },
        )
        EditorDialog.ADD_FLOOR -> AddFloorSheet(
            initialFloor = entrance.nextFloor,
            existingFloors = entrance.floors.map { it.floor },
            bagNumbers = data.bagNumbers,
            onAdd = { floor -> viewModel.update { it.withFloor(floor) } },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun DetailsSection(
    data: ComplexData,
    entrance: Entrance,
    onChange: ((Entrance) -> Entrance) -> Unit,
    onPickBuilding: () -> Unit,
) {
    val resources = LocalResources.current
    // The text fields own what is typed; the database follows. Reading the text back from the
    // database on every key press would make the cursor jump.
    var name by rememberSaveable(entrance.id) { mutableStateOf(entrance.name.orEmpty()) }
    var note by rememberSaveable(entrance.id) { mutableStateOf(entrance.note.orEmpty()) }

    ConnectedSection(title = stringResource(R.string.entrance_section)) {
        ConnectedItem(title = stringResource(R.string.entrance_type)) {
            Spacer(Modifier.height(12.dp))
            ConnectedChoice(
                options = EntranceType.entries,
                selected = entrance.type,
                onSelect = { type -> onChange { it.copy(type = type, innerStairs = it.innerStairs && type == EntranceType.INTERCOM) } },
                label = { resources.entranceTypeName(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (entrance.type) {
                        EntranceType.STAIRWELL -> R.string.entrance_type_stairwell_hint
                        EntranceType.INTERCOM -> R.string.entrance_type_intercom_hint
                        EntranceType.DOOR -> R.string.entrance_type_door_hint
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(entrance.type == EntranceType.INTERCOM) {
            ConnectedItem(
                title = stringResource(R.string.entrance_inner_stairs),
                subtitle = stringResource(R.string.entrance_inner_stairs_hint),
                trailing = { Switch(checked = entrance.innerStairs, onCheckedChange = null) },
                onClick = { onChange { it.copy(innerStairs = !it.innerStairs) } },
            )
        }
        ConnectedItem(title = stringResource(R.string.entrance_name)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { typed ->
                    name = typed
                    onChange { it.copy(name = typed.trim().ifEmpty { null }) }
                },
                singleLine = true,
                placeholder = { Text(resources.entranceName(data, entrance.copy(name = null))) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ConnectedItem(title = stringResource(R.string.entrance_note)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { typed ->
                    note = typed
                    onChange { it.copy(note = typed.trim().ifEmpty { null }) }
                },
                placeholder = { Text(stringResource(R.string.entrance_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ConnectedItem(
            title = stringResource(R.string.entrance_building),
            subtitle = resources.buildingName(data, entrance),
            leading = { Icon(painterResource(R.drawable.ic_apartment), contentDescription = null) },
            trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
            onClick = onPickBuilding,
        )
    }
}

/** Floors read off the address list: the building's numbers, split where the numbering jumps. */
@Composable
private fun SuggestionsSection(suggestions: List<FloorDoors>, onAdd: (FloorDoors) -> Unit, onAddAll: () -> Unit) {
    val resources = LocalResources.current
    ConnectedSection(title = stringResource(R.string.suggestions_title)) {
        suggestions.forEach { floor ->
            ConnectedItem(
                title = stringResource(R.string.suggestion_item, resources.floorTitle(floor.floor), resources.numberRange(floor.doors.first(), floor.doors.last())),
                subtitle = pluralStringResource(R.plurals.count_doors, floor.doors.size, floor.doors.size),
                trailing = { FilledTonalButton(onClick = { onAdd(floor) }) { Text(stringResource(R.string.action_add)) } },
            )
        }
        ConnectedItem(
            title = stringResource(R.string.suggestions_add_all),
            subtitle = stringResource(R.string.suggestions_hint),
            leading = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onAddAll,
        )
    }
}

@Composable
private fun BuildingPicker(data: ComplexData, selected: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entrance_building)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                data.complexBuildings.forEach { building ->
                    ConnectedItem(
                        title = resources.buildingName(data, building),
                        subtitle = resources.buildingNumbers(data, building),
                        leading = { RadioButton(selected = building.osmId == selected, onClick = null) },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onPick(building.osmId); onDismiss() },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

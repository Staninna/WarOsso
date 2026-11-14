package io.github.staninna.warosso.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.Building
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.ui.buildingName
import io.github.staninna.warosso.ui.buildingNumbers
import io.github.staninna.warosso.ui.components.ConnectedItem
import io.github.staninna.warosso.ui.components.ConnectedSection
import io.github.staninna.warosso.ui.components.Placeholder

/** Every building with house numbers, to name it and to say whether its ground floor needs an entrance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingsSheet(data: ComplexData, onChange: (osmId: Long, name: String, streetDoors: Boolean) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ConnectedSection(title = stringResource(R.string.setup_buildings)) {
                data.complexBuildings.forEach { building ->
                    val numbers = data.numbersOf(building.osmId)
                    val entrances = data.entrances.count { it.buildingOsmId == building.osmId }
                    val ownDoors = data.streetDoorNumbers(building.osmId).size
                    ConnectedItem(
                        title = resources.buildingName(data, building),
                        subtitle = listOfNotNull(
                            resources.buildingNumbers(data, building),
                            pluralStringResource(R.plurals.count_numbers, numbers.size, numbers.size),
                            pluralStringResource(R.plurals.count_entrances, entrances, entrances),
                            pluralStringResource(R.plurals.count_own_doors, ownDoors, ownDoors).takeIf { building.streetDoors },
                        ).joinToString(" · "),
                        leading = { Icon(painterResource(R.drawable.ic_apartment), contentDescription = null) },
                        trailing = { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit)) },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = { editing = building.osmId },
                    )
                }
            }
        }
    }

    editing?.let(data::building)?.let { building ->
        BuildingDialog(
            data = data,
            building = building,
            onSave = { name, streetDoors -> onChange(building.osmId, name, streetDoors) },
            onDismiss = { editing = null },
        )
    }
}

/** A building's name, and whether its ground-floor homes have their front door on the street. */
@Composable
private fun BuildingDialog(data: ComplexData, building: Building, onSave: (name: String, streetDoors: Boolean) -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    var name by rememberSaveable { mutableStateOf(building.customName.orEmpty()) }
    var streetDoors by rememberSaveable { mutableStateOf(building.streetDoors) }
    // What the building is called when the field is left empty.
    val fallback = resources.buildingName(data, building.copy(customName = null))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resources.buildingName(data, building)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.building_rename_label)) },
                    placeholder = { Text(fallback) },
                    supportingText = { Text(stringResource(R.string.building_rename_hint, fallback)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                ConnectedItem(
                    title = stringResource(R.string.building_street_doors),
                    subtitle = stringResource(R.string.building_street_doors_hint),
                    trailing = { Switch(checked = streetDoors, onCheckedChange = null) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { streetDoors = !streetDoors },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, streetDoors); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The validation view: house numbers in the complex that lead nowhere yet, per building. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MissingNumbersSheet(data: ComplexData, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val missing = data.unservedNumbers.toSet()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (missing.isEmpty()) {
            Placeholder(
                title = stringResource(R.string.missing_none_title),
                subtitle = stringResource(R.string.missing_none_body),
                icon = R.drawable.ic_check_circle_fill,
                shape = MaterialShapes.Clover4Leaf,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(pluralStringResource(R.plurals.missing_title, missing.size, missing.size), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.missing_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                data.complexBuildings
                    .map { building -> building to data.numbersOf(building.osmId).filter(missing::contains) }
                    .filter { (_, numbers) -> numbers.isNotEmpty() }
                    .forEach { (building, numbers) -> MissingGroup(resources.buildingName(data, building), numbers) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingGroup(buildingName: String, numbers: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(buildingName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            numbers.forEach { number ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                    Text(number.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

/** The complex is one street; this picks which of the streets in the data it is. */
@Composable
fun StreetDialog(data: ComplexData, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_street_title)) },
        text = {
            Column {
                data.streets.forEach { street ->
                    ConnectedItem(
                        title = street,
                        leading = { RadioButton(selected = street == data.complex.street, onClick = null) },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onPick(street); onDismiss() },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

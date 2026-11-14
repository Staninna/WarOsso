package io.github.staninna.warosso.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.BuildConfig
import io.github.staninna.warosso.R
import io.github.staninna.warosso.data.AppLanguage
import io.github.staninna.warosso.data.ImportException
import io.github.staninna.warosso.data.ThemeMode
import io.github.staninna.warosso.domain.Setup
import io.github.staninna.warosso.ui.appViewModel
import io.github.staninna.warosso.ui.components.ConnectedChoice
import io.github.staninna.warosso.ui.components.ConnectedItem
import io.github.staninna.warosso.ui.components.ConnectedSection
import io.github.staninna.warosso.ui.components.ScreenTopBar

private const val EXPORT_FILE_NAME = "warosso.json"
private const val JSON_MIME = "application/json"

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenRefresh: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings, it.complex, it.documents, it.simulator) }
    // MainActivity only composes the app once the settings are read.
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbars = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openImport)
    }

    // Good news goes in a snackbar; a refused file gets a dialog that stays until read.
    LaunchedEffect(result) {
        val message = when (result) {
            TransferResult.Exported -> R.string.export_done
            TransferResult.Imported -> R.string.import_done
            else -> return@LaunchedEffect
        }
        viewModel.clearResult()
        snackbars.showSnackbar(resources.getString(message))
    }

    Scaffold(
        topBar = { ScreenTopBar(stringResource(R.string.settings_title), onBack) },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ConnectedSection(title = stringResource(R.string.settings_guidance)) {
                SwitchItem(
                    icon = R.drawable.ic_screen_rotation,
                    title = stringResource(R.string.settings_auto_switch),
                    subtitle = stringResource(R.string.settings_auto_switch_hint),
                    checked = settings.autoSwitchView,
                    onChange = { on -> viewModel.update { it.copy(autoSwitchView = on) } },
                )
                SwitchItem(
                    icon = R.drawable.ic_vibration,
                    title = stringResource(R.string.settings_vibrate),
                    subtitle = stringResource(R.string.settings_vibrate_hint),
                    checked = settings.vibrateOnArrival,
                    onChange = { on -> viewModel.update { it.copy(vibrateOnArrival = on) } },
                )
            }

            ConnectedSection(title = stringResource(R.string.settings_appearance)) {
                ConnectedItem(
                    title = stringResource(R.string.settings_language),
                    leading = { Icon(painterResource(R.drawable.ic_language), contentDescription = null) },
                ) {
                    Spacer(Modifier.height(12.dp))
                    ConnectedChoice(
                        options = AppLanguage.entries,
                        selected = AppLanguage.current(context),
                        onSelect = { it.apply(context) },
                        label = { language ->
                            when (language) {
                                AppLanguage.SYSTEM -> stringResource(R.string.settings_follow_system)
                                // Language names stay in their own language, so they can always be found.
                                AppLanguage.ENGLISH -> "English"
                                AppLanguage.DUTCH -> "Nederlands"
                            }
                        },
                    )
                }
                ConnectedItem(
                    title = stringResource(R.string.settings_theme),
                    leading = { Icon(painterResource(R.drawable.ic_dark_mode), contentDescription = null) },
                ) {
                    Spacer(Modifier.height(12.dp))
                    ConnectedChoice(
                        options = ThemeMode.entries,
                        selected = settings.theme,
                        onSelect = { theme -> viewModel.update { it.copy(theme = theme) } },
                        label = { theme ->
                            stringResource(
                                when (theme) {
                                    ThemeMode.SYSTEM -> R.string.settings_follow_system
                                    ThemeMode.LIGHT -> R.string.settings_theme_light
                                    ThemeMode.DARK -> R.string.settings_theme_dark
                                },
                            )
                        },
                    )
                }
            }

            ConnectedSection(title = stringResource(R.string.settings_data)) {
                LinkItem(R.drawable.ic_upload, stringResource(R.string.settings_export), stringResource(R.string.settings_export_hint)) {
                    exportLauncher.launch(EXPORT_FILE_NAME)
                }
                LinkItem(R.drawable.ic_download, stringResource(R.string.settings_import), stringResource(R.string.settings_import_hint)) {
                    // Some file managers label .json files as plain text or as nothing at all.
                    importLauncher.launch(arrayOf(JSON_MIME, "text/*", "application/octet-stream"))
                }
                LinkItem(R.drawable.ic_sync, stringResource(R.string.settings_refresh), stringResource(R.string.settings_refresh_hint), onOpenRefresh)
            }

            AboutSection()

            viewModel.simulator?.let { DebugSection(it) }
            Spacer(Modifier.height(32.dp))
        }
    }

    pendingImport?.let { setup -> ConfirmImportDialog(setup, viewModel::confirmImport, viewModel::cancelImport) }

    when (val failure = result) {
        is TransferResult.Refused -> MessageDialog(stringResource(R.string.import_failed_title), failure.reason.describe(), viewModel::clearResult)
        TransferResult.FileError -> MessageDialog(stringResource(R.string.file_error_title), stringResource(R.string.file_error_body), viewModel::clearResult)
        else -> Unit
    }
}

@Composable
private fun ImportException.describe(): String = when (this) {
    is ImportException.NewerSchema -> stringResource(R.string.import_newer_schema, found, supported)
    is ImportException.NotAnExport -> stringResource(R.string.import_not_an_export)
    is ImportException.BrokenReference -> stringResource(R.string.import_broken_reference, buildingOsmId)
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    ConnectedSection(title = stringResource(R.string.settings_about)) {
        ConnectedItem(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            leading = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
        )
        LinkItem(R.drawable.ic_gavel, stringResource(R.string.about_license), stringResource(R.string.about_license_hint)) {
            uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html")
        }
        LinkItem(R.drawable.ic_map_fill, stringResource(R.string.about_osm), stringResource(R.string.about_osm_hint)) {
            uriHandler.openUri("https://www.openstreetmap.org/copyright")
        }
        LinkItem(R.drawable.ic_map_fill, stringResource(R.string.about_openfreemap), stringResource(R.string.about_openfreemap_hint)) {
            uriHandler.openUri("https://openfreemap.org")
        }
        ConnectedItem(
            title = stringResource(R.string.about_font),
            subtitle = stringResource(R.string.about_font_hint),
            leading = { Icon(painterResource(R.drawable.ic_license), contentDescription = null) },
        )
    }
}

@Composable
private fun SwitchItem(@DrawableRes icon: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ConnectedItem(
        title = title,
        subtitle = subtitle,
        leading = { Icon(painterResource(icon), contentDescription = null) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun LinkItem(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    ConnectedItem(
        title = title,
        subtitle = subtitle,
        leading = { Icon(painterResource(icon), contentDescription = null) },
        trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun ConfirmImportDialog(setup: Setup, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val entrances = setup.entrances.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.import_confirm_body,
                    pluralStringResource(R.plurals.count_entrances, entrances, entrances),
                    setup.complex.street,
                ),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_confirm_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MessageDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

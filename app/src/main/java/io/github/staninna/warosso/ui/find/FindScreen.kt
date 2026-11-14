package io.github.staninna.warosso.ui.find

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.R
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Lookup
import io.github.staninna.warosso.ui.appViewModel
import io.github.staninna.warosso.ui.components.BigButton
import io.github.staninna.warosso.ui.components.Placeholder
import io.github.staninna.warosso.ui.components.TonalIconButton
import io.github.staninna.warosso.ui.components.rememberPermissionState
import io.github.staninna.warosso.ui.theme.HouseNumberStyle

@Composable
fun FindScreen(onOpenSetup: () -> Unit, onOpenSettings: () -> Unit) {
    val viewModel = appViewModel { FindViewModel(it.complex, it.settings, it.location, it.orientation, it.buzzer, it.simulator) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val canMoveUser by viewModel.canMoveUser.collectAsStateWithLifecycle()
    val headingAgainstCourse by viewModel.headingAgainstCourse.collectAsStateWithLifecycle()

    val locationPermission = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    LaunchedEffect(locationPermission.granted) { viewModel.setLocationPermitted(locationPermission.granted) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // Keyed on the stage, not its content: a new fix updates the guidance pane in place.
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "findStage",
        ) { stage ->
            when (stage) {
                FindUiState.Loading -> Placeholder(stringResource(R.string.loading), loading = true)

                is FindUiState.Welcome -> Welcome(stage.data, onOpenSetup, onOpenSettings)

                is FindUiState.Search -> SearchPane(
                    data = stage.data,
                    query = stage.query,
                    miss = stage.miss,
                    onQueryChange = viewModel::onQueryChange,
                    onSubmit = viewModel::submit,
                    onPick = viewModel::find,
                    onOpenSetup = onOpenSetup,
                    onOpenSettings = onOpenSettings,
                )

                is FindUiState.Guiding -> {
                    BackHandler(onBack = viewModel::stop)
                    GuidancePane(
                        state = stage,
                        locationPermission = locationPermission,
                        viewMode = viewMode,
                        pose = viewModel.pose,
                        mapHeading = viewModel.mapHeading,
                        headingAgainstWalk = headingAgainstCourse != null,
                        canMoveUser = canMoveUser,
                        onMoveUser = viewModel::moveSimulatedUser,
                        onToggleViewMode = viewModel::toggleViewMode,
                        onTurnHeading = viewModel::turnHeading,
                        onAlignHeadingToWalk = viewModel::alignHeadingToWalk,
                        onFollowCompass = viewModel::followCompass,
                        onStop = viewModel::stop,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBar(onOpenSetup: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TonalIconButton(R.drawable.ic_add_location_alt, stringResource(R.string.setup_title), onOpenSetup)
        Spacer(Modifier.width(8.dp))
        TonalIconButton(R.drawable.ic_settings, stringResource(R.string.settings_title), onOpenSettings)
    }
}

/** First launch, and any time the setup is empty: there is nothing to search yet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Welcome(data: ComplexData, onOpenSetup: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        HomeBar(onOpenSetup, onOpenSettings)
        Placeholder(
            title = stringResource(R.string.welcome_title),
            subtitle = stringResource(R.string.welcome_body, data.bagNumbers.size, data.complex.street),
            icon = R.drawable.ic_door_front_fill,
            shape = MaterialShapes.Cookie12Sided,
            modifier = Modifier.weight(1f),
        )
        BigButton(stringResource(R.string.welcome_action), onOpenSetup, icon = R.drawable.ic_add_location_alt)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SearchPane(
    data: ComplexData,
    query: String,
    miss: Lookup.Miss?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPick: (Int) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeBar(onOpenSetup, onOpenSettings)
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.find_prompt),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = HouseNumberStyle.copy(color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.Center) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.find_placeholder),
                            style = HouseNumberStyle,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    field()
                }
            },
        )
        AnimatedContent(targetState = miss, label = "searchResult", modifier = Modifier.fillMaxWidth()) { result ->
            when (result) {
                is Lookup.Unknown -> UnknownNumberCard(result, onPick)
                is Lookup.NotSetUp -> NotSetUpCard(result.number, onOpenSetup)
                null -> SetupProgress(data)
            }
        }
        Spacer(Modifier.weight(1f))
        BigButton(
            text = stringResource(R.string.find_action),
            onClick = onSubmit,
            enabled = query.isNotEmpty(),
            icon = R.drawable.ic_navigation_fill,
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** A quiet line under the number field: how much of the complex is set up. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupProgress(data: ComplexData) {
    val total = data.bagNumbers.size
    val served = total - data.unservedNumbers.size
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (served < total) {
            LinearWavyProgressIndicator(progress = { served.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = stringResource(R.string.find_progress, served, total, data.complex.street),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UnknownNumberCard(result: Lookup.Unknown, onPick: (Int) -> Unit) {
    MissCard(
        title = stringResource(R.string.not_found_title, result.number),
        body = stringResource(if (result.suggestions.isEmpty()) R.string.not_found_no_suggestions else R.string.not_found_did_you_mean),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            result.suggestions.forEach { suggestion ->
                BigButton(suggestion.toString(), onClick = { onPick(suggestion) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NotSetUpCard(number: Int, onOpenSetup: () -> Unit) {
    MissCard(
        title = stringResource(R.string.not_set_up_title, number),
        body = stringResource(R.string.not_set_up_body),
    ) {
        BigButton(stringResource(R.string.not_set_up_action), onOpenSetup, icon = R.drawable.ic_add_location_alt)
    }
}

@Composable
private fun MissCard(title: String, body: String, action: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

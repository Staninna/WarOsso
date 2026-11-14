package io.github.staninna.warosso.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.staninna.warosso.R
import io.github.staninna.warosso.data.ComplexRepository
import io.github.staninna.warosso.data.OverpassClient
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.OsmData
import io.github.staninna.warosso.domain.RefreshDiff
import io.github.staninna.warosso.domain.diffAgainst
import io.github.staninna.warosso.ui.appViewModel
import io.github.staninna.warosso.ui.components.BigButton
import io.github.staninna.warosso.ui.components.ConnectedItem
import io.github.staninna.warosso.ui.components.ConnectedSection
import io.github.staninna.warosso.ui.components.Placeholder
import io.github.staninna.warosso.ui.components.ScreenTopBar
import io.github.staninna.warosso.ui.entranceName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface RefreshState {
    data object Loading : RefreshState
    data object Failed : RefreshState

    /** Fresh OSM data next to what it would change. [data] is the complex the diff was made against. */
    data class Review(val data: ComplexData, val fresh: OsmData, val diff: RefreshDiff, val applied: Boolean = false) : RefreshState
}

class OsmRefreshViewModel(private val complex: ComplexRepository, private val overpass: OverpassClient) : ViewModel() {
    val state = MutableStateFlow<RefreshState>(RefreshState.Loading)

    init {
        fetch()
    }

    fun fetch() {
        state.value = RefreshState.Loading
        viewModelScope.launch {
            state.value = try {
                val data = complex.data.filterNotNull().first()
                val fresh = overpass.fetch(data.complex.polygon)
                val diff = data.diffAgainst(fresh)
                // With no number added or removed there is nothing to review; outlines and names are just kept fresh.
                if (diff.isEmpty) complex.applyOsm(fresh)
                RefreshState.Review(data, fresh, diff, applied = diff.isEmpty)
            } catch (_: IOException) {
                RefreshState.Failed
            }
        }
    }

    /** Takes the fresh address list. Entrances, doors and names are the owner's and are not touched. */
    fun apply() {
        val review = state.value as? RefreshState.Review ?: return
        viewModelScope.launch {
            complex.applyOsm(review.fresh)
            state.value = review.copy(applied = true)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OsmRefreshScreen(onBack: () -> Unit, onOpenEntrance: (Long) -> Unit) {
    val viewModel = appViewModel { OsmRefreshViewModel(it.complex, it.overpass) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.refresh_title), onBack) }) { padding ->
        when (val current = state) {
            RefreshState.Loading -> Placeholder(
                title = stringResource(R.string.refresh_loading),
                loading = true,
                modifier = Modifier.padding(padding),
            )
            RefreshState.Failed -> Placeholder(
                title = stringResource(R.string.refresh_failed_title),
                subtitle = stringResource(R.string.refresh_failed_body),
                icon = R.drawable.ic_sync,
                shape = MaterialShapes.Cookie6Sided,
                modifier = Modifier.padding(padding),
            ) {
                FilledTonalButton(onClick = viewModel::fetch) { Text(stringResource(R.string.action_retry)) }
            }
            is RefreshState.Review -> if (current.diff.isEmpty) {
                Placeholder(
                    title = stringResource(R.string.refresh_same_title),
                    subtitle = stringResource(R.string.refresh_same_body),
                    icon = R.drawable.ic_check_circle_fill,
                    shape = MaterialShapes.Clover4Leaf,
                    modifier = Modifier.padding(padding),
                )
            } else {
                RefreshReview(current, onApply = viewModel::apply, onOpenEntrance = onOpenEntrance, modifier = Modifier.padding(padding))
            }
        }
    }
}

/** The diff to review: what is new, what is gone, and which entrances that touches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefreshReview(review: RefreshState.Review, onApply: () -> Unit, onOpenEntrance: (Long) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val diff = review.diff
    val colors = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summaryOf(diff), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(if (review.applied) R.string.refresh_applied else R.string.refresh_review_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            NumberChips(stringResource(R.string.refresh_new), diff.added, colors.primaryContainer, colors.onPrimaryContainer)
            NumberChips(stringResource(R.string.refresh_removed), diff.removed, colors.errorContainer, colors.onErrorContainer)

            if (diff.affected.isNotEmpty()) {
                ConnectedSection(title = stringResource(R.string.refresh_affected)) {
                    diff.affected.forEach { affected ->
                        ConnectedItem(
                            title = resources.entranceName(review.data, affected.entrance),
                            subtitle = listOfNotNull(
                                affected.removedDoors.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.refresh_affected_removed, it.joinToString()) },
                                affected.newNumbers.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.refresh_affected_new, it.joinToString()) },
                            ).joinToString("\n"),
                            trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                            onClick = { onOpenEntrance(affected.entrance.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (!review.applied) {
            BigButton(
                text = stringResource(R.string.refresh_apply),
                onClick = onApply,
                icon = R.drawable.ic_check,
                modifier = Modifier.navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

/** "3 new numbers, 1 removed: 57". */
@Composable
private fun summaryOf(diff: RefreshDiff): String = listOfNotNull(
    diff.added.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.refresh_added, it.size, it.size) },
    diff.removed.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.refresh_removed, it.size, it.size, it.joinToString()) },
).joinToString(", ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberChips(title: String, numbers: List<Int>, container: Color, content: Color) {
    if (numbers.isEmpty()) return
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            numbers.forEach { number ->
                Surface(shape = RoundedCornerShape(12.dp), color = container, contentColor = content) {
                    Text(number.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

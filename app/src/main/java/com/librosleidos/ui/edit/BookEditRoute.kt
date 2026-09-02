package com.librosleidos.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest

/**
 * Task 6.3 (design: container/presentational split) — the container that
 * binds [BookEditViewModel] state and events to the stateless
 * [BookEditScreen]. Scaffold-only glue (prop wiring, no logic) — TDD-exempt,
 * covered indirectly by [BookEditViewModelTest] and [BookEditScreenTest].
 *
 * `viewModel` is constructor-injected rather than looked up from
 * `AppContainer`: real DI wiring is Phase 7. [onSaveCompleted] and
 * [onCancelRequested] are navigation callbacks — this route has no NavHost
 * to call back into yet (Phase 7).
 */
@Composable
fun BookEditRoute(
    viewModel: BookEditViewModel,
    onSaveCompleted: () -> Unit,
    onCancelRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BookEditEffect.SaveCompleted -> onSaveCompleted()
            }
        }
    }

    BookEditScreen(
        state = state,
        onTitleChanged = viewModel::onTitleChanged,
        onAuthorChanged = viewModel::onAuthorChanged,
        onRatingChanged = viewModel::onRatingChanged,
        onReviewChanged = viewModel::onReviewChanged,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchRequested = viewModel::onSearchRequested,
        onResultSelected = viewModel::onResultSelected,
        onSaveRequested = viewModel::onSaveRequested,
        onCancelRequested = onCancelRequested,
        modifier = modifier,
    )
}

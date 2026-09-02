package com.librosleidos.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Task 5.3 (design: container/presentational split) — the container that
 * binds [BookListViewModel] state and events to the stateless
 * [BookListScreen]. Scaffold-only glue (prop wiring, no logic) — TDD-exempt,
 * covered indirectly by [BookListViewModelTest] and [BookListScreenTest].
 *
 * `viewModel` is constructor-injected rather than looked up from
 * `AppContainer`: real DI wiring is Phase 7.
 */
@Composable
fun BookListRoute(
    viewModel: BookListViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    BookListScreen(
        state = state,
        onSortSelected = viewModel::onSortSelected,
        onMinRatingSelected = viewModel::onMinRatingSelected,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        modifier = modifier,
    )
}

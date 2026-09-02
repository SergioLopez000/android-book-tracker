package com.librosleidos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.librosleidos.di.AppContainer
import com.librosleidos.ui.edit.BookEditRoute
import com.librosleidos.ui.edit.BookEditViewModel
import com.librosleidos.ui.list.BookListRoute
import com.librosleidos.ui.list.BookListViewModel

/**
 * Task 7.2 (design: "Wiring & Integration"). Launcher activity — sets the
 * Compose theme, the app title (top bar), and hosts the two-destination
 * NavHost (list <-> add <-> edit) backed by the [AppContainer] instance
 * owned by [LibrosLeidosApp]. Pure wiring glue (route strings, ViewModel
 * lookups, navigation callback forwarding) with no branching logic —
 * TDD-exempt scaffold, same rule as Phase 1; covered end to end by the
 * runtime-harness smoke test (task 7.3, [MainActivitySmokeTest]).
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as LibrosLeidosApp).container
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
                ) { contentPadding ->
                    Surface(modifier = Modifier.padding(contentPadding)) {
                        LibrosLeidosNavHost(appContainer = appContainer)
                    }
                }
            }
        }
    }
}

private const val ROUTE_LIST = "list"
private const val ROUTE_EDIT = "edit"
private const val ARG_BOOK_ID = "bookId"

// Sentinel for "no book id" (add mode) — NavType.LongType has no nullable
// variant, so the optional edit argument (design: "edit takes an optional
// bookId arg for edit-vs-add mode") is modeled as this out-of-range default.
private const val NO_BOOK_ID = -1L

private fun editRoute(bookId: Long?): String = "$ROUTE_EDIT?$ARG_BOOK_ID=${bookId ?: NO_BOOK_ID}"

@Composable
private fun LibrosLeidosNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            val viewModel: BookListViewModel = viewModel(factory = appContainer.bookListViewModelFactory())
            BookListRoute(
                viewModel = viewModel,
                onAddRequested = { navController.navigate(editRoute(null)) },
                onBookClicked = { id -> navController.navigate(editRoute(id)) },
            )
        }
        composable(
            route = "$ROUTE_EDIT?$ARG_BOOK_ID={$ARG_BOOK_ID}",
            arguments = listOf(
                navArgument(ARG_BOOK_ID) {
                    type = NavType.LongType
                    defaultValue = NO_BOOK_ID
                },
            ),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong(ARG_BOOK_ID) ?: NO_BOOK_ID
            val bookId = rawId.takeIf { it != NO_BOOK_ID }
            val viewModel: BookEditViewModel = viewModel(
                factory = appContainer.bookEditViewModelFactory(bookId),
            )
            BookEditRoute(
                viewModel = viewModel,
                onSaveCompleted = { navController.popBackStack() },
                onCancelRequested = { navController.popBackStack() },
            )
        }
    }
}

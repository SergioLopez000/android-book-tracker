package com.librosleidos.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import com.librosleidos.BuildConfig
import com.librosleidos.data.local.BookRepositoryImpl
import com.librosleidos.data.local.BooksDatabase
import com.librosleidos.data.remote.ChainedMetadataSource
import com.librosleidos.data.remote.GoogleBooksMetadataSource
import com.librosleidos.data.remote.HttpUrlConnectionClient
import com.librosleidos.data.remote.OpenLibraryMetadataSource
import com.librosleidos.domain.usecase.DeleteBookUseCase
import com.librosleidos.domain.usecase.GetBookByIdUseCase
import com.librosleidos.domain.usecase.GetBooksUseCase
import com.librosleidos.domain.usecase.SaveBookUseCase
import com.librosleidos.domain.usecase.SearchBookMetadataUseCase
import com.librosleidos.ui.edit.BookEditViewModel
import com.librosleidos.ui.list.BookListViewModel

/**
 * Task 7.1 (design D1: manual `AppContainer` on `Application` +
 * `viewModelFactory`; design D2/D3: `ChainedMetadataSource(OpenLibrary,
 * GoogleBooks)` gated by `BuildConfig.GOOGLE_BOOKS_API_KEY`).
 *
 * Assembles the full dependency graph — [BooksDatabase] (real Room, app
 * context) -> `BookDao` -> [BookRepositoryImpl]; [HttpUrlConnectionClient]
 * -> [OpenLibraryMetadataSource] + [GoogleBooksMetadataSource] ->
 * [ChainedMetadataSource]; all 5 use cases — and exposes two
 * `ViewModelProvider.Factory` instances that `MainActivity`'s NavHost uses
 * to construct [BookListViewModel] / [BookEditViewModel], matching the
 * manual-DI approach already used by Phase 5/6's Route files (`viewModel`
 * is constructor-injected there, not looked up).
 *
 * Pure wiring — every dependency is passed straight to a constructor with no
 * conditional branch of its own (the only gate, "is the Google Books key
 * blank", already lives and is tested inside
 * [GoogleBooksMetadataSource.search], Phase 4) — so this class is
 * TDD-exempt scaffold per the tasks doc's own rule, same as Phase 1. The
 * runtime-harness smoke test (task 7.3) proves the whole graph assembles
 * without crashing.
 */
class AppContainer(context: Context) {

    private val database: BooksDatabase = Room.databaseBuilder(
        context.applicationContext,
        BooksDatabase::class.java,
        "books.db",
    ).build()

    private val bookRepository = BookRepositoryImpl(database.bookDao())

    private val httpClient = HttpUrlConnectionClient()
    private val openLibrarySource = OpenLibraryMetadataSource(httpClient)
    private val googleBooksSource = GoogleBooksMetadataSource(
        httpClient = httpClient,
        apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY,
    )
    private val metadataSource = ChainedMetadataSource(
        primary = openLibrarySource,
        fallback = googleBooksSource,
    )

    private val getBooksUseCase = GetBooksUseCase(bookRepository)
    private val getBookByIdUseCase = GetBookByIdUseCase(bookRepository)
    private val saveBookUseCase = SaveBookUseCase(bookRepository)
    private val deleteBookUseCase = DeleteBookUseCase(bookRepository)
    private val searchBookMetadataUseCase = SearchBookMetadataUseCase(metadataSource)

    fun bookListViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        BookListViewModel(getBooksUseCase, deleteBookUseCase)
    }

    fun bookEditViewModelFactory(bookId: Long?): ViewModelProvider.Factory = viewModelFactory {
        BookEditViewModel(saveBookUseCase, searchBookMetadataUseCase, getBookByIdUseCase, bookId)
    }
}

/**
 * Small manual factory helper (design D1: manual DI, no reflection-based
 * `ViewModelProvider.NewInstanceFactory`).
 */
private fun <VM : ViewModel> viewModelFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            create() as T
    }

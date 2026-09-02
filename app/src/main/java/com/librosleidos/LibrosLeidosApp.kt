package com.librosleidos

import android.app.Application
import com.librosleidos.di.AppContainer

/**
 * Application entry point (task 7.1/7.2). Owns the single [AppContainer]
 * instance for the process lifetime; `MainActivity` reads it from here
 * rather than constructing its own, so the dependency graph (Room database
 * included) is assembled exactly once.
 */
class LibrosLeidosApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}

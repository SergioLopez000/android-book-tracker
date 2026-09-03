package com.librosleidos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Task 1.3 (design "app-theme" D12, spec "Apply Custom Dark Color Scheme").
 * App-wide dark Material3 theme applied at the composition root. Deliberately
 * parameterless (D12): no `darkTheme: Boolean`, no `isSystemInDarkTheme()`,
 * no dynamic color — light mode and dynamic color are out of scope, so those
 * branches would be dead, untestable code. Every screen composes under this
 * theme so none falls back to stock [MaterialTheme] defaults or a light
 * scheme.
 *
 * Scaffold — direct delegation to [MaterialTheme] with fixed [DarkColorScheme]
 * and [AppTypography], no branching logic. TDD-exempt, same rule as the rest
 * of Phase 1.
 */
@Composable
fun RinconDeLecturaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}

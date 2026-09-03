package com.librosleidos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val STAR_COUNT = 5
private val STAR_SIZE = 32.dp
private const val INNER_RADIUS_RATIO = 0.4f
private const val STAR_STROKE_WIDTH_DP = 2f

/**
 * Task 2.2/2.4/2.5 (design D10). Renders [rating] (0f..5f, half-star
 * precision) as a row of [STAR_COUNT] stars. Each star draws an outline
 * plus a filled overlay clipped to [starFillFraction] of its width — one
 * fraction per star covers every 0.5 step without `StarHalf` assets or RTL
 * quirks, and this same [Star] draw path is shared by both modes below
 * (task 2.5 dedupe).
 *
 * Display mode ([onRatingChange] null) merges the row into a single
 * content-description node carrying the formatted value; no numeric text
 * is rendered (spec: "Whole-star rating", "Half-star rating"). Input mode
 * splits each star into two half-width tap targets, one per 0.5 step, each
 * carrying its own `"Set rating N"` content description (spec: "User taps
 * to set half-star value", "Input stays within valid bounds") — every
 * resulting value is one of [STAR_COUNT] * 2 fixed 0.5-multiples between
 * `Rating.MIN` and `Rating.MAX` by construction.
 */
@Composable
fun StarRating(
    rating: Float?,
    modifier: Modifier = Modifier,
    onRatingChange: ((Float) -> Unit)? = null,
) {
    val clampedRating = (rating ?: 0f).coerceIn(0f, STAR_COUNT.toFloat())
    val rowModifier = if (onRatingChange == null) {
        modifier.clearAndSetSemantics {
            contentDescription = "Rating %.1f out of %d".format(clampedRating, STAR_COUNT)
        }
    } else {
        modifier
    }

    Row(modifier = rowModifier) {
        for (index in 0 until STAR_COUNT) {
            Box(modifier = Modifier.size(STAR_SIZE)) {
                Star(fraction = starFillFraction(clampedRating, index))
                if (onRatingChange != null) {
                    StarTapTargets(starIndex = index, onRatingChange = onRatingChange)
                }
            }
        }
    }
}

/**
 * Pure logic (design D10): how much of the star at [starIndex] (0-based)
 * is filled for a given [rating], clamped to 0f..1f.
 */
internal fun starFillFraction(rating: Float, starIndex: Int): Float =
    (rating - starIndex).coerceIn(0f, 1f)

/** Single shared draw path for one star: outline plus a [fraction]-clipped filled overlay. */
@Composable
private fun Star(fraction: Float) {
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val filledColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(STAR_SIZE)) {
        drawPath(starPath(size.width, size.height), color = outlineColor, style = Stroke(width = STAR_STROKE_WIDTH_DP.dp.toPx()))
    }
    Canvas(
        modifier = Modifier
            .size(STAR_SIZE)
            .drawWithContent {
                clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
            },
    ) {
        drawPath(starPath(size.width, size.height), color = filledColor)
    }
}

/** Two half-width tap targets for the star at [starIndex], one per 0.5 step. */
@Composable
private fun BoxScope.StarTapTargets(starIndex: Int, onRatingChange: (Float) -> Unit) {
    val halfValue = starIndex + 0.5f
    val fullValue = (starIndex + 1).toFloat()
    Row(modifier = Modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onRatingChange(halfValue) }
                .semantics { contentDescription = "Set rating %.1f".format(halfValue) },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onRatingChange(fullValue) }
                .semantics { contentDescription = "Set rating %.1f".format(fullValue) },
        )
    }
}

/** Five-point star outline path, centered in a [width]x[height] box. */
private fun starPath(width: Float, height: Float): Path {
    val centerX = width / 2f
    val centerY = height / 2f
    val outerRadius = minOf(centerX, centerY)
    val innerRadius = outerRadius * INNER_RADIUS_RATIO
    val path = Path()
    val pointCount = 5
    var angle = -Math.PI.toFloat() / 2f
    val angleStep = Math.PI.toFloat() / pointCount
    for (i in 0 until pointCount * 2) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val x = centerX + radius * cos(angle)
        val y = centerY + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        angle += angleStep
    }
    path.close()
    return path
}

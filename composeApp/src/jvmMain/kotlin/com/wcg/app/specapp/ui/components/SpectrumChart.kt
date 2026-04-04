package com.wcg.app.specapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wcg.app.specapp.data.model.SpectrumData
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val CHART_LINE_COLORS = listOf(
    Color(0xFF1565C0),  // blue – primary
    Color(0xFFD32F2F),  // red  – dark
    Color(0xFF2E7D32),  // green
    Color(0xFFF57F17),  // amber
    Color(0xFF6A1B9A),  // purple
)

/**
 * A Compose Canvas-based spectrum chart that supports:
 *  - Multiple overlaid spectra
 *  - Zoom (pinch or scroll) and pan on the X-axis
 *  - Cursor line with wavelength/intensity readout
 *  - Optional peak labels
 *  - Optional grid lines
 *
 * @param spectra       List of spectra to display (first = primary, rest = overlay)
 * @param darkSpectrum  Optional dark-background spectrum to subtract visually
 * @param showGrid      Whether to draw grid lines
 * @param showPeaks     Whether to annotate detected peaks
 * @param autoscaleY    Whether Y-axis is auto-scaled to visible data
 * @param modifier      Compose modifier
 */
@Composable
fun SpectrumChart(
    spectra: List<SpectrumData>,
    darkSpectrum: SpectrumData? = null,
    showGrid: Boolean = true,
    showPeaks: Boolean = true,
    autoscaleY: Boolean = true,
    modifier: Modifier = Modifier
) {
    val primary = spectra.firstOrNull()
    val initialXMin = primary?.minWavelength ?: 200f
    val initialXMax = primary?.maxWavelength ?: 1100f

    var xMin by remember(initialXMin) { mutableStateOf(initialXMin) }
    var xMax by remember(initialXMax) { mutableStateOf(initialXMax) }
    var cursorX by remember { mutableStateOf<Float?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF444444))
    val cursorStyle = TextStyle(fontSize = 11.sp, color = Color(0xFFFF6600))

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val range = xMax - xMin
                        val newRange = (range / zoom).coerceIn(10f, 2000f)
                        val center = (xMin + xMax) / 2f - pan.x / size.width * range
                        xMin = center - newRange / 2f
                        xMax = center + newRange / 2f
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val plotLeft = 60f
                        val plotRight = size.width - 20f
                        val fraction = (offset.x - plotLeft) / (plotRight - plotLeft)
                        cursorX = xMin + fraction * (xMax - xMin)
                    }
                }
        ) {
            val padLeft = 60f
            val padRight = 20f
            val padTop = 20f
            val padBottom = 40f
            val plotW = size.width - padLeft - padRight
            val plotH = size.height - padTop - padBottom

            if (plotW <= 0 || plotH <= 0) return@Canvas

            val visibleIntensities = spectra.flatMap { sp ->
                sp.wavelengths.zip(sp.intensities)
                    .filter { (wl, _) -> wl in xMin..xMax }
                    .map { (_, v) -> v }
            }
            val rawYMax = if (autoscaleY && visibleIntensities.isNotEmpty())
                visibleIntensities.max() * 1.1f
            else
                spectra.maxOfOrNull { it.maxIntensity }?.times(1.1f) ?: 65535f
            val yMin = 0f
            val yMax = rawYMax.coerceAtLeast(1f)

            fun wlToX(wl: Float) = padLeft + (wl - xMin) / (xMax - xMin) * plotW
            fun intToY(v: Float) = padTop + plotH - (v - yMin) / (yMax - yMin) * plotH

            // background
            drawRect(color = Color(0xFFFAFAFA), topLeft = Offset(padLeft, padTop), size = Size(plotW, plotH))

            // grid
            if (showGrid) {
                niceTicks(xMin, xMax, 8).forEach { wl ->
                    drawLine(Color(0xFFDDDDDD), Offset(wlToX(wl), padTop), Offset(wlToX(wl), padTop + plotH))
                }
                niceTicks(yMin, yMax, 6).forEach { v ->
                    val y = intToY(v)
                    if (y in padTop..(padTop + plotH))
                        drawLine(Color(0xFFDDDDDD), Offset(padLeft, y), Offset(padLeft + plotW, y))
                }
            }

            // border
            drawRect(
                color = Color(0xFF888888), topLeft = Offset(padLeft, padTop),
                size = Size(plotW, plotH), style = Stroke(width = 1f)
            )

            // draw each spectrum
            spectra.forEachIndexed { idx, sp ->
                if (sp.wavelengths.isEmpty()) return@forEachIndexed
                val color = CHART_LINE_COLORS[idx % CHART_LINE_COLORS.size]
                val path = Path()
                var first = true
                for (i in sp.wavelengths.indices) {
                    val wl = sp.wavelengths[i]
                    if (wl < xMin || wl > xMax) continue
                    val darkVal = darkSpectrum?.intensityAt(wl) ?: 0f
                    val displayInt = (sp.intensities[i] - darkVal).coerceAtLeast(0f)
                    val x = wlToX(wl)
                    val y = intToY(displayInt).coerceIn(padTop, padTop + plotH)
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 1.5f))

                // peak labels on primary spectrum
                if (showPeaks && idx == 0) {
                    sp.findPeaks(
                        minHeight = yMax * 0.1f,
                        minDistance = (sp.size / 20).coerceAtLeast(3)
                    ).filter { (wl, _) -> wl in xMin..xMax }.forEach { (wl, v) ->
                        val darkVal = darkSpectrum?.intensityAt(wl) ?: 0f
                        val displayV = (v - darkVal).coerceAtLeast(0f)
                        val x = wlToX(wl)
                        val y = intToY(displayV) - 6f
                        val text = "${wl.roundToInt()}nm"
                        val measured = textMeasurer.measure(text, labelStyle)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = text,
                            style = TextStyle(fontSize = 10.sp, color = Color(0xFF444444)),
                            topLeft = Offset(x - measured.size.width / 2f, y.coerceAtLeast(padTop + 2f))
                        )
                    }
                }
            }

            // cursor line
            cursorX?.let { cx ->
                val clampedWl = cx.coerceIn(xMin, xMax)
                val x = wlToX(clampedWl)
                drawLine(Color(0xAAFF6600), Offset(x, padTop), Offset(x, padTop + plotH), strokeWidth = 1.5f)
                val intensity = primary?.let { sp ->
                    val i = sp.wavelengths.indexOfFirst { it >= clampedWl }
                    if (i >= 0) sp.intensities[i] else 0f
                } ?: 0f
                val label = "${clampedWl.roundToInt()} nm  |  ${intensity.roundToInt()}"
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = cursorStyle,
                    topLeft = Offset((x + 4f).coerceAtMost(size.width - 120f), padTop + 4f)
                )
            }

            // X-axis ticks & labels
            niceTicks(xMin, xMax, 8).forEach { wl ->
                val x = wlToX(wl)
                drawLine(Color(0xFF444444), Offset(x, padTop + plotH), Offset(x, padTop + plotH + 5f))
                val text = "${wl.roundToInt()}"
                val measured = textMeasurer.measure(text, labelStyle)
                drawText(
                    textMeasurer, text, labelStyle,
                    topLeft = Offset(x - measured.size.width / 2f, padTop + plotH + 6f)
                )
            }
            val xAxisLabel = "波长 (nm)"
            val xAxisMeasured = textMeasurer.measure(xAxisLabel, labelStyle)
            drawText(
                textMeasurer, xAxisLabel, labelStyle,
                topLeft = Offset(padLeft + plotW / 2 - xAxisMeasured.size.width / 2f, size.height - 18f)
            )

            // Y-axis ticks & labels
            niceTicks(yMin, yMax, 6).forEach { v ->
                val y = intToY(v)
                if (y in padTop..(padTop + plotH)) {
                    drawLine(Color(0xFF444444), Offset(padLeft - 5f, y), Offset(padLeft, y))
                    val text = formatAxisValue(v)
                    val measured = textMeasurer.measure(text, labelStyle)
                    drawText(
                        textMeasurer, text, labelStyle,
                        topLeft = Offset(padLeft - measured.size.width - 7f, y - measured.size.height / 2f)
                    )
                }
            }
        }

        // legend for multiple spectra
        if (spectra.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 28.dp, top = 24.dp)
                    .background(Color(0xCCFFFFFF))
                    .padding(6.dp)
            ) {
                spectra.forEachIndexed { idx, sp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(16.dp, 3.dp)
                                .background(CHART_LINE_COLORS[idx % CHART_LINE_COLORS.size])
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            sp.name.take(20),
                            style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────── helpers ────────────────────────────────────────

private fun niceTicks(min: Float, max: Float, count: Int): List<Float> {
    if (min >= max) return emptyList()
    val range = max - min
    val rawStep = range / count
    val mag = 10.0.pow(floor(log10(rawStep.toDouble()))).toFloat()
    val step = when {
        rawStep / mag < 1.5f -> mag
        rawStep / mag < 3.5f -> 2f * mag
        rawStep / mag < 7.5f -> 5f * mag
        else -> 10f * mag
    }
    val start = ceil((min / step).toDouble()).toFloat() * step
    val result = mutableListOf<Float>()
    var tick = start
    while (tick <= max + step * 0.01f) {
        result.add(tick)
        tick += step
    }
    return result
}

private fun formatAxisValue(v: Float): String = when {
    v >= 10000 -> "${(v / 1000).roundToInt()}k"
    v >= 1000  -> "${"%.1f".format(v / 1000)}k"
    else       -> "${v.roundToInt()}"
}

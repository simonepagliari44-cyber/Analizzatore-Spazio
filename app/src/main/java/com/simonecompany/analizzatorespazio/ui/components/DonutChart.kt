package com.simonecompany.analizzatorespazio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun DonutChart(
    percentages: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    highlightedIndex: Int = -1,
    normalThicknessRatio: Float = 0.14f,
    highlightedThicknessRatio: Float = 0.20f,
    centerContent: @Composable (() -> Unit)? = null
) {
    var entered by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(percentages) {
        entered = 1f
    }

    val progress by animateFloatAsState(
        targetValue = entered,
        animationSpec = tween(durationMillis = 900),
        label = "donutDrawProgress"
    )

    val highlightAnim by animateFloatAsState(
        targetValue = if (highlightedIndex >= 0) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "donutHighlight"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = minOf(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)

            val normalStroke = canvasSize * normalThicknessRatio
            val highlightedStroke = canvasSize * highlightedThicknessRatio
            val stroke = normalStroke + (highlightedStroke - normalStroke) * highlightAnim

            val outerRadius = canvasSize / 2f - stroke / 2f - 1.dp.toPx()
            val arcTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
            val arcSize = Size(outerRadius * 2f, outerRadius * 2f)

            var startAngle = -90f

            percentages.forEachIndexed { index, percentage ->
                val isHighlighted = index == highlightedIndex
                val sweep = percentage * 3.6f * progress

                if (sweep > 0.01f) {
                    if (isHighlighted) {
                        val glowAlpha = 0.12f + 0.22f * highlightAnim
                        drawArc(
                            color = colors.getOrElse(index) { Color.Gray }.copy(alpha = glowAlpha),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(
                                width = stroke + highlightAnim * canvasSize * 0.05f,
                                cap = StrokeCap.Butt
                            )
                        )
                    }
                    drawArc(
                        color = colors.getOrElse(index) { Color.Gray },
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                }

                startAngle += sweep
            }
        }

        centerContent?.invoke()
    }
}
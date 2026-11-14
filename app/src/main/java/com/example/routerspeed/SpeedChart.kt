package com.example.routerspeed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val UploadSpeedColor = Color(0xFF159957)
val DownloadSpeedColor = Color(0xFF2878D0)

@Composable
fun SpeedChart(points: List<SpeedPoint>, modifier: Modifier = Modifier) {
    val latest = points.lastOrNull()
    val elapsed = remember(points) {
        if (points.size >= 2) points.last().timestamp - points.first().timestamp else 0L
    }
    val peak = remember(points) {
        points.maxOfOrNull { maxOf(it.upRate, it.downRate) }?.coerceAtLeast(1L) ?: 1L
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近 10 分钟速度",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    points.size < 2 -> "等待采样"
                    elapsed < SPEED_HISTORY_WINDOW_MILLIS -> "已记录 ${formatDuration(elapsed)}"
                    else -> "10:00"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            SpeedLegend(
                label = "上传",
                rate = latest?.upRate ?: 0,
                color = UploadSpeedColor,
                modifier = Modifier.weight(1f),
            )
            SpeedLegend(
                label = "下载",
                rate = latest?.downRate ?: 0,
                color = DownloadSpeedColor,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val horizontalCount = 3
            for (index in 0..horizontalCount) {
                val y = size.height * index / horizontalCount
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            for (index in 0..4) {
                val x = size.width * index / 4
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            if (points.isEmpty()) return@Canvas

            val yScale = peak.toFloat() * 1.1f
            val startTime = points.first().timestamp
            val timeSpan = points.last().timestamp - startTime
            val upPath = Path()
            val downPath = Path()

            points.forEachIndexed { index, point ->
                val x = when {
                    points.size == 1 -> 0f
                    timeSpan > 0 -> (point.timestamp - startTime).toFloat() / timeSpan * size.width
                    else -> index.toFloat() / (points.size - 1) * size.width
                }
                val upY = size.height - point.upRate.coerceAtLeast(0) / yScale * size.height
                val downY = size.height - point.downRate.coerceAtLeast(0) / yScale * size.height
                if (index == 0) {
                    upPath.moveTo(x, upY)
                    downPath.moveTo(x, downY)
                } else {
                    upPath.lineTo(x, upY)
                    downPath.lineTo(x, downY)
                }
            }
            if (points.size == 1) {
                val onlyPoint = points.first()
                upPath.lineTo(size.width, size.height - onlyPoint.upRate / yScale * size.height)
                downPath.lineTo(size.width, size.height - onlyPoint.downRate / yScale * size.height)
            }

            val stroke = Stroke(
                width = 2.25.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            drawPath(upPath, UploadSpeedColor, style = stroke)
            drawPath(downPath, DownloadSpeedColor, style = stroke)
        }
        Spacer(Modifier.height(2.dp))
        Row {
            Text(
                text = if (elapsed < SPEED_HISTORY_WINDOW_MILLIS) "开始" else "10 分钟前",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "峰值 ${formatRate(peak)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "现在",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpeedLegend(
    label: String,
    rate: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 14.dp, height = 3.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = formatRate(rate),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

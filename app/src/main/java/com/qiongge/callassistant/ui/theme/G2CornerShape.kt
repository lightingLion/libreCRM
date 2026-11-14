package com.qiongge.callassistant.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.min

/** A superellipse-like corner with zero curvature where it meets each straight edge. */
class G2CornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(radius: Dp) : this(
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
        CornerSize(radius),
    )

    constructor(topStart: Dp, topEnd: Dp, bottomEnd: Dp, bottomStart: Dp) : this(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart),
    )

    @Suppress("UNUSED_PARAMETER")
    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val width = size.width.coerceAtLeast(0f)
        val height = size.height.coerceAtLeast(0f)
        val maxRadius = min(width, height) / 2f
        val (topLeft, topRight) = if (layoutDirection == LayoutDirection.Ltr) {
            topStart to topEnd
        } else {
            topEnd to topStart
        }
        val (bottomLeft, bottomRight) = if (layoutDirection == LayoutDirection.Ltr) {
            bottomStart to bottomEnd
        } else {
            bottomEnd to bottomStart
        }
        val tlRadius = topLeft.coerceIn(0f, maxRadius)
        val trRadius = topRight.coerceIn(0f, maxRadius)
        val brRadius = bottomRight.coerceIn(0f, maxRadius)
        val blRadius = bottomLeft.coerceIn(0f, maxRadius)
        val scale = minOf(
            1f,
            edgeScale(width, tlRadius + trRadius),
            edgeScale(width, blRadius + brRadius),
            edgeScale(height, tlRadius + blRadius),
            edgeScale(height, trRadius + brRadius),
        )
        val tl = tlRadius * scale
        val tr = trRadius * scale
        val br = brRadius * scale
        val bl = blRadius * scale

        val path = Path().apply {
            moveTo(tl, 0f)
            lineTo(width - tr, 0f)
            appendG2Corner(Offset(width - tr, 0f), tr, CornerOrientation.TopRight)
            lineTo(width, height - br)
            appendG2Corner(Offset(width, height - br), br, CornerOrientation.BottomRight)
            lineTo(bl, height)
            appendG2Corner(Offset(bl, height), bl, CornerOrientation.BottomLeft)
            lineTo(0f, tl)
            appendG2Corner(Offset(0f, tl), tl, CornerOrientation.TopLeft)
            close()
        }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): G2CornerShape = G2CornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean =
        other is G2CornerShape &&
            topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart

    override fun hashCode(): Int =
        (((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31) +
            bottomStart.hashCode()

    override fun toString(): String =
        "G2CornerShape(topStart=$topStart, topEnd=$topEnd, " +
            "bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}

private enum class CornerOrientation {
    TopRight,
    BottomRight,
    BottomLeft,
    TopLeft,
}

private fun edgeScale(available: Float, sum: Float): Float =
    if (sum == 0f) 1f else (available / sum).coerceIn(0f, 1f)

private fun Path.appendG2Corner(origin: Offset, radius: Float, orientation: CornerOrientation) {
    if (radius <= 0f) return

    val segments = 8
    fun point(t: Float): Offset {
        val x = radius * g2Profile(t)
        val y = radius * (1f - g2Profile(1f - t))
        return origin + orient(x, y, orientation)
    }
    fun tangent(t: Float): Offset {
        val x = radius * g2ProfileDerivative(t)
        val y = radius * g2ProfileDerivative(1f - t)
        return orient(x, y, orientation)
    }

    for (segment in 0 until segments) {
        val t0 = segment / segments.toFloat()
        val t1 = (segment + 1) / segments.toFloat()
        val interval = t1 - t0
        val start = point(t0)
        val end = point(t1)
        var control1 = start + tangent(t0) * (interval / 3f)
        var control2 = end - tangent(t1) * (interval / 3f)

        // Preserve zero curvature at the edge joins after cubic approximation.
        if (segment == 0) {
            control2 = if (abs(tangent(t0).x) >= abs(tangent(t0).y)) {
                Offset(control2.x, start.y)
            } else {
                Offset(start.x, control2.y)
            }
        }
        if (segment == segments - 1) {
            control1 = if (abs(tangent(t1).x) >= abs(tangent(t1).y)) {
                Offset(control1.x, end.y)
            } else {
                Offset(end.x, control1.y)
            }
        }

        cubicTo(
            control1.x,
            control1.y,
            control2.x,
            control2.y,
            end.x,
            end.y,
        )
    }
}

private fun orient(x: Float, y: Float, orientation: CornerOrientation): Offset = when (orientation) {
    CornerOrientation.TopRight -> Offset(x, y)
    CornerOrientation.BottomRight -> Offset(-y, x)
    CornerOrientation.BottomLeft -> Offset(-x, -y)
    CornerOrientation.TopLeft -> Offset(y, -x)
}

private fun g2Profile(t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    val t4 = t3 * t
    val t5 = t4 * t
    return 1.5f * t + t3 - 3f * t4 + 1.5f * t5
}

private fun g2ProfileDerivative(t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    val t4 = t3 * t
    return 1.5f + 3f * t2 - 12f * t3 + 7.5f * t4
}

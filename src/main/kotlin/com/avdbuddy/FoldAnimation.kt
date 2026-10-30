package com.avdbuddy

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One side of a foldable: its frame artwork and the screen showing through it. */
internal class FoldFace(val frame: ProfileFrame, val screenSize: IntSize, val artwork: ImageBitmap, val screen: FoldPicture?)

/** A streamed picture and the quarter turns it is rotated by, as the live screen draws it. */
internal data class FoldPicture(val image: ImageBitmap, val rotation: Int)

/**
 * A foldable closing like a book. [progress] runs from 0 (open) to 1 (closed). The left half turns
 * about the hinge onto the right half, showing the [cover] on its back, then settles into the closed
 * device's place in the middle while the cover screen comes on.
 */
@Composable
internal fun FoldScene(progress: Float, inner: FoldFace, cover: FoldFace, tint: ColorFilter, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val open = inner.frame
        val closed = cover.frame
        val openScale = min(size.width / open.width, size.height / open.height)
        val openSize = Size(open.width * openScale, open.height * openScale)
        val closedScale = min(size.width / closed.width, size.height / closed.height)
        val closedSize = Size(closed.width * closedScale, closed.height * closedScale)
        val turn = ease((progress / TurnShare).coerceIn(0f, 1f))
        val settle = ease(((progress - TurnShare) / (1f - TurnShare)).coerceIn(0f, 1f))
        // Slide while turning, so the folded half ends up in the middle.
        val half = openSize.width / 2
        val top = (size.height - openSize.height) / 2
        val hinge = size.width / 2 - half / 2 * turn
        val angle = turn * PI.toFloat()
        val panel = Size(half, openSize.height)
        val thickness = half * Thickness
        // The cover's frame artwork has clear space around the phone, so fade the half beneath out
        // as the cover lands on it, or its dark edge peeks out behind the cover's silver one.
        val beneath = ((PI.toFloat() - angle) / (PI.toFloat() / 4)).coerceIn(0f, 1f)
        if (beneath > 0f) layer(beneath) {
            drawFace(inner, from = open.width / 2f, to = open.width.toFloat(), Offset(hinge, top), panel, tint,
                // Tuck under the turning half so no seam shows along the hinge.
                bleed = 2f)
            // The turning half's shadow, reaching further as it comes over.
            val reach = half * (0.15f + 0.85f * (1 - cos(angle)) / 2)
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f * sin(angle)), Color.Transparent), hinge, hinge + reach),
                Offset(hinge, top), Size(reach, panel.height), blendMode = BlendMode.SrcAtop)
        }
        if (angle < PI / 2) {
            // Stack the body's outline behind the screen, back to front, to give the half its thickness.
            for (step in Layers downTo 1) drawTurning(Offset(hinge, top), panel, angle, depth = thickness * step / Layers) {
                drawFace(inner, from = 0f, to = open.width / 2f, Offset.Zero, panel, tint, solid = sideColor(step, angle))
            }
            drawTurning(Offset(hinge, top), panel, angle) {
                shaded(0.45f * (1 - cos(angle))) {
                    drawFace(inner, from = 0f, to = open.width / 2f, Offset.Zero, panel, tint)
                }
            }
        } else if (settle == 0f) {
            // The back sits a phone's thickness behind the screen, with the body stacked in front of it.
            for (step in 0 until Layers) drawTurning(Offset(hinge, top), panel, angle, depth = thickness * step / Layers) {
                withTransform({ scale(-1f, 1f, Offset(half / 2, 0f)) }) {
                    drawFace(cover, 0f, closed.width.toFloat(), Offset.Zero, panel, tint, solid = sideColor(Layers - step, angle))
                }
            }
            drawTurning(Offset(hinge, top), panel, angle, depth = thickness) {
                shaded(0.45f * (1 + cos(angle))) {
                    // The back of the turning half, seen through the hinge.
                    withTransform({ scale(-1f, 1f, Offset(half / 2, 0f)) }) {
                        drawFace(cover, 0f, closed.width.toFloat(), Offset.Zero, panel, tint, screenAlpha = 0f)
                    }
                }
            }
        } else {
            val from = Rect(Offset(hinge, top), Size(half, openSize.height))
            val to = Rect(Offset((size.width - closedSize.width) / 2, (size.height - closedSize.height) / 2), closedSize)
            drawFace(cover, 0f, closed.width.toFloat(), lerp(from.topLeft, to.topLeft, settle),
                Size(lerp(from.width, to.width, settle), lerp(from.height, to.height, settle)), tint, screenAlpha = settle)
        }
    }
}

private const val TurnShare = 0.75f

/** How thick each half is, as a share of its width. */
private const val Thickness = 0.05f

/** How many copies of the body's outline make up its side. */
private const val Layers = 8

/** The side's metal, darker towards the middle of the body and as it turns away from the light. */
private fun sideColor(step: Int, angle: Float): Color {
    val light = (0.6f + 0.4f * sin(angle)) * (1f - 0.25f * step / Layers)
    return Color(0.69f * light, 0.71f * light, 0.77f * light)
}

private fun DrawScope.layer(alpha: Float = 1f, content: DrawScope.() -> Unit) = drawIntoCanvas { canvas ->
    canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    content()
    canvas.restore()
}

/** Darkens what [content] paints, and only that, as a face turns away from the light. */
private fun DrawScope.shaded(darken: Float, content: DrawScope.() -> Unit) = layer {
    content()
    drawRect(Color.Black.copy(alpha = darken.coerceIn(0f, 1f)), blendMode = BlendMode.SrcAtop)
}

private fun ease(t: Float) = (1 - cos(t * PI.toFloat())) / 2

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

/** Where the camera sits: far enough that the outer edge only grows a few percent as it turns towards the viewer. */
private fun DrawScope.cameraDistance() = 6f * maxOf(size.width, size.height)

/**
 * Draws a panel hinged on its right edge at [hinge], turned [angle] radians towards the viewer, in
 * perspective, [depth] behind its front face. Content is drawn in the panel's own flat coordinates.
 */
private fun DrawScope.drawTurning(hinge: Offset, panel: Size, angle: Float, depth: Float = 0f, content: DrawScope.() -> Unit) {
    val w = panel.width
    val d = cameraDistance()
    val cx = size.width / 2
    val cy = size.height / 2
    val s = sin(angle)
    val c = cos(angle)
    // Stepping back by depth along the face's normal (sin, cos) moves the hinge line.
    val hx = hinge.x - depth * s
    val z0 = -depth * c
    // A point u along the panel (0 = outer edge, w = hinge) sits (w - u) from the hinge, at depth
    // z0 + (w - u)·sin towards the viewer. Projecting through a camera d away is a homography in (u, v).
    val matrix = org.jetbrains.skia.Matrix33(
        (cx * s + d * c) / d, 0f, (cx * (d - z0 - w * s) + d * (hx - cx - w * c)) / d,
        cy * s / d, 1f, (cy * (d - z0 - w * s) + d * (hinge.y - cy)) / d,
        s / d, 0f, (d - z0 - w * s) / d,
    )
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.nativeCanvas.concat(matrix)
        content()
        canvas.restore()
    }
}

/** Draws the frame-pixel columns [from]..[to] of [face] into [size] at [topLeft]. */
private fun DrawScope.drawFace(face: FoldFace, from: Float, to: Float, topLeft: Offset, size: Size, tint: ColorFilter, screenAlpha: Float = 1f, bleed: Float = 0f, solid: Color? = null) {
    val frame = face.frame
    val sx = size.width / (to - from)
    val sy = size.height / frame.height
    clipRect(topLeft.x - bleed, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        translate(topLeft.x - from * sx, topLeft.y) {
            withTransform({ scale(sx, sy, Offset.Zero) }) {
                val screenSize = Size(face.screenSize.width.toFloat(), face.screenSize.height.toFloat())
                val screen = RoundRect(frame.screenX.toFloat(), frame.screenY.toFloat(),
                    frame.screenX + screenSize.width, frame.screenY + screenSize.height,
                    CornerRadius(frame.cornerRadius.toFloat()))
                if (solid != null) {
                    // Just the body's outline, filled.
                    drawPath(Path().apply { addRoundRect(screen) }, solid)
                    drawImage(face.artwork, IntOffset.Zero, IntSize(face.artwork.width, face.artwork.height),
                        IntOffset.Zero, IntSize(frame.width, frame.height), colorFilter = ColorFilter.tint(solid, BlendMode.SrcIn))
                    return@withTransform
                }
                clipPath(Path().apply { addRoundRect(screen) }) {
                    // Black only where the picture doesn't fully cover, or its antialiased edges darken the hinge.
                    if (face.screen == null || screenAlpha < 1f) drawRect(Color.Black, Offset(screen.left, screen.top), screenSize)
                    face.screen?.let { (image, rotation) ->
                        // Undo the stream's orientation, like the live screen does.
                        val upright = rotation % 2 == 0
                        val middle = Offset(screen.left + screenSize.width / 2, screen.top + screenSize.height / 2)
                        val drawn = IntSize((if (upright) screenSize.width else screenSize.height).toInt(),
                            (if (upright) screenSize.height else screenSize.width).toInt())
                        rotate(rotation * 90f, middle) {
                            drawImage(image, IntOffset.Zero, IntSize(image.width, image.height),
                                IntOffset((middle.x - drawn.width / 2f).toInt(), (middle.y - drawn.height / 2f).toInt()),
                                drawn, alpha = screenAlpha)
                        }
                    }
                }
                drawImage(face.artwork, IntOffset.Zero, IntSize(face.artwork.width, face.artwork.height),
                    IntOffset.Zero, IntSize(frame.width, frame.height), colorFilter = tint)
            }
        }
    }
}

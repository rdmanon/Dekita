package tech.vauldex.dekita.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.max
import tech.vauldex.dekita.domain.Store
import tech.vauldex.dekita.domain.StoreAvailability

/**
 * The store's photo slot. The mockup loads no remote images, so each store gets a flat illustration,
 * drawn like the design's: a 100 x 100 picture that covers the slot and is cropped at the edges.
 * Paused stores are dimmed. The picture is decoration, so it is hidden from screen readers.
 */
@Composable
fun StoreArt(
    store: Store,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Canvas(
        modifier
            .clip(shape)
            .alpha(if (store.availability == StoreAvailability.PAUSED) 0.55f else 1f)
            .clearAndSetSemantics {},
    ) {
        // Cover: scale the 100 x 100 picture to the longer side and centre it.
        val scale = max(size.width, size.height) / 100f
        withTransform({
            translate((size.width - 100f * scale) / 2f, (size.height - 100f * scale) / 2f)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            when (store.artKey) {
                "ramen" -> drawRamen()
                "convenience" -> drawMart()
                "curry" -> drawCurry()
                "soba" -> drawSoba()
                "massage" -> drawMassage()
                "bento" -> drawBento()
                "udon" -> drawUdon()
                "teishoku" -> drawTeishoku()
                "hair" -> drawHairSalon()
                else -> rect(0f, 0f, 100f, 100f, 0f, Color(0xFFE8C9A1))
            }
        }
    }
}

private fun svg(pathData: String): Path = PathParser().parsePathString(pathData).toPath()

private fun DrawScope.background(color: Color) = drawRect(color, Offset.Zero, Size(100f, 100f))

private fun DrawScope.circle(cx: Float, cy: Float, r: Float, color: Color) = drawCircle(color, r, Offset(cx, cy))

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, color: Color, width: Float) =
    drawCircle(color, r, Offset(cx, cy), style = Stroke(width))

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun DrawScope.rect(x: Float, y: Float, w: Float, h: Float, rx: Float, color: Color) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(rx))

private fun DrawScope.line(path: Path, color: Color, width: Float) =
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))

// Paths are parsed once, on first draw.

private object RamenPaths {
    val noodlesTop = svg("M24,56 c5,-4 9,4 14,0 s9,4 14,0 s9,4 14,0 s7,3 10,1")
    val noodlesBottom = svg("M27,64 c5,-4 9,4 14,0 s9,4 14,0 s9,4 14,0")
}

private fun DrawScope.drawRamen() {
    background(Color(0xFFE8C9A1))
    circle(50f, 50f, 40f, Color(0xFF27211D))
    circle(50f, 50f, 34f, Color(0xFFC98A3E))
    line(RamenPaths.noodlesTop, Color(0xFFF6DB8F), 3f)
    line(RamenPaths.noodlesBottom, Color(0xFFF6DB8F), 3f)
    circle(38f, 38f, 9f, Color(0xFFE9B8A4))
    ring(38f, 38f, 9f, Color(0xFFB66F55), 2f)
    oval(62f, 38f, 9f, 7.5f, Color(0xFFFFF6E3))
    circle(62f, 38f, 4.2f, Color(0xFFF2A900))
    rotate(12f, pivot = Offset(66f, 62f)) { rect(60f, 54f, 12f, 16f, 1.5f, Color(0xFF1F3B2D)) }
    circle(44f, 74f, 2.2f, Color(0xFF7CB342))
    circle(50f, 77f, 2.2f, Color(0xFF7CB342))
    circle(39f, 69f, 2.2f, Color(0xFF8BC34A))
}

private object MartPaths {
    val basket = svg("M22,46 h56 l-7,30 H29 z")
    val weave = svg("M30,56 h40 M32,66 h36")
}

private fun DrawScope.drawMart() {
    background(Color(0xFFDCE8F5))
    rect(30f, 34f, 12f, 14f, 2f, Color(0xFFF2B544))
    circle(56f, 40f, 7f, Color(0xFFE35D5B))
    rect(46f, 30f, 6f, 18f, 2f, Color(0xFF3FA672))
    drawPath(MartPaths.basket, Color(0xFF2F6FB2))
    line(MartPaths.weave, Color(0xFF5B92CC), 2.5f)
}

private object CurryPaths {
    val rice = svg("M50,22 a28,28 0 0,0 0,56 z")
    val roux = svg("M50,22 a28,28 0 0,1 0,56 z")
}

private fun DrawScope.drawCurry() {
    background(Color(0xFFF3D9A4))
    circle(50f, 50f, 38f, Color(0xFFEEE9E0))
    ring(50f, 50f, 38f, Color(0xFFDED7CA), 2f)
    drawPath(CurryPaths.rice, Color.White)
    drawPath(CurryPaths.roux, Color(0xFF9A5B13))
    rect(56f, 38f, 8f, 8f, 2f, Color(0xFFE8892A))
    rect(60f, 55f, 9f, 8f, 2f, Color(0xFFF4D58A))
    circle(66f, 46f, 3f, Color(0xFF6B3A0B))
    circle(40f, 58f, 2.2f, Color(0xFFC2410C))
}

private object SobaPaths {
    val noodles = svg(
        "M24,32 c8,4 16,-4 24,0 s12,4 16,2 M24,42 c8,4 16,-4 24,0 s12,4 16,2 " +
            "M24,52 c8,4 16,-4 24,0 s12,4 16,2 M24,62 c8,4 16,-4 24,0 s12,4 16,2",
    )
}

private fun DrawScope.drawSoba() {
    background(Color(0xFFDCE6D8))
    rect(14f, 18f, 60f, 60f, 6f, Color(0xFFB98B53))
    rect(19f, 23f, 50f, 50f, 3f, Color(0xFFD8B27A))
    line(SobaPaths.noodles, Color(0xFF7E6A55), 2.5f)
    circle(82f, 74f, 12f, Color(0xFF2D2A26))
}

private object MassagePaths {
    val leaf = svg("M50,43 c-2,-11 4,-19 15,-21 c-1,11 -6,18 -15,21 z")
}

private fun DrawScope.drawMassage() {
    background(Color(0xFFE4ECE3))
    oval(50f, 72f, 24f, 8f, Color(0xFF7D8C7A))
    oval(50f, 60f, 19f, 7f, Color(0xFF98A795))
    oval(50f, 49f, 13f, 5.5f, Color(0xFFB3C0B0))
    drawPath(MassagePaths.leaf, Color(0xFF5E8B5A))
}

private fun DrawScope.drawBento() {
    background(Color(0xFFEFE3CF))
    rect(16f, 24f, 68f, 52f, 6f, Color(0xFF2D2A26))
    rect(20f, 28f, 30f, 44f, 3f, Color.White)
    circle(35f, 50f, 3.5f, Color(0xFFC2185B))
    rect(54f, 28f, 26f, 20f, 3f, Color(0xFFC0662B))
    rect(54f, 52f, 26f, 20f, 3f, Color(0xFF7CB342))
}

private object UdonPaths {
    val noodles = svg("M28,44 c8,-4 14,4 22,0 s14,4 22,0 M26,54 c8,-4 14,4 22,0 s14,4 24,0 M30,64 c8,-4 14,4 20,0 s12,4 18,0")
}

private fun DrawScope.drawUdon() {
    background(Color(0xFFF1E6D0))
    circle(50f, 50f, 38f, Color(0xFF3E2A1E))
    circle(50f, 50f, 32f, Color(0xFFE9C98B))
    line(UdonPaths.noodles, Color(0xFFFFF7E0), 3.5f)
    circle(42f, 36f, 2.4f, Color(0xFF6AA84F))
    circle(58f, 34f, 2.4f, Color(0xFF6AA84F))
    oval(64f, 66f, 7f, 5f, Color(0xFFF8BBD0))
}

private fun DrawScope.drawTeishoku() {
    background(Color(0xFFE9DFD3))
    rect(8f, 20f, 84f, 60f, 6f, Color(0xFF8D6E63))
    circle(30f, 60f, 12f, Color.White)
    circle(70f, 60f, 12f, Color(0xFFA0522D))
    circle(70f, 60f, 9f, Color(0xFFC68642))
    oval(48f, 36f, 20f, 7f, Color(0xFFB0BEC5))
    circle(72f, 34f, 4f, Color(0xFFF2C744))
}

private object HairSalonPaths {
    val blades = svg("M41,60 L64,24 M59,60 L36,24")
}

private fun DrawScope.drawHairSalon() {
    background(Color(0xFFEDE3F3))
    line(HairSalonPaths.blades, Color(0xFF3B3B3B), 4f)
    ring(38f, 68f, 8f, Color(0xFF5B1F8C), 4f)
    ring(62f, 68f, 8f, Color(0xFF5B1F8C), 4f)
}

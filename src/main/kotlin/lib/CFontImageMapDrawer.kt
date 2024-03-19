package lib

import org.openrndr.draw.*
import org.openrndr.extra.shapes.rectify.RectifiedContour
import org.openrndr.internal.Driver
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.buildTransform
import kotlin.math.round


fun Vector2.transform(m : Matrix44) : Vector2 {
    return (m * this.xy01).xy
}

fun Drawer.textOnAPath(text: String, path: RectifiedContour, fim: CFontImageMapDrawer, offsetX: Double = 0.0, offsetY: Double = 0.0, tracking: Double = 0.0, scale: Double = 1.0) {
    fim.drawTextOnPath(path, context, drawStyle, text, offsetX, offsetY, tracking, scale)
}

class GlyphRectangle(val character: Char, val x: Double, val y: Double, val width: Double, val height: Double)

class CFontImageMapDrawer {

    var lastPos = Vector2.ZERO

    private val shaderManager: ShadeStyleManager = ShadeStyleManager.fromGenerators("font-image-map",
            vsGenerator = Driver.instance.shaderGenerators::fontImageMapVertexShader,
            fsGenerator = Driver.instance.shaderGenerators::fontImageMapFragmentShader)

    private val maxQuads = 20000

    private val vertices = VertexBuffer.createDynamic(VertexFormat().apply {
        textureCoordinate(2)
        attribute("bounds", VertexElementType.VECTOR4_FLOAT32)
        position(3)
        attribute("instance", VertexElementType.FLOAT32)
    }, 6 * maxQuads)

    private var quadCount = 0

    fun drawText(
        context: DrawContext,
        drawStyle: DrawStyle,
        text: String,
        x: Double,
        y: Double
    )= drawTexts(context, drawStyle, listOf(text), listOf(Vector2(x, y)))

    fun drawTextOnPath (
        contour: RectifiedContour,
        context: DrawContext,
        drawStyle: DrawStyle,
        text: String,
        offsetX: Double = 0.0,
        offsetY: Double = 0.0,
        tracking: Double = 0.0,
        scale: Double = 1.0
    ) = drawTextsOnPaths(contour, context, drawStyle, listOf(text), listOf(Vector2(offsetX, offsetY)), tracking, scale)

    fun drawTexts(
        context: DrawContext,
        drawStyle: DrawStyle,
        texts: List<String>,
        positions: List<Vector2>
    ):List<List<GlyphRectangle>> {
        val fontMap = drawStyle.fontMap as? FontImageMap



        if (fontMap!= null) {

            var instance = 0

            val textAndPositionPairs = texts.zip(positions)
            for ((text, position) in textAndPositionPairs) {
                var cursorX = 0.0
                val cursorY = 0.0

                val bw = vertices.shadow.writer()
                bw.position = vertices.vertexFormat.size * quadCount * 6

                var lastChar:Char? = null
                text.forEach {
                    val lc = lastChar
                    if (drawStyle.kerning == KernMode.METRIC) {
                       cursorX += if (lc != null) fontMap.kerning(lc, it) else 0.0
                    }
                    val metrics = fontMap.glyphMetrics[it] ?: fontMap.glyphMetrics.getValue(' ')
                    val (dx, gr) = insertCharacterQuad(
                        fontMap,
                        bw,
                        it,
                        position.x + cursorX,
                        position.y + cursorY ,
                        instance,
                        drawStyle.textSetting
                    )
                    cursorX += metrics.advanceWidth + dx
                    lastChar = it
                }
                instance++
            }
            flush(context, drawStyle)
        }
        return emptyList()
    }

    fun drawTextsOnPaths(
        contour: RectifiedContour,
        context: DrawContext,
        drawStyle: DrawStyle,
        texts: List<String>,
        positions: List<Vector2>,
        tracking: Double = 0.0,
        scale: Double = 1.0
    ):List<List<GlyphRectangle>> {
        val fontMap = drawStyle.fontMap as? FontImageMap



        if (fontMap!= null) {

            var instance = 0

            val textAndPositionPairs = texts.zip(positions)
            for ((text, position) in textAndPositionPairs) {
                var cursorX = 0.0
                val cursorY = 0.0

                val bw = vertices.shadow.writer()
                bw.position = vertices.vertexFormat.size * quadCount * 6

                var lastChar:Char? = null
                text.forEach {
                    val lc = lastChar
                    if (drawStyle.kerning == KernMode.METRIC) {
                        cursorX += if (lc != null) fontMap.kerning(lc, it) else 0.0
                    }
                    val metrics = fontMap.glyphMetrics[it] ?: fontMap.glyphMetrics.getValue(' ')
                    val (dx, gr) = insertCharacterQuadOnPath(
                        contour,
                        fontMap,
                        bw,
                        it,
                        position.x + cursorX,
                        position.y + cursorY,
                        instance,
                        drawStyle.textSetting,
                        scale
                    )
                    cursorX += metrics.advanceWidth + dx + tracking
                    lastChar = it
                }
                instance++
            }
            flush(context, drawStyle)
        }
        return emptyList()
    }

    var queuedInstances = 0
    fun queueText(
        fontMap: FontMap,
        text: String,
        x: Double,
        y: Double,
        tracking: Double = 0.0,
        kerning: KernMode, // = KernMode.METRIC,
        textSetting: TextSettingMode,// = TextSettingMode.PIXEL,
    ) {
        val bw = vertices.shadow.writer()
        bw.position = vertices.vertexFormat.size * quadCount * 6
        fontMap as FontImageMap
        var cursorX = 0.0
        var cursorY = 0.0
        var lastChar:Char? = null
        text.forEach {
            val lc = lastChar
            val metrics = fontMap.glyphMetrics[it]
            metrics?.let { m ->
                if (kerning == KernMode.METRIC) {
                    cursorX += if (lc != null) fontMap.kerning(lc, it) else 0.0
                }
                val (dx,rect) = insertCharacterQuad(
                    fontMap,
                    bw,
                    it,
                    x + cursorX,
                    y + cursorY + metrics.yBitmapShift / fontMap.contentScale,
                    0,
                    textSetting
                )
                cursorX += m.advanceWidth + tracking +dx
                lastChar = it
            }
        }
        queuedInstances++
    }


    fun flush(context: DrawContext, drawStyle: DrawStyle) {
        if (quadCount > 0) {
            vertices.shadow.uploadElements(0, quadCount * 6)
            val shader = shaderManager.shader(drawStyle.shadeStyle, vertices.vertexFormat)
            shader.begin()
            context.applyToShader(shader)

            Driver.instance.setState(drawStyle)
            drawStyle.applyToShader(shader)
            (drawStyle.fontMap as FontImageMap).texture.bind(0)
            Driver.instance.drawVertexBuffer(shader, listOf(vertices), DrawPrimitive.TRIANGLES, 0, quadCount * 6, verticesPerPatch = 0)
            shader.end()
            quadCount = 0
        }
        queuedInstances = 0
    }

    private fun insertCharacterQuad(
        fontMap: FontImageMap,
        bw: BufferWriter,
        character: Char,
        cx: Double,
        cy: Double,
        instance: Int,
        textSetting: TextSettingMode
    ) : Pair<Double, GlyphRectangle?> {

        val rectangle = fontMap.map[character] ?: fontMap.map[' ']
        val targetContentScale = RenderTarget.active.contentScale

        val x = if (textSetting == TextSettingMode.PIXEL) round(cx * targetContentScale) / targetContentScale else cx
        val y = if (textSetting == TextSettingMode.PIXEL) round(cy * targetContentScale) / targetContentScale else cy

        val metrics = fontMap.glyphMetrics[character] ?: fontMap.glyphMetrics[' '] ?: error("glyph or space substitute not found")


        val glyphRectangle =
        if (rectangle != null) {
            val pad = 2.0f
            val ushift = 0.0f// if (metrics.xBitmapShift <= pad) -(metrics.xBitmapShift/fontMap.texture.effectiveWidth).toFloat() else 0.0f
            val xshift = (metrics.xBitmapShift/fontMap.contentScale).toFloat()

            val yshift = (metrics.yBitmapShift / fontMap.contentScale).toFloat()

            val u0 = (rectangle.x.toFloat() - pad) / fontMap.texture.effectiveWidth + ushift
            val u1 = (rectangle.x.toFloat() + rectangle.width.toFloat() + pad) / fontMap.texture.effectiveWidth + ushift
            val v0 = (rectangle.y.toFloat() - pad) / fontMap.texture.effectiveHeight
            val v1 = v0 + (pad * 2 + rectangle.height.toFloat()) / fontMap.texture.effectiveHeight

            val x0 = x.toFloat() - pad / fontMap.contentScale.toFloat() + xshift
            val x1 = x.toFloat() + (rectangle.width.toFloat() / fontMap.contentScale.toFloat()) + pad / fontMap.contentScale.toFloat() + xshift
            val y0 = y.toFloat() - pad / fontMap.contentScale.toFloat() + yshift
            val y1 = y.toFloat() + rectangle.height.toFloat() / fontMap.contentScale.toFloat() + pad / fontMap.contentScale.toFloat() + yshift

            val s0 = 0.0f
            val t0 = 0.0f
            val s1 = 1.0f
            val t1 = 1.0f

            val w = (x1 - x0)
            val h = (y1 - y0)
            val z = quadCount.toFloat()

            val floatInstance = instance.toFloat()

            if (quadCount < maxQuads) {
                bw.apply {
                    write(u0, v0); write(s0, t0, w, h); write(x0, y0, z); write(floatInstance)
                    write(u1, v0); write(s1, t0, w, h); write(x1, y0, z); write(floatInstance)
                    write(u1, v1); write(s1, t1, w, h); write(x1, y1, z); write(floatInstance)

                    write(u0, v0); write(s0, t0, w, h); write(x0, y0, z); write(floatInstance)
                    write(u0, v1); write(s0, t1, w, h); write(x0, y1, z); write(floatInstance)
                    write(u1, v1); write(s1, t1, w, h); write(x1, y1, z); write(floatInstance)
                }
                quadCount++
            }
            GlyphRectangle(character, x0.toDouble(), y0.toDouble(), (x1-x0).toDouble(), (y1-y0).toDouble())
        } else {
            null
        }
        return Pair(x - cx, glyphRectangle)
    }

    private fun insertCharacterQuadOnPath(
        contour: RectifiedContour,
        fontMap: FontImageMap,
        bw: BufferWriter,
        character: Char,
        cx: Double,
        cy: Double,
        instance: Int,
        textSetting: TextSettingMode,
        scale: Double
    ) : Pair<Double, GlyphRectangle?> {

        val rectangle = fontMap.map[character] ?: fontMap.map[' ']
        val targetContentScale = RenderTarget.active.contentScale

        val x = if (textSetting == TextSettingMode.PIXEL) round(cx * targetContentScale) / targetContentScale else cx
        val y = if (textSetting == TextSettingMode.PIXEL) round(cy * targetContentScale) / targetContentScale else cy

        val metrics = fontMap.glyphMetrics[character] ?: fontMap.glyphMetrics[' '] ?: error("glyph or space substitute not found")


        val glyphRectangle =
            if (rectangle != null) {
                val pad = 2.0f
                val ushift = 0.0f
                val xshift = (metrics.xBitmapShift/fontMap.contentScale).toFloat()
                val yshift = (metrics.yBitmapShift / fontMap.contentScale).toFloat()

                val u0 = (rectangle.x.toFloat() - pad) / fontMap.texture.effectiveWidth + ushift
                val u1 = (rectangle.x.toFloat() + rectangle.width.toFloat() + pad) / fontMap.texture.effectiveWidth + ushift
                val v0 = (rectangle.y.toFloat() - pad) / fontMap.texture.effectiveHeight
                val v1 = v0 + (pad * 2 + rectangle.height.toFloat()) / fontMap.texture.effectiveHeight


                val x0 = - pad / fontMap.contentScale.toFloat() + xshift
                val x1 = (rectangle.width.toFloat() / fontMap.contentScale.toFloat()) + pad / fontMap.contentScale.toFloat() + xshift


                val t = (x + (x0+x1)/2.0) / (contour.contour.length/scale)

                if (t >= 1.0) {
                    null
                } else {
                    val pose = contour.pose(t)

                    val y0 = -pad / fontMap.contentScale.toFloat() + yshift
                    val y1 =
                        rectangle.height.toFloat() / fontMap.contentScale.toFloat() + pad / fontMap.contentScale.toFloat() + yshift

                    val ch = (y1 - y0).toDouble()
                    val transform = buildTransform {


                        //translate((x0+x1)/2.0, 0.0)
                        multiply(Matrix44(pose.c0r0, pose.c1r0, pose.c2r0, pose.c3r0/scale,
                            pose.c0r1,  pose.c1r1, pose.c2r1, pose.c3r1/scale,
                            pose.c0r2,  pose.c1r2, pose.c2r2, pose.c3r2/scale,
                            pose.c0r3,  pose.c1r3, pose.c2r3, pose.c3r3,
                            ))
                        multiply(
                            Matrix44(
                                -1.0, 0.0, 0.0, 0.0,
                                0.0, -1.0, 0.0, 0.0,
                                0.0, 0.0, 1.0, 0.0,
                                0.0, 0.0, 0.0, 1.0
                            )
                        )

                        translate(-(x0 + x1) / 2.0, 0.0)

                    }


                    val p00 = Vector2(x0.toDouble(), y0.toDouble())
                    val p01 = Vector2(x0.toDouble(), y1.toDouble())
                    val p10 = Vector2(x1.toDouble(), y0.toDouble())
                    val p11 = Vector2(x1.toDouble(), y1.toDouble())

                    val t00 = p00.transform(transform)
                    val t01 = p01.transform(transform)
                    val t10 = p10.transform(transform)
                    val t11 = p11.transform(transform)

                    lastPos = t00

                    val s0 = 0.0f
                    val t0 = 0.0f
                    val s1 = 1.0f
                    val t1 = 1.0f

                    val w = (x1 - x0)
                    val h = (y1 - y0)
                    val z = quadCount.toFloat()

                    val floatInstance = instance.toFloat()

                    if (quadCount < maxQuads) {
                        bw.apply {
                            write(u0, v0); write(s0, t0, w, h); write(t00.x.toFloat(), t00.y.toFloat(), z); write(
                            floatInstance
                        )
                            write(u1, v0); write(s1, t0, w, h); write(t10.x.toFloat(), t10.y.toFloat(), z); write(
                            floatInstance
                        )
                            write(u1, v1); write(s1, t1, w, h); write(t11.x.toFloat(), t11.y.toFloat(), z); write(
                            floatInstance
                        )

                            write(u0, v0); write(s0, t0, w, h); write(t00.x.toFloat(), t00.y.toFloat(), z); write(
                            floatInstance
                        )
                            write(u0, v1); write(s0, t1, w, h); write(t01.x.toFloat(), t01.y.toFloat(), z); write(
                            floatInstance
                        )
                            write(u1, v1); write(s1, t1, w, h); write(t11.x.toFloat(), t11.y.toFloat(), z); write(
                            floatInstance
                        )
                        }
                        quadCount++
                    }
                    GlyphRectangle(character, x0.toDouble(), y0.toDouble(), (x1 - x0).toDouble(), (y1 - y0).toDouble())
                }
            } else {
                null
            }
        return Pair(x - cx, glyphRectangle)
    }
}
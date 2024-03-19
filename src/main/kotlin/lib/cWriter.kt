package lib

import org.openrndr.draw.*
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle

fun Writer.newLine(count: Int = 1) {
    cursor.x = box.corner.x
    cursor.y += ((drawStyle.fontMap?.leading
        ?: 0.0) + style.leading) * count
}


@Suppress("unused")
class TextToken(val token: String, val x: Double, val y: Double, val width: Double, val tracking: Double)

@Suppress("unused", "UNUSED_PARAMETER")
class CWriter(val drawerRef: Drawer?) {
    var cursor = Cursor()
    var boxes = listOf(
        Rectangle(
            Vector2.ZERO, drawerRef?.width?.toDouble() ?: Double.POSITIVE_INFINITY, drawerRef?.height?.toDouble()
                ?: Double.POSITIVE_INFINITY
        )
    )
        set(value) {
            field = value
            cursor.x = value.first().corner.x
            cursor.y = value.first().corner.y
        }
    var style = WriteStyle()
    val styleStack = ArrayDeque<WriteStyle>()
    val box: Rectangle
        get() = boxes.first()
    var leading
        get() = style.leading
        set(value) {
            style.leading = value
        }
    var tracking
        get() = style.tracking
        set(value) {
            style.tracking = value
        }
    var ellipsis
        get() = style.ellipsis
        set(value) {
            style.ellipsis = value
        }
    var drawStyle: DrawStyle = DrawStyle()
        get() {
            return drawerRef?.drawStyle ?: field
        }
        set(value) {
            field = drawStyle
        }

    fun nextBox(): Boolean {
        if (boxes.size > 1) {
            boxes = boxes.drop(1)
            newLine()
            return true
        } else {
            return false
        }
    }

    fun newLine() {
        cursor.x = box.corner.x
        cursor.y += /*(drawer.drawStyle.fontMap?.height ?: 0.0)*/ +(drawStyle.fontMap?.leading
            ?: 0.0) + style.leading
    }

    fun gaplessNewLine() {
        cursor.x = box.corner.x
        cursor.y += drawStyle.fontMap?.height ?: 0.0
    }

    fun move(x: Double, y: Double) {
        cursor.x += x
        cursor.y += y
    }

    fun textWidth(text: String): Double =
        text.sumOf { (drawStyle.fontMap as FontImageMap).glyphMetrics[it]?.advanceWidth ?: 0.0 } +
                (text.length - 1).coerceAtLeast(0) * style.tracking

    /**
     * Draw text
     * @param text the text to write, may contain newlines
     * @param visible draw the text when set to true, when set to false only type setting is performed
     * @return a list of [TextToken] instances
     */
    fun text(text: String, visible: Boolean = true): List<TextToken> {
        // Triggers loading the default font (if needed) by accessing .fontMap
        // otherwise makeRenderTokens() is not aware of the default font.
        drawerRef?.fontMap
        val renderTokens = makeTextTokens(text, false)
        if (visible) {
            drawTextTokens(renderTokens)
        }
        return renderTokens
    }

    /**
     * Draw pre-set text tokens.
     * @param tokens a list of [TextToken] instances
     * @since 0.4.3
     */
    fun drawTextTokens(tokens: List<TextToken>) {
        drawerRef?.let { d ->
            val renderer = d.fontImageMapDrawer
            tokens.forEach {
                renderer.queueText(
                    fontMap = d.drawStyle.fontMap!!,
                    text = it.token,
                    x = it.x,
                    y = it.y,
                    tracking = style.tracking,
                    kerning = drawStyle.kerning,
                    textSetting = drawStyle.textSetting
                )
            }
            renderer.flush(d.context, d.drawStyle)
        }
    }

    fun makeTextTokens(text: String, mustFit: Boolean = false): List<TextToken> {
        drawStyle.fontMap?.let { font ->
            var fits = true
            font as FontImageMap
            val lines = text.split("((?<=\n)|(?=\n))".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val tokens = mutableListOf<String>()
            lines.forEach { line ->
                val lineTokens = line.split("((?<=[ \u00AD]))".toRegex())
                tokens.addAll(lineTokens)
            }
            val localCursor = Cursor(cursor)
            val spaceWidth = font.glyphMetrics[' ']!!.advanceWidth
            val verticalSpace = style.leading + font.leading
            val textTokens = mutableListOf<TextToken>()
            var lastToken = ""
            tokenLoop@ for (i in 0 until tokens.size) {
                val token = tokens[i]
                if (token == "\n") {
                    localCursor.x = box.corner.x
                    localCursor.y += verticalSpace
                } else {
                    val tokenWidth = token.sumOf {
                        font.glyphMetrics[it]?.advanceWidth ?: 0.0
                    } + style.tracking * token.length
                    if (localCursor.x + tokenWidth < box.x + box.width && localCursor.y <= box.y + box.height || tokenWidth >= box.width) run {
                        val textToken = TextToken(token, localCursor.x, localCursor.y, tokenWidth, style.tracking)
                        emitToken(localCursor, textTokens, textToken)

                    } else {
                        if (localCursor.y > box.corner.y + box.height) {
                            fits = false
                        }
                        if (localCursor.y + verticalSpace <= box.y + box.height) {
                            if (lastToken.length > 0 && lastToken.last() == '\u00AD') {
                                val hypen = TextToken("-", localCursor.x, localCursor.y, tokenWidth, style.tracking)
                                emitToken(localCursor, textTokens, hypen)
                            }
                            localCursor.y += verticalSpace
                            localCursor.x = box.x
                            emitToken(
                                localCursor,
                                textTokens,
                                TextToken(token, localCursor.x, localCursor.y, tokenWidth, style.tracking)
                            )
                        } else {
                            if (nextBox()) {
                                localCursor.x = cursor.x
                                localCursor.y = cursor.y
                                emitToken(
                                    localCursor,
                                    textTokens,
                                    TextToken(token, localCursor.x, localCursor.y, tokenWidth, style.tracking)
                                )
                            } else if (!mustFit && style.ellipsis != null && cursor.y <= box.y + box.height) {
                                emitToken(
                                    localCursor, textTokens, TextToken(
                                        style.ellipsis
                                            ?: "", localCursor.x, localCursor.y, tokenWidth, style.tracking
                                    )
                                )
                                break@tokenLoop
                            } else {
                                fits = false
                            }
                        }
                        cursor.y += verticalSpace
                        cursor.x = box.x
                    }
                    localCursor.x += tokenWidth
//                    if (i != tokens.size - 1) {
//                        localCursor.x += spaceWidth
//                    }
                }
                lastToken = token
            }
            if (fits || (!fits && !mustFit)) {
                cursor = Cursor(localCursor)
            } else {
                textTokens.clear()
            }
            return textTokens
        }
        return emptyList()
    }



    private fun emitToken(cursor: Cursor, textTokens: MutableList<TextToken>, textToken: TextToken) {
        textTokens.add(textToken)
    }
}

fun <T> cwriter(drawer: Drawer, f: CWriter.() -> T): T {
    val writer = CWriter(drawer)
    return writer.f()
}

@JvmName("drawerWriter")
fun <T> Drawer.cwriter(f: CWriter.() -> T): T {
    return cwriter(this, f)
}
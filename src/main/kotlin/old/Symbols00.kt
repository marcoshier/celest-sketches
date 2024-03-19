package old

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.noise.scatter
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.hobbyCurve
import org.openrndr.math.transforms.buildTransform
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1280
        height = 720
    }
    oliveProgram {

        val p = drawer.bounds.scatter(8.0, 8.0, 30.0)
        val symbols = p.map { randomElement().transform(buildTransform { translate(it) }) }

        val lines = (0..8).map { hobbyCurve((0..Int.uniform(1, 6)).map { p.random() }) }

        extend {
            drawer.clear(ColorRGBa.WHITE.shade(0.8))

            drawer.stroke = null
            drawer.fill = ColorRGBa.BLACK
            drawer.contours(symbols)

            drawer.stroke = ColorRGBa.BLACK
            for ((i, line) in lines.withIndex()) {
                val r = Double.uniform(random = Random(i))
                if (r > 0.0) {
                    drawer.shadeStyle = dashed(0.4)
                    drawer.contour(line)
                    drawer.shadeStyle = null
                } else {
                    drawer.contour(line)
                }
            }
        }
    }
}
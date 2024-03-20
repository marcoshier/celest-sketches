import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.fx.distort.PolarToRectangular
import org.openrndr.extra.fx.distort.RectangularToPolar
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.Arrangement
import org.openrndr.extra.shapes.bezierPatch
import org.openrndr.math.Polar
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.offset
import org.openrndr.svg.loadSVG
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1080
        height = 1080
        multisample = WindowMultisample.SampleCount(32)
    }
    oliveProgram {

        extend(Screenshots())

        val r = 500.0
        val density = 20

        val circle = Circle(drawer.bounds.center, r)
        val bezierPatch = bezierPatch(circle.contour)

        val tex = loadImage("data/output2.jpg")
        PolarToRectangular().apply {
            logPolar = true
        }.apply(tex, tex)

        ShapeContour

        val contoursU = (0..density).map { bezierPatch.horizontal(it / density.toDouble()) }
        val contoursV = (0..density).map { bezierPatch.vertical(it / density.toDouble()) }

        val arrangement = Arrangement(contoursU + contoursV)
        extend {
            drawer.circle(circle)

            drawer.stroke = ColorRGBa.BLACK

            drawer.strokeWeight = 1.0


            arrangement.edges.mapIndexed {  i, it ->
                val c = it.contour

                repeat(12) {
                    val cnt = c.offset((it - 5) * 7.0)
                    if (cnt.position(0.5) in circle) {
                        val rnd = Double.uniform(0.0, 360.0, Random(it))
                        val tr = buildTransform {
                            translate(c.bounds.center + rnd * 0.005)
                            rotate(rnd * 0.015)
                            translate(-c.bounds.center + rnd * 0.005)
                        }
                        drawer.contour(cnt.transform(tr))
                    }
                }
            }
            drawer.shadeStyle = null
        }
    }
}

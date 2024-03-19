import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.camera.ProjectionType
import org.openrndr.extra.jumpfill.DirectionalField
import org.openrndr.extra.meshgenerators.sphereMesh
import org.openrndr.extra.noise.scatter
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.bezierPatch
import org.openrndr.extra.triangulation.delaunayTriangulation
import org.openrndr.math.Polar
import org.openrndr.math.Spherical
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.svg.loadSVG
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun main() = application {
    configure {
        width = 960
        height = 400
        multisample = WindowMultisample.SampleCount(32)
    }
    oliveProgram {

        val r = 60.0
        val density = 30

        val centers = loadSVG("data/svg/spheres_3x17.svg").findShapes().map { it.shape.bounds.center * 1.2 + 20.0 }
        val circles = centers.map { Circle(it, r) }

        val bezierPatches = circles.map { it to bezierPatch(it.contour) }

        extend {

            drawer.circles(circles)


            for ((i, cbp) in bezierPatches.withIndex()) {
                val (c, bp) = cbp
                drawer.strokeWeight = 0.1

                val lightPosition = Polar(seconds * r + Double.uniform(0.0, 360.0, Random(i)), r).cartesian +  bp.contour.bounds.center

                for (j in 0..density) {
                    drawer.shadeStyle = shadeStyle {
                        fragmentTransform = """
                            float d = distance(c_screenPosition.xy, p_pos) / ${r * 2.0};
                            x_stroke *= sign(smoothstep(0.2, 0.8, d) - 0.5);
                        """.trimIndent()
                        parameter("pos", lightPosition)
                    }
                    drawer.contour(bp.horizontal(j / density.toDouble()))
                    drawer.contour(bp.vertical(j / density.toDouble()))
                    drawer.shadeStyle = null
                }
            }
        }
    }
}

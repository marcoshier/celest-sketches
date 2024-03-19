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
import org.openrndr.extra.triangulation.delaunayTriangulation
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
        width = 780
        height = 290
        multisample = WindowMultisample.SampleCount(8)
    }
    oliveProgram {

        val sph = sphereMesh(radius = 2.0)
        val centers = loadSVG("data/svg/spheres_3x17.svg").findShapes().map { it.shape.bounds.center }

        val vf = vertexFormat { attribute("transform", VertexElementType.MATRIX44_FLOAT32) }
        val tr = vertexBuffer(vf, centers.size)

        tr.put {
            for (c in centers) write(transform {
                translate(c / 20.0)
            })
        }

        extend(Orbital()) {
            projectionType = ProjectionType.ORTHOGONAL
            eye = Vector3(0.0, 0.0,1.0)
            camera.magnitudeEnd = 8.0
            userInteraction = false
        }
        extend {

            drawer.clear(ColorRGBa.BLACK)

            drawer.translate(-18.0, -7.0, 0.0)

            drawer.shadeStyle = shadeStyle {
                vertexTransform = "x_viewMatrix *= i_transform;"
                fragmentTransform = """
                float l = dot(v_worldNormal, p_lightDirection - (sin(c_instance) * 0.5 + 0.5));
                x_fill.xyz *= min(0.9, l);""".trimIndent() //
                parameter("seconds", seconds)
                parameter("lightDirection",
                    Spherical(cos(seconds * 1 * PI * 0.1) * 180.0, sin(seconds * 1 * PI * 0.1) * 90.0, 1.0).cartesian)
            }
            drawer.vertexBufferInstances(listOf(sph), listOf(tr), DrawPrimitive.TRIANGLES, centers.size)

        }
    }
}

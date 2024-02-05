import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.DrawPrimitive
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.draw.shadeStyle
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.camera.Camera2D
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.marchingsquares.findContours
import org.openrndr.extra.meshgenerators.boxMesh
import org.openrndr.extra.meshgenerators.buildTriangleMesh
import org.openrndr.extra.meshgenerators.extrudeShape
import org.openrndr.extra.noise.simplex
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.hobbyCurve
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.LineSegment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun main() = application {
    configure {
        width = 720
        height = 720
    }
    oliveProgram {


        extend(Orbital()) {
            eye = Vector3.UNIT_Z * -50.0
        }

        val rt = renderTarget(width, height) {
            colorBuffer()
            depthBuffer()
        }


        fun f(v: Vector2): Double {
            val iv = v.toInt()
            val d = if (iv.x >= 0 && iv.y >= 0 && iv.x < width && iv.y < height) rt.colorBuffer(0).shadow[iv.x, iv.y].luminance else 0.0
            return cos(d * PI * 3.0)
        }

        drawer.isolatedWithTarget(rt) {
            drawer.shadeStyle = shadeStyle {

                fragmentPreamble = perlin_noise
                fragmentTransform = """
                    vec2 pos = c_boundsPosition.xy * c_boundsSize.xy;		
	                float c, n;

	                n = perlin_noise((pos) * 0.25);
	                c = abs(cos(n*10.0));

	                x_fill = vec4(c, c, c, 1.0);
                """.trimIndent()
                parameter("t", seconds)
            }

            drawer.rectangle(drawer.bounds)
        }

        rt.colorBuffer(0).shadow.download()

        val contours = findContours(::f, drawer.bounds.offsetEdges(32.0), 12.0)

        val extr = contours.map {
            buildTriangleMesh {
                extrudeShape(it.close().shape, (1.0 / it.bounds.area) * 2000.0)
            }
        }


        extend {

            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                        x_fill.rgb *= v_viewNormal.z;
                    """.trimIndent()
            }
            for (ex in extr) {
                drawer.vertexBuffer(ex, DrawPrimitive.TRIANGLES)
            }
            drawer.vertexBuffer(boxMesh(20.0, 20.0, 20.0), DrawPrimitive.TRIANGLES)
/*
            drawer.defaults()


            drawer.fill = null
            drawer.clear(ColorRGBa.BLACK)
            drawer.stroke = ColorRGBa.WHITE
            drawer.contours(contours)*/

        }
    }
}
import lib.MutableMesh
import lib.decal
import lib.generateSphere
import lib.writeToVertexBuffer
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.meshgenerators.sphereMesh
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.noise.uniformRing
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform
import org.openrndr.math.transforms.lookAt
import kotlin.math.PI
import kotlin.math.cos

fun main() {
    application {
        configure {
            width = 960
            height = 960
        }
        program {
            val sphereMesh = MutableMesh()
            val moonTexture = loadImage("data/images/lroc_color_poles_8k.png")
            val faceTexture = loadImage("data/images/portrait-01.png")

            generateSphere(sides =  24, segments = 24, radius = 1.0, mesh = sphereMesh)
            val vb = vertexBuffer(vertexFormat {
                position(3)
                normal(3)
                textureCoordinate(2)
            }, sphereMesh.positions.size)


            sphereMesh.writeToVertexBuffer(vb)

            val decalVbs = mutableListOf<VertexBuffer>()

            for (i in 0 until 1000) {

                val pm = lookAt(Vector3.uniform(-1.0, 1.0).normalized*1.2, Vector3.uniform(-0.75, 0.75), Vector3.UNIT_Y).inversed * buildTransform {
                    rotate(Vector3.UNIT_Z, Double.uniform(-45.0, 45.0))
                }
                val decalMesh = sphereMesh.decal(pm, Vector3(0.5, 0.5, 4.0))

                val decalVb = vertexBuffer(vertexFormat {
                    position(3)
                    normal(3)
                    textureCoordinate(2)
                }, decalMesh.positions.size)
                decalMesh.writeToVertexBuffer(decalVb)
                decalVbs.add(decalVb)
            }



            extend(Orbital()) {
                fov = 15.0
            }
            extend {
                drawer.rotate(Vector3.UNIT_Y, seconds*18.0, TransformTarget.MODEL)

                drawer.shadeStyle = shadeStyle {
                    fragmentTransform = """
                        x_fill = texture(p_texture, va_texCoord0 - vec2(0.5, 0.0));
                    """
                    parameter("texture", moonTexture)
                }

                drawer.vertexBuffer(vb, DrawPrimitive.TRIANGLES)

                drawer.shadeStyle = shadeStyle {
                    vertexTransform = """
                        x_projectionMatrix[3].z -= p_zOffset;
                    """.trimIndent()
                    fragmentTransform = """
                        x_fill = texture(p_texture, va_texCoord0.xy).rrra;
                        float l = length(va_texCoord0.xy - vec2(0.5));
                        x_fill.a *= smoothstep(0.5, 0.4, l) * 0.9;
                    """.trimIndent()
                    parameter("texture", faceTexture)
                    parameter("zOffset", 0.01)
                }

                drawer.fill = ColorRGBa.GREEN
                for ((index, decalVb) in decalVbs.take((decalVbs.size * (cos(PI+seconds*PI*0.05)*0.5+0.5)).toInt()).withIndex()) {
                    drawer.shadeStyle!!.parameter("zOffset", 0.0001 + index* 0.00001)
                    drawer.vertexBuffer(decalVb, DrawPrimitive.TRIANGLES)
                }
            }
        }
    }
}
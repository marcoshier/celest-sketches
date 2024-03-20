import lib.MutableMesh
import lib.decal
import lib.generateSphere
import lib.writeToVertexBuffer
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.drawImage
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.compositor.*
import org.openrndr.extra.jumpfill.DirectionalField
import org.openrndr.extra.meshgenerators.sphereMesh
import org.openrndr.extra.noise.scatter
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.shapes.grid
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform
import org.openrndr.math.transforms.lookAt
import org.openrndr.shape.Rectangle
import kotlin.math.PI
import kotlin.math.cos

fun main() = application {
    configure { }
    oliveProgram {

        val sphereMesh = MutableMesh()
        generateSphere(sides =  128, segments = 128, radius = 5.0, mesh = sphereMesh)

        val vb = vertexBuffer(vertexFormat {
            position(3)
            normal(3)
            textureCoordinate(2)
        }, sphereMesh.positions.size)

        sphereMesh.writeToVertexBuffer(vb)

        val hm = loadImage("data/depthmap-0002-final.jpg")
        val points = hm.bounds.scatter(20.0)

        val patch = drawImage(128, 128) {
            drawer.strokeWeight = 0.5
            drawer.rectangles(
                bounds.grid(9, 24).flatten()
            )
        }

        val rt = renderTarget(width, height) {
            colorBuffer()
            depthBuffer()
        }

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
            eye = Vector3.UNIT_Z * -40.0
            near = 0.1
            far = 5000.0
        }
        extend {


            drawer.isolatedWithTarget(rt) {
                drawer.defaults()
                drawer.clear(ColorRGBa.BLACK)

                drawer.image(hm)
                drawer.circles(points, 2.0)

                drawer.image(patch)
            }


            drawer.shadeStyle = shadeStyle {
                vertexTransform = """
                    vec4 offset = texture(p_hm, a_texCoord0- vec2(0.5, 0.0));
                    float lum = 0.2126 * offset.r + 0.7152 * offset.g + 0.0722 * offset.b;
                    x_viewMatrix[3].z -= max(0.0, abs(1.0 - lum)) * 14.0;
                """.trimIndent()
                fragmentTransform = """
                    vec4 offset = texture(p_hm, va_texCoord0 - vec2(0.5, 0.0));
                    x_fill.xyz *= v_viewNormal.z * offset.xyz;
                """.trimIndent()
                parameter("hm", hm)
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
                parameter("texture", patch)
                parameter("zOffset", 0.01)
            }

            drawer.fill = ColorRGBa.GREEN
            for ((index, decalVb) in decalVbs.take((decalVbs.size * (cos(PI +seconds* PI *0.05) *0.5+0.5)).toInt()).withIndex()) {
                drawer.shadeStyle!!.parameter("zOffset", 0.0001 + index* 0.00001)
                drawer.vertexBuffer(decalVb, DrawPrimitive.TRIANGLES)
            }

            drawer.defaults()
           // drawer.image(rt.colorBuffer(0))

        }
    }
}
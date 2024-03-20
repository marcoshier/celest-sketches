import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.compositor.*
import org.openrndr.extra.meshgenerators.sphereMesh
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.math.Vector3
import org.openrndr.shape.Rectangle

fun main() = application {
    configure { }
    oliveProgram {

        val parameters = object {
            @DoubleParameter("patch noise", 0.0, 1.0)
            var patchNoise = 0.0

            @DoubleParameter("patch falloff", 0.0, 1.0)
            var falloff = 0.0
        }

        val sph = sphereMesh(64, 64)



        val patchRt = renderTarget(128, 128) { colorBuffer() }

        val texture = loadImage("data/output.jpg")

        val c = compose {
            val aside = aside {
                draw {
                    drawer.defaults()
                    drawer.clear(ColorRGBa.BLUE)
                    val r = Rectangle(0.0, 0.0, 512.0, 512.0)
                    drawer.image(texture, r, Rectangle(0.0, 0.0, 128.0, 128.0))
                }
            }

            layer {
                draw {
                    drawer.image(aside.result)
                }
                mask {
                    drawer.circle(64.0, 64.0, 62.0)
                }
            }
        }


        extend(Orbital()) {
            eye = Vector3.UNIT_Z * -2.0
        }
        extend {

            drawer.isolatedWithTarget(patchRt) {
                drawer.defaults()
                drawer.clear(ColorRGBa.TRANSPARENT)
                c.draw(drawer)
            }



            val ss = shadeStyle {
                vertexPreamble = "out vec3 o_position;"
                /*vertexTransform = """
                    vec3 p = x_position * 12.0;
                    o_position = vec3(sin(p.y * 1.0) * 0.05, cos(p.z * 0.66) * 0.05, sin(p.x * 1.5) * 0.05);
                    x_position += o_position;
                """.trimIndent()
*/
                fragmentPreamble = "in vec3 o_position;"
                fragmentTransform = """
                    float overlapFactor = 3.0;
                    
                    vec2 textureSize = textureSize(p_texture, 0);
                    vec2 overlapSize = vec2(textureSize.x * overlapFactor, textureSize.y * overlapFactor);
    
                    vec2 blockCoord = floor((va_texCoord0.xy * textureSize) / (textureSize - overlapSize));
                    
                    
                    vec4 texColor = texture(p_texture, fract(va_texCoord0 * 64.0) * 0.75 + 0.125);
                    vec4 txn0 = texture(p_texture, fract(va_texCoord0 * 64.0) * 0.75 + vec2(0.55, 0.0));
                    
                    x_fill.xyz = max(texColor.xyz, txn0.xyz);
                """.trimIndent()
                parameter("seconds", seconds * 5.0)
                parameter("texture", patchRt.colorBuffer(0))
            }

            drawer.shadeStyle = ss
            drawer.vertexBuffer(sph, DrawPrimitive.TRIANGLES)


            drawer.shadeStyle = shadeStyle {
                vertexPreamble = "out vec3 o_position;"
                /*vertexTransform = """
                    vec3 p = x_position * 12.0;
                    o_position = vec3(sin(p.y * 1.0) * 0.05, cos(p.z * 0.66) * 0.05, sin(p.x * 1.5) * 0.05);
                    x_position += o_position;
                """.trimIndent()*/
            }
            drawer.stroke = ColorRGBa.WHITE
            drawer.vertexBuffer(sph, DrawPrimitive.LINE_STRIP)

            drawer.defaults()
            drawer.image(patchRt.colorBuffer(0))
        }
    }
}
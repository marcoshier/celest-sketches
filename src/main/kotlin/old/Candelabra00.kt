package old

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.meshgenerators.boxMesh
import org.openrndr.extra.noise.gaussian
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.buildTransform

fun main() = application {
    configure {
        width = 1280
        height = 720
    }
    oliveProgram {

        val rect = boxMesh(1.0, 0.8, 0.1)
        val count = 250

        val transform = vertexBuffer(vertexFormat {
            attribute("transform", VertexElementType.MATRIX44_FLOAT32)
        }, count * rect.vertexCount)

        transform.put {
            (0..count).map {
                val p = Vector2.gaussian(Vector2.ZERO, Vector2.ONE * 30.0)
                write( buildTransform {
                    translate(p)
                    scale(10.0)
                    rotate(Vector3.UNIT_Y, Int.uniform() * 90.0)
                })
            }
        }

        extend(Orbital()) {
            eye = Vector3.UNIT_Z * -30.0
            dampingFactor = 0.0
        }

        extend {

            drawer.clear(ColorRGBa.BLACK)

            drawer.shadeStyle = shadeStyle {
                fragmentTransform = "x_fill.a *= 0.5;"
                vertexTransform = "x_viewMatrix *= i_transform;"
            }

            drawer.vertexBufferInstances(listOf(rect), listOf(transform), DrawPrimitive.TRIANGLE_STRIP, transform.vertexCount)
        }
    }
}
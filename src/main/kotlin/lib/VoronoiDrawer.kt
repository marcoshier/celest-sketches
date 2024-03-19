package lib

import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

class VoronoiDrawer {

    val sides = 40
    val cone = vertexBuffer(vertexFormat {
        position(3)
    }, sides * 3)

    val instances = vertexBuffer(vertexFormat {
        attribute("offset", VertexElementType.VECTOR3_FLOAT32)
        color(4)
    }, 250_000)

    init {
        cone.put {
            for (side in 0 until sides) {
                write(Vector3(0.0, 0.0, 1.0))
                val x0 = 1.0 * cos(Math.PI * 2 * ((side * 1.0) / sides))
                val y0 = 1.0 * sin(Math.PI * 2 * ((side * 1.0) / sides))
                val x1 = 1.0 * cos(Math.PI * 2 * (((side + 1.0) * 1.0) / sides))
                val y1 = 1.0 * sin(Math.PI * 2 * (((side + 1.0) * 1.0) / sides))
                write(Vector3(x0, y0, 0.0))
                write(Vector3(x1, y1, 0.0))
            }
        }

    }

    fun draw(drawer: Drawer, positions: List<Vector2>, radius: Double = 100.0) {
        draw(drawer, positions.map { it.xy0 }, radius)
    }

    @JvmName("draw3")
    fun draw(drawer: Drawer, positions: List<Vector3>, radius: Double = 100.0) {


        instances.put {
            positions.forEachIndexed { index, it ->
                write(it)
                write(ColorRGBa((index % 256) / 255.0, ((index / 256) % 256) / 255.0, ((index / 65536) % 256) / 255.0, 1.0))
            }
        }

        drawer.isolated {
            println("${RenderTarget.active.width} ${RenderTarget.active.height}")
            val renderTarget = RenderTarget.active

            ortho(0.0, renderTarget.width.toDouble(), renderTarget.height.toDouble(), 0.0, -1000.0, 1000.0)
            drawer.view = Matrix44.IDENTITY
            drawer.model = Matrix44.IDENTITY
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL

            drawer.shadeStyle = org.openrndr.draw.shadeStyle {

                vertexTransform = """
                    x_position.xyz *= p_radius;
                    x_position.xyz += i_offset.xyz;

                """.trimIndent()

                fragmentTransform = """
                    x_fill = vi_color;
                """.trimIndent()

                parameter("radius", radius)
            }

            drawer.vertexBufferInstances(
                listOf(cone),
                listOf(instances),
                DrawPrimitive.TRIANGLES,
                positions.size,
                0,
                sides * 3
            )
        }


    }

}
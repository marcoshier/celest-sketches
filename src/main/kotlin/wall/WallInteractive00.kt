package wall

import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.camera.ProjectionType
import org.openrndr.extra.meshgenerators.sphereMesh
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Quaternion
import org.openrndr.math.Spherical
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.transforms.transform
import org.openrndr.shape.Circle
import org.openrndr.shape.findShapes
import org.openrndr.svg.loadSVG
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

fun main() = application {

    configure {
        width = 1920
        height = 563
        multisample = WindowMultisample.SampleCount(8)
    }

    oliveProgram {

       // extend(ScreenRecorder())

        val comp = loadSVG("data/svg/templates/template00.svg").findGroups()
        val rect = comp[1].children.first { it.id!!.startsWith("Rect") }
        val centers = (comp[1].findShapes() - rect).map { it.bounds.center }

        val sph = sphereMesh(64, 64, radius = 40.0)

        val cam = extend(Orbital()) {
            userInteraction = true
            fov = 15.0
            eye = Vector3(0.0, 0.0,2140.0)
            far = 5000.0
            near = 0.1
        }
        extend {

            drawer.defaults()
         //   drawer.circles(centers, 40.0)

            drawer.view = cam.camera.viewMatrix()
            drawer.perspective(cam.fov, width / height.toDouble(), cam.near, cam.far)
            drawer.depthWrite = true
            drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL

            drawer.translate(-width / 2.0, height / 2.0 + 11.5)
            drawer.rotate(Vector3.UNIT_X, 180.0)

            drawer.fill = ColorRGBa.WHITE
            drawer.stroke = null


            for (c in centers) {
                drawer.isolated {

                    val roll = Math.toDegrees(atan2(c.y - mouse.position.y, c.x - mouse.position.x))
                    val pitch = Math.toDegrees(atan2(c.y  - 50.0, c.x - mouse.position.y))
                  //  val pitch = Math.toDegrees(atan2(c.y - mouse.position.y, c.x - mouse.position.x))

                    val mousePos = mouse.position.vector3(z = 50.0)

                    drawer.translate(c)
                    drawer.rotate(Vector3.UNIT_XYZ, roll)
                 //   drawer.rotate(Vector3.UNIT_Y, pitch)
                //    drawer.rotate(Vector3.UNIT_Z, pitch)

                    drawer.shadeStyle = shadeStyle {
                        fragmentTransform = """x_fill.xyz *= sign(va_texCoord0.x - 1.0);"""
                    }
                    drawer.vertexBuffer(sph, DrawPrimitive.TRIANGLES)
                }
            }


        }
    }
}
package wall

import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.camera.ProjectionType
import org.openrndr.extra.compositor.*
import org.openrndr.extra.fx.blur.ApproximateGaussianBlur
import org.openrndr.extra.fx.blur.GaussianBlur
import org.openrndr.extra.meshgenerators.sphereMesh
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.*
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

        val gb = ApproximateGaussianBlur()
        gb.apply {
            sigma = 40.0
            window = 20
        }
        extend(ScreenRecorder())

        val comp = loadSVG("data/svg/templates/template00.svg").findGroups()
        val rect = comp[1].children.first { it.id!!.startsWith("Rect") }
        val centers = (comp[1].findShapes() - rect).map { it.bounds.center }

        val comps = compose {
            centers.map {
                val a = aside {
                    draw {
                        drawer.fill = ColorRGBa.WHITE
                        val angle = atan2(mouse.position.y - it.y, mouse.position.x - it.x)
                        val distance = map(0.0, width * 1.0, 0.0, 50.0,  (mouse.position.times(Vector2(2.4, 1.0))).distanceTo(it.times(Vector2(2.4, 1.0))), true)
                        val ss = smoothstep(0.0, 25.0, distance) * 100.0
                        val sph = Polar(Math.toDegrees(angle), ss).cartesian + it
                        drawer.circle(sph, 50.0)


                        //drawer.circle(Vector2(sin(seconds) * width / 2.0 + width / 2.0, it.y), 50.0)
                    }
                }

                layer {
                    val cb = colorBuffer(width, height)
                    draw {
                        drawer.stroke = null
                        drawer.fill = ColorRGBa.BLACK
                        drawer.circle(it, 50.0)
                        gb.apply(a.result, cb)
                        drawer.image(cb)
                    }
                    mask {
                        drawer.circle(it, 50.0)
                    }
                }
            }
        }


        extend {

            drawer.clear(ColorRGBa.GRAY)
            comps.draw(drawer)
        }
    }
}
package lib

import kotlinx.coroutines.delay
import org.openrndr.collections.pmap
import org.openrndr.color.ColorRGBa
import org.openrndr.math.Vector2
import org.openrndr.draw.ColorBufferShadow
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extra.noise.uniform
import org.openrndr.math.max
import org.openrndr.math.min
import org.openrndr.shape.Rectangle
import org.openrndr.throttle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class SuspendStippleOptimizer(val bounds: Rectangle, val points: MutableList<Vector2>) {

    val target = renderTarget(bounds.width.toInt(), bounds.height.toInt()) {
        colorBuffer()
        depthBuffer()
    }

    var exponent = 16.0
    val directions = points.map { Vector2.ZERO }.toMutableList()
    val linearWeights = points.map { 0.0 }.toMutableList()
    val weights = points.map { 0.0 }.toMutableList()
    val iweights = points.map { 0.0 }.toMutableList()
    val ipoints = points.map { Vector2.ZERO }.toMutableList()

    companion object {
        val voronoiDrawer = VoronoiDrawer()
    }
    class Sample(
        var xsum: Double = 0.0, var ysum: Double = 0.0,
        var weight: Double = 0.0,
        var xisum: Double = 0.0, var yisum: Double = 0.0,
        var iweight: Double = 0.0,
        var count: Double = 0.0,
        var intensitySum: Double = 0.0,
        var min: Double = Double.POSITIVE_INFINITY,
        var max: Double = Double.NEGATIVE_INFINITY,
        var xmin: Double = Double.POSITIVE_INFINITY,
        var ymin: Double = Double.POSITIVE_INFINITY,
        var xmax: Double = Double.NEGATIVE_INFINITY,
        var ymax: Double = Double.POSITIVE_INFINITY,

        ) {

        fun position(): Vector2 {
            return Vector2(xsum / weight, ysum / weight)
        }

        fun direction(): Vector2 {
            val v0 = Vector2(xsum / weight, ysum / weight)
            val v1 = Vector2(xisum / iweight, yisum / iweight)

            return (v1 - v0).normalized
        }

    }


    var samples = mutableMapOf<Int, Sample>()

    var smooth = 0.0
    fun iterate(image: ColorBufferShadow, drawer: Drawer) {
        drawer.isolatedWithTarget(target) {
            drawer.clear(ColorRGBa.BLACK)
            voronoiDrawer.draw(drawer, points, 100.0)
        }
        target.colorBuffer(0).shadow.download()
//        target.colorBuffer(0).read(buffer)

        val shad = target.colorBuffer(0).shadow
        samples = mutableMapOf<Int, Sample>()

        (0 until 16).pmap { t ->

            val sy = (target.height / 16) * t
            val ey = ((target.height / 16) * (t+1)).coerceAtMost(target.height)

            for (y in sy until ey) {
                for (x in 0 until target.width) {
//                    val i = buffer.getInt((y * target.width + x) * 4) and 0xffFFff
                    val c = shad.read(x, y)


                    val i = (c.r * 255).toInt() + (c.g * 255).toInt() * 256 + (c.b * 255).toInt() * 65536
                    val s = synchronized(samples) { samples.getOrPut(i) { Sample() } }
                    var lf = (1.0 - image.read(x, y).toLABa().l / 100.0)
                    var f = lf + 0.0000001
                    f = Math.pow(f, exponent)
                    var fi = (image.read(x, y).toLABa().l / 100.0) + 0.0000001
                    fi = Math.pow(fi, exponent)
                    s.min = min(s.min, fi)
                    s.max = max(s.max, fi)
                    s.xsum += (x + 0.5) * 1.0 * f
                    s.ysum += (y + 0.5) * 1.0 * f
                    s.weight += f
                    s.count += 1.0
                    s.intensitySum += (1.0 - lf)
                    s.xmin = min(s.xmin, x.toDouble())
                    s.xmax = max(s.xmax, x.toDouble())
                    s.ymin = min(s.xmin, y.toDouble())
                    s.ymax = max(s.xmax, y.toDouble())

                    s.xisum += (x + 0.5) * 1.0 * fi
                    s.yisum += (y + 0.5) * 1.0 * fi
                    s.iweight += fi
                }
                //throttle(100)
            }
        }

        points.forEachIndexed { index, it ->
            val s = samples[index]
            if (s != null) {
                val newPosition = if (s.weight > 0) Vector2(s.xsum / s.weight, s.ysum / s.weight) else points[index]
                val newPositionI =
                    if (s.iweight > 0) Vector2(s.xisum / s.iweight, s.yisum / s.iweight) else ipoints[index]

                val d = (newPosition - newPositionI)
                val sd = if (d.length > 0) d.normalized else Vector2.ONE.normalized

                directions[index] = directions[index] * smooth + (sd) * (1.0 - smooth)

                val np =
                if (s.weight > s.iweight) {
                    newPositionI
                } else {
                    newPosition
                }


                points[index] = points[index]  * smooth + newPosition * (1.0 - smooth)
                ipoints[index] = ipoints[index] * smooth + newPositionI * (1.0 - smooth)
                weights[index] = weights[index] * smooth + s.weight * (1.0 - smooth)
                iweights[index] = s.iweight

//                if (s.count <= 100 && Math.random() < 0.1) {
//                    points[index] = Vector2.uniform(image.colorBuffer.bounds)
//                    weights[index] = 0.0
//                }

            } else {
                println("sample missing $index")
            }
        }
    }
}
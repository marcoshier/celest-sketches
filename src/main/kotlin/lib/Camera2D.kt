package lib

import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.animatable.easing.Easing
import org.openrndr.draw.Drawer
import org.openrndr.draw.RenderTarget
import org.openrndr.events.Event
import org.openrndr.extra.camera.ChangeEvents
import org.openrndr.launch
import org.openrndr.math.*
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.Rectangle
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max


fun Matrix44.scale(): Double = (this * Vector4(1.0, 1.0, 0.0, 0.0).normalized).xy.length

class Camera2D(val bounds: Rectangle, val program: Program) : Extension, ChangeEvents, UIElementImpl() {
    override var enabled = true

    private val zoomSpeed = 0.1

    private var minZoom = 0.5
    private var maxZoom = 60.0

    var mappedZoom = 0.0
    private var zoomPosition = Vector2(0.0, 0.0)
    var view = Matrix44.IDENTITY
        set(value) {
            val scale = (value * Vector4(1.0, 1.0, 0.0, 0.0).normalized).xy.length

            if (scale in minZoom..maxZoom) {
                mappedZoom = toNormalizedScale(scale)
                field = value
            }
        }

    override val changed = Event<Unit>()

    private fun quantize(x: Double): Double {
        return floor(x * 10000.0) / 10000.0
    }

    private fun toNormalizedScale(scale: Double): Double {
        return quantize(ln(scale).map(ln(minZoom), ln(maxZoom), 0.0, 1.0, clamp = true))
    }

    private fun toExpScale(scale: Double): Double {
        val lnScale = scale.map(0.0, 1.0, ln(minZoom), ln(maxZoom))
        return exp(lnScale)
    }

    private var panJob: Job? = null
    private var zoomJob: Job? = null

    var velocity = 0.0
        private set

    fun centerAtSlow(worldTargetPosition: Vector2, userViewCenter: Vector2? = null) {
        val maxAxis = max(bounds.width, bounds.height)
        val viewCenter = userViewCenter ?: Vector2(bounds.width / 2.0, bounds.height / 2.0)
        val currentPosition = (view.inversed * viewCenter.xy01).xy

        val travel = currentPosition.distanceTo(worldTargetPosition)
        val pixelTravel = travel * view.scale()

        panJob?.cancel()
        panJob = program.launch {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1000) {
                val dt = (System.currentTimeMillis() - startTime) / 1000.0
                val edt = Easing.CubicInOut.easer.ease(dt, 0.0, 1.0, 1.0)
                velocity = if (pixelTravel > maxAxis) {
                    smoothstep(0.0, 0.5, dt) * smoothstep(1.0, 0.5, dt)
                } else {
                    0.0
                }
                centerAt(currentPosition.mix(worldTargetPosition, edt), viewCenter)
                yield()
            }
        }
    }

    fun centerAt(worldTargetPosition: Vector2, userViewCenter: Vector2? = null) {
        val viewTargetPosition = (view * (worldTargetPosition.xy01)).xy
        val viewCenter = userViewCenter ?: (Vector2(bounds.width, bounds.height) / 2.0)
        view = buildTransform {
            translate(viewCenter - viewTargetPosition)
        } * view
    }

    fun getNormalizedScale(): Double {
        return toNormalizedScale(view.scale())
    }

    fun setNormalizedScaleSlow(targetScaleNormalized: Double) {
        zoomJob?.cancel()
        zoomJob = program.launch {
            val startTime = System.currentTimeMillis()
            val cs = getNormalizedScale()
            while (System.currentTimeMillis() - startTime < 1000) {
                val dt = (System.currentTimeMillis() - startTime) / 1000.0
                val edt = Easing.CubicInOut.easer.ease(dt, 0.0, 1.0, 1.0)
                setNormalizedScale(mix(cs, targetScaleNormalized, edt))
                yield()
            }
        }
    }

    fun setNormalizedScale(targetScaleNormalized: Double) {
        val targetScale = toExpScale(quantize(targetScaleNormalized.coerceIn(0.0, 1.0)))
        val currentScale = view.scale()
        val factor = targetScale / currentScale
        zoomPosition = program.drawer.bounds.center
        view = buildTransform {
            translate(zoomPosition)
            scale(factor)
            translate(-zoomPosition)
        } * view
        dirty = true
    }

    private var dirty = true
        set(value) {
            if (value && !field) {
                changed.trigger(Unit)
            }
            field = value
        }
    override val hasChanged: Boolean
        get() = dirty

    init {
        actionBounds = Rectangle(0.0, 0.0, bounds.width, bounds.height)

        buttonDown.listen {
            it.cancelPropagation()
        }

        dragged.listen {
            val viewCenter = Vector2(bounds.width / 2.0, bounds.height / 2.0)
            val worldCenter = Vector2(bounds.width / 2.0, bounds.height / 2.0)
            val currentWorld = (view.inversed * viewCenter.xy01).xy
            val currentDistance = worldCenter.distanceTo(currentWorld)

            var candidate = buildTransform {
                translate(it.dragDisplacement)
            } * view


            val candidateWorld = (candidate.inversed * viewCenter.xy01).xy
            val distance = worldCenter.distanceTo(candidateWorld)

            if (distance > 600 && distance >= currentDistance) {
                candidate = buildTransform {
                    translate(it.dragDisplacement * smoothstep(120.0, 0.0, distance - 600.0))
                } * view
            }

            val candidateWorld2 = (candidate.inversed * viewCenter.xy01).xy
            val distance2 = worldCenter.distanceTo(candidateWorld2)

            if (distance2 < 720.0) {
                view = candidate
            }
            dirty = true
        }


        scrolled.listen { mouse ->
            val scaleFactor = 1.0 - mouse.rotation.y * zoomSpeed
            zoomPosition = mouse.position

            view = buildTransform {
                translate(mouse.position)
                scale(scaleFactor)
                translate(-mouse.position)
            } * view
            dirty = true
        }
    }

    fun reset() {
        val sc = toNormalizedScale(1.0)
        setNormalizedScaleSlow(sc)
        val w = bounds.width
        val h = bounds.height
        val center = Vector2(w / 2.0, h / 2.0)
        centerAtSlow(center)
    }

    fun bounds(): Rectangle {
        val w = bounds.width
        val h = bounds.height

        val vi = view.inversed
        val x00 = (vi * Vector2.ZERO.xy01).xy
        val x11 = (vi * Vector2(w.toDouble(), h.toDouble()).xy01).xy
        return Rectangle(x00, x11.x - x00.x, x11.y - x00.y)
    }

    override fun beforeDraw(drawer: Drawer, program: Program) {
        drawer.pushTransforms()
        drawer.ortho()
        drawer.view = view
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        dirty = false
        drawer.popTransforms()
    }
}


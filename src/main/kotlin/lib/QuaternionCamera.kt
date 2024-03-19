package extensions

import org.openrndr.Extension
import org.openrndr.Program
import org.openrndr.animatable.Animatable
import org.openrndr.animatable.easing.Easing
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.events.Event
import org.openrndr.math.Quaternion
import org.openrndr.math.Vector2
import org.openrndr.math.mix
import org.openrndr.math.transforms.perspective
import kotlin.math.min

class QuaternionCamera : Extension {
    var orientation = Quaternion.IDENTITY

    var dragStart = Vector2(0.0, 0.0)


    val minTravel = 350.0
    var maxZoomOut = 90.0

    var zoomOutStarted: Event<Unit> = Event()
    var zoomInStarted: Event<Unit> = Event()
    var zoomInFinished: Event<Unit> = Event()

    var zoomLockStarted: Event<Unit> = Event()
    var zoomLockFinished: Event<Unit> = Event()

    var orientationChanged: Event<Quaternion> = Event()

    var buttonDown = false


    override fun setup(program: Program) {
        program.mouse.buttonDown.listen {
            if (!it.propagationCancelled) {
                buttonDown = true
                dragStart = it.position
            }
        }

        program.mouse.dragged.listen {
            if (!it.propagationCancelled) {
                if (!buttonDown) {
                    return@listen
                }

                orientation = Quaternion.fromAngles(
                    it.dragDisplacement.x,
                    it.dragDisplacement.y,
                    0.0
                ) * orientation
                orientationChanged.trigger(orientation)

            }
        }


        program.mouse.buttonUp.listen {
            if (!it.propagationCancelled) {
                buttonDown = false
            }
        }
    }

    override var enabled: Boolean = true

    override fun beforeDraw(drawer: Drawer, program: Program) {
        drawer.pushTransforms()
        val fov = (1.0 * 30.0 + 12.0).coerceAtMost(maxZoomOut)
        drawer.projection = perspective(fov, drawer.width * 1.0 / drawer.height * 1.0, 0.1, 150.0)
        drawer.view = orientation.matrix.matrix44
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        drawer.popTransforms()
    }
}
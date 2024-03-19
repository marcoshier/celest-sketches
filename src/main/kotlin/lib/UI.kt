package lib

import mu.KotlinLogging
import org.openrndr.*
import org.openrndr.color.ColorHSLa
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.draw.isolated
import org.openrndr.events.Event
import org.openrndr.extra.noise.uniform
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import kotlin.random.Random


interface UIElement: MouseEvents {
    val zOrder: Int
    val actionBounds: Rectangle
    val visible: Boolean
    val clicked: Event<MouseEvent>
    val doubleClicked: Event<MouseEvent>
}

open class UIElementImpl : UIElement {

    override var zOrder = 0
    override var actionBounds = Rectangle(0.0, 0.0, 100.0, 100.0)
    override var visible = true

    override val buttonDown: Event<MouseEvent> = Event("ui-element-button-down")
    override val buttonUp: Event<MouseEvent> = Event("ui-element-button-up")
    override val clicked: Event<MouseEvent> = Event("ui-element-clicked")
    override val doubleClicked: Event<MouseEvent> = Event("ui-element-double-clicked")

    override val dragged: Event<MouseEvent> = Event("ui-element-dragged")

    override val entered: Event<MouseEvent> = Event("ui-element-entered")
    override val exited: Event<MouseEvent> = Event("ui-element-exited")
    override val moved: Event<MouseEvent> = Event("ui-element-moved")

    override val position: Vector2
        get() = error("don't use 'position'")

    override val scrolled: Event<MouseEvent> = Event("ui-element-scrolled")
}

private val logger = KotlinLogging.logger {  }

class UIManager(val window: Window, mouseEvents: MouseEvents) {
    var activeElement: UIElement? = null
    var dragElement: UIElement? = null
    var postDragElement: UIElement? = null

    var lastMousePosition = Vector2(0.0, 0.0)
    private var velocity = Vector2(0.0, 0.0)
    private var potentialVelocity = Vector2(0.0, 0.0)

    var lastAction = System.currentTimeMillis()
    var lastClicked = 0L

    fun requestDraw() {
        lastAction = System.currentTimeMillis()
        window.requestDraw()
    }

    val postDragEnded = Event<Unit>()
    val clicked = Event<Unit>()
    val doubleClicked = Event<Unit>()

    var dragged = false

    init {
        mouseEvents.buttonDown.listen { event ->
            lastMousePosition = event.position
            if (!event.propagationCancelled) {
                for (e in elements.filter { it.visible && event.position in it.actionBounds }.sortedBy { it.zOrder }) {
                    e.buttonDown.trigger(event)
                    if (event.propagationCancelled) {
                        potentialVelocity = Vector2.ZERO
                        velocity = Vector2.ZERO
                        requestDraw()
                        activeElement = e
                        dragElement = null
                    }
                }
            }
        }

        mouseEvents.dragged.listen { event ->
            if (!event.propagationCancelled) {

                if (dragElement != null) {
                    potentialVelocity = Vector2.ZERO
                }


                if (System.currentTimeMillis() - lastClicked >= 300) {
                    dragElement = activeElement
                } else {
                    logger.info { "masking drag" }
                }

                if (dragElement != null) {
                    requestDraw()
                    potentialVelocity = event.position - lastMousePosition
                    lastMousePosition = event.position
                }

                dragElement?.dragged?.trigger(event)
            }
        }

        mouseEvents.buttonUp.listen { event ->
            if (!event.propagationCancelled) {
                lastMousePosition = event.position

                activeElement?.buttonUp?.trigger(event)
                if (activeElement != null && dragElement == null) {
                    if (System.currentTimeMillis() - lastClicked < 300) {
                        logger.info {
                            "double click dispatched to ${activeElement}, ${System.currentTimeMillis() - lastClicked}ms"
                        }
                        activeElement?.doubleClicked?.trigger(event)
                        doubleClicked.trigger(Unit)
                        lastClicked = 0L
                    } else {
                        logger.info {
                            "single click dispatched to ${activeElement}"
                        }
                        activeElement?.clicked?.trigger(event)
                        clicked.trigger(Unit)
                        lastClicked = System.currentTimeMillis()
                    }
                }

                if (dragElement != null) {
                    velocity = potentialVelocity
                    postDragElement = dragElement
                    dragElement = null
                }
            }
        }

        mouseEvents.scrolled.listen {
            if (!it.propagationCancelled) {
                activeElement?.scrolled?.trigger(it)
                if (activeElement != null) {
                    requestDraw()
                }
            }
        }
    }
    val elements = mutableListOf<UIElement>()

    fun update() {
        if (System.currentTimeMillis() - lastAction < 2000) {
            window.requestDraw()
        }

        velocity *= 0.9
        if (velocity.length < 0.05 && postDragElement != null) {
            velocity = Vector2.ZERO
            postDragElement = null
            postDragEnded.trigger(Unit)
        } else {
            if (postDragElement != null) {
                requestDraw()
                lastMousePosition += velocity
                postDragElement?.dragged?.trigger(MouseEvent(lastMousePosition, Vector2.ZERO, velocity, MouseEventType.DRAGGED, MouseButton.NONE, emptySet()))
            }
        }
    }

    fun drawDebugBoxes(drawer: Drawer) {
        drawer.isolated {
            drawer.defaults()
            drawer.fill = null
            for((i, e) in elements.filter { it.visible }.withIndex()) {
                val rect = e.actionBounds
                val c = ColorHSLa(0.5, 0.5, 0.5).shiftHue(360.0 * Double.uniform(0.0, 1.0, Random(i) )).toRGBa()

                drawer.stroke = c
                this.rectangle(rect)

                drawer.stroke = null
                drawer.fill = c.opacify(0.5)
                e::class.simpleName?.let { drawer.text(it, rect.center) }
            }

            if (dragElement != null) {
                drawer.fill = ColorRGBa.WHITE.opacify(0.2)
                drawer.rectangle(dragElement!!.actionBounds)
            }

            drawer.fill = ColorRGBa.PINK
            if(postDragElement != null) {
                postDragElement!!::class.simpleName?.let { drawer.text("last dragged: $it", 20.0, 20.0) }
            }
            if(activeElement != null) {
                activeElement!!::class.simpleName?.let { drawer.text("last clicked: $it", 20.0, 40.0) }
            }

            drawer.fill = ColorRGBa.RED
        }
    }
}


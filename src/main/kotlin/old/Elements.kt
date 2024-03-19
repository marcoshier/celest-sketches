package old

import org.openrndr.draw.shadeStyle
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.shapes.regularPolygon
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.*
import kotlin.random.Random

val arrowTriangle = regularPolygon(3, radius = 5.0, phase = 30.0)
val arrow = contour {
    val straight = arrowTriangle.segments.first { it.isStraight() }
    val skew = arrowTriangle.segments - straight
    segment(skew[1])
    lineTo(Vector2(skew[1].start.x, skew[1].start.y + 4.0))
    lineTo(skew[0].start)
    close()
}
val circle = Circle(Vector2.ZERO, 4.0).contour
val square = Rectangle(Vector2.ZERO, 6.0).contour
val rectangle = Rectangle(Vector2.ZERO, 2.0, 7.0).contour.transform(buildTransform { rotate(Double.uniform(0.0, 90.0)) })

fun ShapeContour.withArrow(): ShapeContour {
    return contour {
        for (s in segments) segment(s)
        moveTo(arrow.segments[0].start)
        for (s in arrow.segments) segment(s)
    }
}

val rect = Rectangle(0.0, 3.0, 2.0, 10.0)


fun dashed(interval: Double = 20.0, phase: Double = 0.0) = shadeStyle {
        fragmentTransform = "x_stroke.a *= sign(sin(c_contourPosition * p_interval + p_phase)) * 0.5 + 0.5;"
        parameter("interval", interval)
        parameter("phase", phase)
    }


fun dashFill(interval: Double = 20.0) = shadeStyle {
        fragmentTransform = "x_fill.a *= sign(sin(c_boundsPosition.x * p_interval)) * 0.5 + 0.5;"
        parameter("interval", interval)
    }



fun randomElement(rnd: Random = Random.Default): ShapeContour {
    return listOf(arrow, arrowTriangle, circle, square, rectangle).random(random = rnd)
}

fun randomBase(rnd: Random = Random.Default): ShapeContour {
    return listOf(arrowTriangle, circle, square, rectangle).random(random = rnd)
}

/*
val arrowTriangle = regularPolygon(3, radius = 5.0)
val arrowTriangle = regularPolygon(3, radius = 5.0)
val arrowTriangle = regularPolygon(3, radius = 5.0)
val arrowTriangle = regularPolygon(3, radius = 5.0)
val arrowTriangle = regularPolygon(3, radius = 5.0)
val arrowTriangle = regularPolygon(3, radius = 5.0)
val arrowTriangle = regularPolygon(3, radius = 5.0)*/

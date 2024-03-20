import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.fx.distort.PolarToRectangular
import org.openrndr.extra.fx.distort.RectangularToPolar
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.bezierPatch
import org.openrndr.math.Polar
import org.openrndr.shape.Circle
import org.openrndr.svg.loadSVG
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1080
        height = 1080
        multisample = WindowMultisample.SampleCount(32)
    }
    oliveProgram {

        extend(Screenshots())

        val r = 500.0
        val density = 240

        val circle = Circle(drawer.bounds.center, r)
        val bezierPatch = bezierPatch(circle.contour)

        val tex = loadImage("data/output2.jpg")
        PolarToRectangular().apply {
            logPolar = true
        }.apply(tex, tex)

        extend {

            drawer.circle(circle)

            drawer.strokeWeight = 0.5

            val lightPosition = Polar(seconds * 0.2 * r + Double.uniform(0.0, 360.0, Random(1)), r).cartesian + drawer.bounds.center

            drawer.shadeStyle = shadeStyle {
                fragmentPreamble = """
                        #define circleSize ${r * 2.0};
                    """.trimIndent()
                fragmentTransform = """
                            vec2 contourPos = c_screenPosition.xy;
                            vec2 boundsUV = (c_screenPosition.xy - p_corner) / circleSize;
                            
                            vec4 imgColor = texture(p_tex, boundsUV);
                            imgColor = smoothstep(0.3, 0.66, imgColor);
                           
                            float d = distance(contourPos.xy, p_pos) / circleSize;
                            float l = smoothstep(0.2, 0.3, d);
                            float l2 = 1.0 - smoothstep(0.6, 0.4, d);
                            x_stroke = l * l2 * imgColor; //
                        """.trimIndent()
                parameter("pos", lightPosition)
                parameter("tex", tex)
                parameter("corner", circle.corner)
            }

            for (j in 0..density) {
                drawer.contour(bezierPatch.horizontal(j / density.toDouble()))
            }


            drawer.shadeStyle = null

            drawer.shadeStyle = shadeStyle {
                fragmentPreamble = """
                        #define circleSize ${r * 2.0};
                        
                        float luminosity(vec3 rgb) {
                            return 0.2126 * rgb.r + 0.7152 * rgb.g + 0.0722 * rgb.b;
                        }
                    """.trimIndent()
                fragmentTransform = """
                            vec2 contourPos = c_screenPosition.xy;
                            vec2 boundsUV = (c_screenPosition.xy - p_corner) / circleSize;
                            
                            vec4 imgColor = texture(p_tex, boundsUV);
                            
                            if(luminosity(imgColor.xyz) > 0.0) {
                                imgColor = smoothstep(0.3, 0.31, imgColor);
                           
                                float d = distance(contourPos.xy, p_pos) / circleSize;
                                float l = smoothstep(0.5, 0.6, d);
                                x_stroke = imgColor; // l * 
                            } else {
                                x_stroke = vec4(0.0); // l * 
                            }
                            
                            
                        """.trimIndent()
                parameter("pos", lightPosition)
                parameter("tex", tex)
                parameter("corner", circle.corner)
            }

            for (j in 0..density) {
                drawer.contour(bezierPatch.vertical(j / density.toDouble()))
            }
            drawer.shadeStyle = null
        }
    }
}

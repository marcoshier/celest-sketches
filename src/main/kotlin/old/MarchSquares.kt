package old

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.draw.shadeStyle
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.marchingsquares.findContours
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.hobbyCurve
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.LineSegment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

fun main() = application {
    configure {
        width = 720
        height = 720
    }
    oliveProgram {

        val rt = renderTarget(width, height) {
            colorBuffer()
            depthBuffer()
        }


        fun f(v: Vector2): Double {
            val iv = v.toInt()
            val d = if (iv.x >= 0 && iv.y >= 0 && iv.x < width && iv.y < height) rt.colorBuffer(0).shadow[iv.x, iv.y].luminance else 0.0
            return cos(d * PI * 3.0)
        }

        var startingPosition = Vector2.ZERO
        var delta = Vector2.ZERO

        mouse.buttonDown.listen {
            startingPosition = it.position
        }

        mouse.dragged.listen {
            delta = startingPosition - it.position
        }

        mouse.buttonUp.listen {
            startingPosition = it.position + delta
        }

        extend(Screenshots())

        extend {

            drawer.isolatedWithTarget(rt) {
                drawer.shadeStyle = shadeStyle {

                    fragmentPreamble = perlin_noise
                    fragmentTransform = """
                    vec2 pos = c_boundsPosition.xy * c_boundsSize.xy;		
	                float c, n;

	                n = perlin_noise((pos - p_offset) * 0.25);
	                c = abs(cos(n*10.0));

	                x_fill = vec4(c, c, c, 1.0);
                """.trimIndent()
                    parameter("t", seconds)
                    parameter("offset", -delta)
                }

                drawer.rectangle(drawer.bounds)
            }

            rt.colorBuffer(0).shadow.download()

            val contours = findContours(::f, drawer.bounds.offsetEdges(32.0), 12.0)


            drawer.fill = null
            drawer.clear(ColorRGBa.GRAY.shade(1.5))
            drawer.stroke = ColorRGBa.BLACK

            for (c in contours) {
                if (c.bounds.area < 100000.0 && c.bounds.area > 100.0) {
                    drawer.fill = ColorRGBa.BLACK
                    drawer.shadeStyle = dashFill(220.0)
                } else {
                    drawer.fill = null
                }
                drawer.contour(c.close())
                drawer.shadeStyle = null
            }

            drawer.fill = ColorRGBa.BLACK

            val centers = contours.shuffled(Random(0)).take(100).map { it.bounds.center }

            centers.mapIndexed { i, it ->
                drawer.contour(randomElement(rnd = Random(i)).transform(buildTransform { translate(it) }))
                val r = Double.uniform(0.0, 1.0, Random(i))
                if (r > 0.5) {
                    drawer.text((0..2).map { j ->  Character.toString(Int.uniform(65, 125, Random(i + j))) }.joinToString("") , it + Vector2(10.0, 0.0))
                }
            }

            centers.take(12).chunked(2).forEachIndexed { i, it ->
                val r = Double.uniform(0.0, 1.0, Random(i))
                when(r) {
                    in 0.0..0.33 -> drawer.shadeStyle = dashed(0.05)
                    in 0.34..0.66 -> drawer.shadeStyle = dashed(0.5)
                }
                drawer.contour(LineSegment(it[0], it[1]).contour)
                drawer.shadeStyle = null
            }

            centers.take(12).chunked(3).forEachIndexed { i, it ->
                val r = Double.uniform(0.0, 1.0, Random(i))
                when(r) {
                    in 0.0..0.33 -> drawer.shadeStyle = dashed(5.0)
                    in 0.34..0.66 -> drawer.shadeStyle = dashed(0.5)
                }
                drawer.contour(hobbyCurve(it))
                drawer.shadeStyle = null
            }




        }
    }
}

val perlin_noise = """
    // noise
    float noise(vec2 pos)
    {
    	return fract( sin( dot(pos*0.001 ,vec2(24.12357, 36.789) ) ) * 12345.123);	
    }


    // blur noise
    float smooth_noise(vec2 pos)
    {
    	return   ( noise(pos + vec2(1,1)) + noise(pos + vec2(1,1)) + noise(pos + vec2(1,1)) + noise(pos + vec2(1,1)) ) / 16.0 		
    		   + ( noise(pos + vec2(1,0)) + noise(pos + vec2(-1,0)) + noise(pos + vec2(0,1)) + noise(pos + vec2(0,-1)) ) / 8.0 		
        	   + noise(pos) / 4.0;
    }


    // linear interpolation
    float interpolate_noise(vec2 pos)
    {
    	float	a, b, c, d;
    	
    	a = smooth_noise(floor(pos));	
    	b = smooth_noise(vec2(floor(pos.x+1.0), floor(pos.y)));
    	c = smooth_noise(vec2(floor(pos.x), floor(pos.y+1.0)));
    	d = smooth_noise(vec2(floor(pos.x+1.0), floor(pos.y+1.0)));
    		
    	a = mix(a, b, fract(pos.x));
    	b = mix(c, d, fract(pos.x));
    	a = mix(a, b, fract(pos.y));
    	
    	return a;				   	
    }



    float perlin_noise(vec2 pos)
    {
    	float	n;
    	
    	n = interpolate_noise(pos*0.0625)*0.5;
    	n += interpolate_noise(pos*0.125)*0.25;
    	n += interpolate_noise(pos*0.025)*0.225;
    	n += interpolate_noise(pos*0.05)*0.0625;
    	n += interpolate_noise(pos)*0.03125;
    	return n;
    }
""".trimIndent()
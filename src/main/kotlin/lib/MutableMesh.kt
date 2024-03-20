package lib

import org.openrndr.draw.VertexBuffer
import org.openrndr.math.Matrix44
import org.openrndr.math.Spherical
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import kotlin.math.abs

class MutableMesh {
    var positions = mutableListOf<Vector3>()
    var uvs = mutableListOf<Vector2>()
    var normals = mutableListOf<Vector3>()
}

// lifted from https://github.com/mrdoob/three.js/blob/master/examples/jsm/geometries/DecalGeometry.js
fun MutableMesh.decal(
    projectorMatrix: Matrix44,
    size: Vector3
): MutableMesh {
    val projectorMatrixInverse = projectorMatrix.inversed

    val dpositions = positions.map { (projectorMatrixInverse * (it.xyz1)).div }.toMutableList()
    val dnormals = normals.map { it }.toMutableList()
    var decalVertices = dpositions.zip(dnormals)

    decalVertices = clipGeometry(decalVertices, size, Vector3(1.0, 0.0, 0.0))
    decalVertices = clipGeometry(decalVertices, size, Vector3(-1.0, 0.0, 0.0))
    decalVertices = clipGeometry(decalVertices, size, Vector3(0.0, 1.0, 0.0))
    decalVertices = clipGeometry(decalVertices, size, Vector3(0.0, -1.0, 0.0))
    decalVertices = clipGeometry(decalVertices, size, Vector3(0.0, 0.0, 1.0))
    decalVertices = clipGeometry(decalVertices, size, Vector3(0.0, 0.0, -1.0))

    val decalMesh = MutableMesh()
    for (v in decalVertices) {
        decalMesh.positions.add((projectorMatrix * v.first.xyz1).div)
        decalMesh.normals.add(v.second)
        decalMesh.uvs.add(v.first.xy / size.xy + Vector2(0.5))
    }
    return decalMesh
}

fun clipGeometry(
    inVertices: List<Pair<Vector3, Vector3>>,
    size: Vector3,
    plane: Vector3
): MutableList<Pair<Vector3, Vector3>> {
    val outVertices: MutableList<Pair<Vector3, Vector3>> = mutableListOf()
    val s = 0.5 * abs(size.dot(plane));

    fun clip(
        v0: Pair<Vector3, Vector3>,
        v1: Pair<Vector3, Vector3>, p: Vector3, s: Double
    ): Pair<Vector3, Vector3> {

        val d0 = v0.first.dot(p) - s;
        val d1 = v1.first.dot(p) - s;

        val s0 = d0 / (d0 - d1);

        val v = Pair(
            Vector3(
                v0.first.x + s0 * (v1.first.x - v0.first.x),
                v0.first.y + s0 * (v1.first.y - v0.first.y),
                v0.first.z + s0 * (v1.first.z - v0.first.z)
            ),
            Vector3(
                v0.second.x + s0 * (v1.second.x - v0.second.x),
                v0.second.y + s0 * (v1.second.y - v0.second.y),
                v0.second.z + s0 * (v1.second.z - v0.second.z)
            )
        );

        // need to clip more values (texture coordinates)? do it this way:
        // intersectpoint.value = a.value + s * ( b.value - a.value );
        return v
    }

    for (i in inVertices.indices step 3) {
        var total = 0

        val d1 = inVertices[i + 0].first.dot(plane) - s
        val d2 = inVertices[i + 1].first.dot(plane) - s
        val d3 = inVertices[i + 2].first.dot(plane) - s

        val v1Out = d1 > 0
        val v2Out = d2 > 0
        val v3Out = d3 > 0

        total = (if (v1Out) 1 else 0) + (if (v2Out) 1 else 0) + (if (v3Out) 1 else 0)

        when (total) {
            0 -> {
                outVertices.add(inVertices[i])
                outVertices.add(inVertices[i + 1])
                outVertices.add(inVertices[i + 2])
            }

            1 -> {
                if (v1Out) {
                    val nV1 = inVertices[i + 1]
                    val nV2 = inVertices[i + 2]
                    val nV3 = clip(inVertices[i], nV1, plane, s)
                    val nV4 = clip(inVertices[i], nV2, plane, s)

                    outVertices.add(nV1);
                    outVertices.add(nV2);
                    outVertices.add(nV3);

                    outVertices.add(nV4);
                    outVertices.add(nV3);
                    outVertices.add(nV2);
                }

                if (v2Out) {
                    val nV1 = inVertices[i];
                    val nV2 = inVertices[i + 2];
                    val nV3 = clip(inVertices[i + 1], nV1, plane, s);
                    val nV4 = clip(inVertices[i + 1], nV2, plane, s);

                    outVertices.add(nV3);
                    outVertices.add(nV2);
                    outVertices.add(nV1);

                    outVertices.add(nV2);
                    outVertices.add(nV3);
                    outVertices.add(nV4);
                }

                if (v3Out) {
                    val nV1 = inVertices[i]
                    val nV2 = inVertices[i + 1]
                    val nV3 = clip(inVertices[i + 2], nV1, plane, s)
                    val nV4 = clip(inVertices[i + 2], nV2, plane, s)

                    outVertices.add(nV1);
                    outVertices.add(nV2);
                    outVertices.add(nV3);

                    outVertices.add(nV4);
                    outVertices.add(nV3);
                    outVertices.add(nV2);
                }
            }

            2 -> {
                if (!v1Out) {
                    val nV1 = inVertices[i]
                    val nV2 = clip(nV1, inVertices[i + 1], plane, s);
                    val nV3 = clip(nV1, inVertices[i + 2], plane, s);
                    outVertices.add(nV1);
                    outVertices.add(nV2);
                    outVertices.add(nV3);
                }

                if (!v2Out) {
                    val nV1 = inVertices[i + 1]
                    val nV2 = clip(nV1, inVertices[i + 2], plane, s);
                    val nV3 = clip(nV1, inVertices[i], plane, s);
                    outVertices.add(nV1);
                    outVertices.add(nV2);
                    outVertices.add(nV3);
                }

                if (!v3Out) {
                    val nV1 = inVertices[i + 2]
                    val nV2 = clip(nV1, inVertices[i], plane, s);
                    val nV3 = clip(nV1, inVertices[i + 1], plane, s);
                    outVertices.add(nV1);
                    outVertices.add(nV2);
                    outVertices.add(nV3);
                }
            }

            else -> {
            }
        }
    }
    return outVertices
}

// lifted from orx-mesh-generators
fun generateSphere(
    sides: Int,
    segments: Int,
    radius: Double = 1.0,
    flipNormals: Boolean = false,
    mesh: MutableMesh
) {
    fun writer(position: Vector3, normal: Vector3, uv: Vector2) {
        mesh.positions.add(position)
        mesh.normals.add(normal)
        mesh.uvs.add(uv)
    }

    val invertFactor = if (flipNormals) -1.0 else 1.0
    for (t in 0 until segments) {
        for (s in 0 until sides) {
            val st00 = Spherical(s * 180.0 * 2.0 / sides, t * 180.0 / segments, radius)
            val st01 = Spherical(s * 180.0 * 2.0 / sides, (t + 1) * 180.0 / segments, radius)
            val st10 = Spherical((s + 1) * 180.0 * 2.0 / sides, t * 180.0 / segments, radius)
            val st11 = Spherical((s + 1) * 180.0 * 2.0 / sides, (t + 1) * 180.0 / segments, radius)

            val thetaMax = 180.0 * 2.0
            val phiMax = 180.0

            when (t) {
                0 -> {
                    writer(st00.cartesian, st00.cartesian.normalized * invertFactor, Vector2(st00.theta / thetaMax + 0.5, 1.0 - st00.phi / phiMax))
                    writer(st01.cartesian, st01.cartesian.normalized * invertFactor, Vector2(st01.theta / thetaMax + 0.5, 1.0 - st01.phi / phiMax))
                    writer(st11.cartesian, st11.cartesian.normalized * invertFactor, Vector2(st11.theta / thetaMax + 0.5, 1.0 - st11.phi / phiMax))
                }
                segments - 1 -> {
                    writer(st11.cartesian, st11.cartesian.normalized * invertFactor, Vector2(st11.theta / thetaMax + 0.5, 1.0 - st11.phi / phiMax))
                    writer(st10.cartesian, st10.cartesian.normalized * invertFactor, Vector2(st10.theta / thetaMax + 0.5, 1.0 - st10.phi / phiMax))
                    writer(st00.cartesian, st00.cartesian.normalized * invertFactor, Vector2(st00.theta / thetaMax + 0.5, 1.0 - st00.phi / phiMax))
                }
                else -> {
                    writer(st00.cartesian, st00.cartesian.normalized * invertFactor, Vector2(st00.theta / thetaMax + 0.5, 1.0 - st00.phi / phiMax))
                    writer(st01.cartesian, st01.cartesian.normalized * invertFactor, Vector2(st01.theta / thetaMax + 0.5, 1.0 - st01.phi / phiMax))
                    writer(st11.cartesian, st11.cartesian.normalized * invertFactor, Vector2(st11.theta / thetaMax + 0.5, 1.0 - st11.phi / phiMax))

                    writer(st11.cartesian, st11.cartesian.normalized * invertFactor, Vector2(st11.theta / thetaMax + 0.5, 1.0 - st11.phi / phiMax))
                    writer(st10.cartesian, st10.cartesian.normalized * invertFactor, Vector2(st10.theta / thetaMax + 0.5, 1.0 - st10.phi / phiMax))
                    writer(st00.cartesian, st00.cartesian.normalized * invertFactor, Vector2(st00.theta / thetaMax + 0.5, 1.0 - st00.phi / phiMax))
                }
            }
        }
    }
}

fun MutableMesh.writeToVertexBuffer(vb: VertexBuffer) {
    vb.put {
        for (i in 0 until positions.size) {
            write(positions[i])
            write(normals[i])
            write(uvs[i])
        }
    }
}
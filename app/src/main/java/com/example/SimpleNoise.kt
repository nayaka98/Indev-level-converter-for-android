package com.example

import kotlin.math.floor

class SimpleNoise(seed: Long) {
    private val p = IntArray(512)
    init {
        val random = java.util.Random(seed)
        val permutation = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = permutation[i]
            permutation[i] = permutation[j]
            permutation[j] = temp
        }
        for (i in 0 until 512) {
            p[i] = permutation[i % 256]
        }
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(t: Double, a: Double, b: Double) = a + t * (b - a)
    private fun grad(hash: Int, x: Double, y: Double): Double {
        val h = hash and 3
        val u = if (h < 2) x else y
        val v = if (h < 2) y else x
        return (if (h and 1 == 0) u else -u) + (if (h and 2 == 0) v else -v)
    }

    fun noise(x: Double, y: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)

        val u = fade(xf)
        val v = fade(yf)

        val a = p[xi] + yi
        val b = p[xi + 1] + yi

        return lerp(
            v,
            lerp(u, grad(p[a], xf, yf), grad(p[b], xf - 1, yf)),
            lerp(u, grad(p[a + 1], xf, yf - 1), grad(p[b + 1], xf - 1, yf - 1))
        )
    }
}

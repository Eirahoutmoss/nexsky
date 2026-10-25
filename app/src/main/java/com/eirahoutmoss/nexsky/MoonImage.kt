package com.eirahoutmoss.nexsky

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.libration
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A photographic Moon: NASA's LRO colour map (SVS "CGI Moon Kit", public
 * domain) wrapped onto a sphere as seen from Earth — with the night's
 * libration — and lit by the Sun for the given phase.
 */
object MoonImage {
    private const val SIZE = 384
    private var tex: IntArray? = null
    private var texW = 0; private var texH = 0

    /** Earth's selenographic latitude/longitude now (libration), degrees. */
    @Volatile var libLat = 0.0; private set
    @Volatile var libLon = 0.0; private set

    // Unlit disc (colour + surface normal) for one libration.
    private var baseKey = ""
    private var baseColor = IntArray(0)
    private var baseNx = FloatArray(0); private var baseNy = FloatArray(0); private var baseNz = FloatArray(0)
    private val cache = LinkedHashMap<String, ImageBitmap>()

    val available get() = tex != null

    @Synchronized
    fun load(ctx: Context) {
        if (tex != null) return
        val bmp = try { ctx.assets.open("moon_map.jpg").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } ?: return
        texW = bmp.width; texH = bmp.height
        tex = IntArray(texW * texH).also { bmp.getPixels(it, 0, texW, 0, 0, texW, texH) }
        bmp.recycle()
    }

    fun setTime(t: Time) {
        try {
            val l = libration(t)
            libLat = l.elat; libLon = l.elon
        } catch (_: Exception) {}
    }

    private fun buildBase(lat0: Double, lon0: Double) {
        val t = tex ?: return
        val n = SIZE * SIZE
        val col = IntArray(n); val nx = FloatArray(n); val ny = FloatArray(n); val nz = FloatArray(n)
        val cp = cos(Math.toRadians(lat0)); val sp = sin(Math.toRadians(lat0))
        val half = SIZE / 2.0
        for (j in 0 until SIZE) {
            val y = (half - j - 0.5) / half           // up = lunar north
            for (i in 0 until SIZE) {
                val x = (i + 0.5 - half) / half       // right = lunar east (Mare Crisium side)
                val rr = x * x + y * y
                val k = j * SIZE + i
                if (rr > 1.0) { col[k] = 0; continue }
                val z = sqrt(1.0 - rr)
                // Tilt by the sub-Earth latitude, then shift by the sub-Earth longitude.
                val y2 = y * cp + z * sp
                val z2 = -y * sp + z * cp
                val lat = asin(y2.coerceIn(-1.0, 1.0))
                val lon = atan2(x, z2) + Math.toRadians(lon0)
                var u = ((Math.toDegrees(lon) + 180.0) / 360.0) % 1.0
                if (u < 0) u += 1.0
                val v = (90.0 - Math.toDegrees(lat)) / 180.0
                val tx = (u * texW).toInt().coerceIn(0, texW - 1)
                val ty = (v * texH).toInt().coerceIn(0, texH - 1)
                col[k] = t[ty * texW + tx]
                nx[k] = x.toFloat(); ny[k] = y.toFloat(); nz[k] = z.toFloat()
            }
        }
        baseColor = col; baseNx = nx; baseNy = ny; baseNz = nz
    }

    /**
     * [phaseDeg]: Moon–Sun elongation in ecliptic longitude (0 new, 90 first
     * quarter, 180 full, 270 last quarter). North up, waxing lit on the right.
     */
    @Synchronized
    fun bitmap(phaseDeg: Double): ImageBitmap? {
        if (tex == null) return null
        val lat0 = (libLat * 2).roundToInt() / 2.0; val lon0 = (libLon * 2).roundToInt() / 2.0
        val bk = "$lat0/$lon0"
        if (bk != baseKey) { buildBase(lat0, lon0); baseKey = bk; cache.clear() }
        val ph = ((phaseDeg % 360) + 360) % 360
        val key = "$bk/${ph.roundToInt()}"
        cache[key]?.let { return it }
        val e = Math.toRadians(ph.roundToInt().toDouble())
        val sx = sin(e).toFloat(); val sz = (-cos(e)).toFloat()
        val out = IntArray(SIZE * SIZE)
        for (k in out.indices) {
            val c = baseColor[k]
            if (c == 0) continue
            val d = baseNx[k] * sx + baseNz[k] * sz
            // Soft terminator; a faint earthshine keeps the dark side visible.
            val lit = (d * 6f + 0.5f).coerceIn(0f, 1f)
            val f = 0.05f + 0.95f * lit
            val r = (((c shr 16) and 0xFF) * f).toInt(); val g = (((c shr 8) and 0xFF) * f).toInt(); val b = ((c and 0xFF) * f).toInt()
            out[k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache[key] = bmp
        while (cache.size > 24) cache.remove(cache.keys.first())
        return bmp
    }
}

package com.eirahoutmoss.nexsky

import android.content.Context
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.RotationMatrix
import io.github.cosinekitty.astronomy.Spherical
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.constellation
import io.github.cosinekitty.astronomy.defineStar
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.geoMoon
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase
import io.github.cosinekitty.astronomy.rotationEqjHor
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchHourAngle
import io.github.cosinekitty.astronomy.searchMoonQuarter
import io.github.cosinekitty.astronomy.searchRiseSet
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

const val AU_KM = 149_597_870.7

enum class ObjType { SUN, MOON, PLANET, STAR, DSO, CONSTELLATION }

/** A searchable sky object. Catalog objects carry J2000 RA/Dec in degrees. */
data class SkyObject(
    val id: String,
    val name: String,
    val kindLabel: String,
    val type: ObjType,
    val raDeg: Double = 0.0,
    val decDeg: Double = 0.0,
    val mag: Double? = null,
    val sizeArcmin: DoubleArray = doubleArrayOf(0.0, 0.0),
    val aliases: List<String> = emptyList(),
    val body: Body? = null,
) {
    val searchKey: String = norm((listOf(name, id) + aliases).joinToString(" "))
}

/** Where an object is right now for the observer. */
data class SkyPos(
    val azDeg: Double,
    val altDeg: Double,          // with atmospheric refraction
    val raHours: Double,         // of date
    val decDeg: Double,
    val distAu: Double?,         // solar-system bodies only
    val mag: Double?,
    val diameterArcmin: Double?, // apparent size
)

data class RiseSet(val rise: Time?, val transit: Time?, val transitAlt: Double?, val set: Time?, val alwaysUp: Boolean, val neverUp: Boolean)

/** Lowercase, Turkish-aware, without diacritics: "Jüpiter" ~ "jupiter", "Ülker" ~ "ulker". */
fun norm(s: String): String {
    val lower = s.replace('I', 'ı').replace('İ', 'i').lowercase()
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ç', 'c').replace('ö', 'o').replace('ü', 'u')
    return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}

fun timeOf(millis: Long): Time = Time.fromMillisecondsSince1970(millis)
fun Time.millis(): Long = toMillisecondsSince1970()

class Constellation(val id: String, val name: String, val latin: String, val labelRa: Double, val labelDec: Double, val lines: List<DoubleArray>)

/** A J2000 unit vector, prepared once for fast per-frame rotation. */
class StarPoint(val x: Double, val y: Double, val z: Double, val mag: Float, val bv: Float)

object SkyCatalog {
    lateinit var objects: List<SkyObject>; private set
    lateinit var stars: List<StarPoint>; private set
    lateinit var constellations: List<Constellation>; private set
    private var loaded = false

    val solarSystem = listOf(
        SkyObject("sun", "Güneş", "Yıldız", ObjType.SUN, aliases = listOf("sun", "gunes"), body = Body.Sun),
        SkyObject("moon", "Ay", "Doğal uydu", ObjType.MOON, aliases = listOf("moon", "luna", "dolunay", "hilal"), body = Body.Moon),
        SkyObject("mercury", "Merkür", "Gezegen", ObjType.PLANET, aliases = listOf("mercury", "utarit"), body = Body.Mercury),
        SkyObject("venus", "Venüs", "Gezegen", ObjType.PLANET, aliases = listOf("venus", "coban yildizi", "zuhre", "sabah yildizi", "aksam yildizi"), body = Body.Venus),
        SkyObject("mars", "Mars", "Gezegen", ObjType.PLANET, aliases = listOf("merih", "kizil gezegen"), body = Body.Mars),
        SkyObject("jupiter", "Jüpiter", "Gezegen", ObjType.PLANET, aliases = listOf("jupiter", "musteri"), body = Body.Jupiter),
        SkyObject("saturn", "Satürn", "Gezegen", ObjType.PLANET, aliases = listOf("saturn", "zuhal"), body = Body.Saturn),
        SkyObject("uranus", "Uranüs", "Gezegen", ObjType.PLANET, aliases = listOf("uranus"), body = Body.Uranus),
        SkyObject("neptune", "Neptün", "Gezegen", ObjType.PLANET, aliases = listOf("neptune"), body = Body.Neptune),
    )

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val arr = JSONArray(ctx.assets.open("objects.json").bufferedReader().readText())
        val list = ArrayList<SkyObject>(arr.length() + 100)
        list += solarSystem
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val size = o.optJSONArray("size")
            val al = o.optJSONArray("alias")
            list += SkyObject(
                id = o.getString("id"), name = o.getString("name"), kindLabel = o.getString("kind"),
                type = if (o.getString("kind") == "Yıldız") ObjType.STAR else ObjType.DSO,
                raDeg = o.getDouble("ra"), decDeg = o.getDouble("dec"),
                mag = if (o.isNull("mag")) null else o.getDouble("mag"),
                sizeArcmin = doubleArrayOf(size?.optDouble(0, 0.0) ?: 0.0, size?.optDouble(1, 0.0) ?: 0.0),
                aliases = (0 until (al?.length() ?: 0)).map { al!!.getString(it) },
            )
        }
        val ca = JSONArray(ctx.assets.open("constellations.json").bufferedReader().readText())
        val cons = ArrayList<Constellation>()
        for (i in 0 until ca.length()) {
            val o = ca.getJSONObject(i)
            val lab = o.getJSONArray("label")
            val ls = o.getJSONArray("lines")
            val lines = (0 until ls.length()).map { j ->
                val seg = ls.getJSONArray(j); DoubleArray(seg.length()) { k -> seg.getDouble(k) }
            }
            cons += Constellation(o.getString("id"), o.getString("name"), o.getString("latin"), lab.getDouble(0), lab.getDouble(1), lines)
            list += SkyObject(
                o.getString("id"), o.getString("name"), "Takımyıldız", ObjType.CONSTELLATION,
                lab.getDouble(0), lab.getDouble(1), aliases = listOf(o.getString("latin")),
            )
        }
        val bytes = ctx.assets.open("stars.bin").readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.int
        val st = ArrayList<StarPoint>(n)
        repeat(n) {
            val ra = Math.toRadians(bb.float.toDouble()); val dec = Math.toRadians(bb.float.toDouble())
            val mag = bb.float; val bv = bb.float
            st += StarPoint(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec), mag, bv)
        }
        objects = list; stars = st; constellations = cons; loaded = true
    }

    fun search(q: String, limit: Int = 40): List<SkyObject> {
        val k = norm(q.trim())
        if (k.isEmpty()) return emptyList()
        val tokens = k.split(' ').filter { it.isNotBlank() }
        return objects.asSequence()
            .filter { o -> tokens.all { o.searchKey.contains(it) } }
            .sortedWith(compareBy<SkyObject>(
                { if (norm(it.name).startsWith(k)) 0 else 1 },
                { it.type.ordinal },
                { it.mag ?: 99.0 },
            ))
            .take(limit).toList()
    }

    fun byId(id: String) = objects.firstOrNull { it.id == id }
}

/** Astronomy for one observer, built on Astronomy Engine (MIT, Don Cross). */
object Sky {
    fun observer(lat: Double, lon: Double, heightM: Double = 0.0) = Observer(lat, lon, heightM)

    /** Rotation J2000 → horizontal (x north, y west, z up) for [time]; reuse for many stars. */
    fun eqjToHor(time: Time, obs: Observer): RotationMatrix = rotationEqjHor(time, obs)

    fun position(o: SkyObject, time: Time, obs: Observer): SkyPos {
        val b = o.body
        if (b != null) {
            val eq = equator(b, time, obs, EquatorEpoch.OfDate, Aberration.Corrected)
            val hor = horizon(time, obs, eq.ra, eq.dec, Refraction.Normal)
            val mag = try { illumination(b, time).mag } catch (_: Exception) { null }
            val radiusKm = when (b) {
                Body.Sun -> 695_700.0; Body.Moon -> 1_737.4; Body.Mercury -> 2_439.7; Body.Venus -> 6_051.8
                Body.Mars -> 3_389.5; Body.Jupiter -> 69_911.0; Body.Saturn -> 58_232.0; Body.Uranus -> 25_362.0
                Body.Neptune -> 24_622.0; else -> 0.0
            }
            val distKm = eq.dist * AU_KM
            val diam = if (radiusKm > 0) Math.toDegrees(2 * atan(radiusKm / distKm)) * 60 else null
            return SkyPos(hor.azimuth, hor.altitude, eq.ra, eq.dec, eq.dist, mag, diam)
        }
        val v = Spherical(o.decDeg, o.raDeg, 1.0).toVector(time)
        val h = rotationEqjHor(time, obs).rotate(v).toHorizontal(Refraction.Normal)
        val size = o.sizeArcmin[0].takeIf { it > 0 }
        return SkyPos(h.lon, h.lat, o.raDeg / 15.0, o.decDeg, null, o.mag, size)
    }

    private fun bodyFor(o: SkyObject): Body {
        o.body?.let { return it }
        defineStar(Body.Star1, (o.raDeg / 15.0).coerceIn(0.0, 23.99999), o.decDeg, 1000.0)
        return Body.Star1
    }

    /** Next rise / culmination / set within ~1 day after [start]. */
    @Synchronized
    fun riseSet(o: SkyObject, start: Time, obs: Observer): RiseSet {
        val b = bodyFor(o)
        val rise = try { searchRiseSet(b, obs, Direction.Rise, start, 1.0) } catch (_: Exception) { null }
        val set = try { searchRiseSet(b, obs, Direction.Set, start, 1.0) } catch (_: Exception) { null }
        val tr = try { searchHourAngle(b, obs, 0.0, start) } catch (_: Exception) { null }
        var always = false; var never = false
        if (rise == null && set == null) {
            val p = position(o, start, obs)
            if (p.altDeg > 0) always = true else never = true
        }
        return RiseSet(rise, tr?.time, tr?.hor?.altitude, set, always, never)
    }

    /** Sun crossing [altitude] (degrees) in [dir] within a day after [start]. */
    fun sunAltitude(dir: Direction, start: Time, obs: Observer, altitude: Double): Time? =
        try { searchAltitude(Body.Sun, obs, dir, start, 1.0, altitude) } catch (_: Exception) { null }

    fun constellationName(raHours: Double, decDeg: Double): String {
        val c = try { constellation(raHours, decDeg) } catch (_: Exception) { return "" }
        return SkyCatalog.constellations.firstOrNull { it.id == c.symbol }?.name ?: c.name
    }
}

/** Moon facts for a moment. */
data class MoonInfo(
    val phaseDeg: Double,        // 0 new, 90 first quarter, 180 full, 270 last quarter
    val illuminated: Double,     // 0..1
    val distanceKm: Double,
    val diameterDeg: Double,
) {
    val waxing get() = phaseDeg < 180
    val phaseName: String
        get() = when {
            phaseDeg < 6 || phaseDeg > 354 -> "Yeni Ay"
            phaseDeg < 84 -> "Büyüyen Hilal"
            phaseDeg < 96 -> "İlk Dördün"
            phaseDeg < 174 -> "Büyüyen Şişkin Ay"
            phaseDeg < 186 -> "Dolunay"
            phaseDeg < 264 -> "Küçülen Şişkin Ay"
            phaseDeg < 276 -> "Son Dördün"
            else -> "Küçülen Hilal"
        }
    /** Perigee-side full moons are popularly called "süper ay". */
    val sizeNote: String
        get() = when {
            distanceKm < 362_000 -> "süper ay'a yakın"
            distanceKm > 400_000 -> "mikro ay"
            else -> "ortalama"
        }
}

object MoonCalc {
    fun info(time: Time): MoonInfo {
        val ph = moonPhase(time)
        val ill = illumination(Body.Moon, time)
        val distKm = geoMoon(time).length() * AU_KM
        return MoonInfo(ph, ill.phaseFraction, distKm, Math.toDegrees(2 * atan(1737.4 / distKm)))
    }

    /** The next four principal phases after [start]. */
    fun nextQuarters(start: Time): List<Pair<Int, Time>> {
        val out = ArrayList<Pair<Int, Time>>()
        var t = start
        repeat(4) {
            val q = searchMoonQuarter(t)
            out += q.quarter to q.time
            t = q.time.addDays(1.0)
        }
        return out
    }

    fun quarterName(q: Int) = when (q) { 0 -> "Yeni Ay"; 1 -> "İlk Dördün"; 2 -> "Dolunay"; else -> "Son Dördün" }
}

private val DIRS = arrayOf("Kuzey", "Kuzeydoğu", "Doğu", "Güneydoğu", "Güney", "Güneybatı", "Batı", "Kuzeybatı")
fun dirName(az: Double): String = DIRS[(((az % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]


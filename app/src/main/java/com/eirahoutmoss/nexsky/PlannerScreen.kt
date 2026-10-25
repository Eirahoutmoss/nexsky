package com.eirahoutmoss.nexsky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.Time
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

@Composable
fun PlannerScreen(act: MainActivity) {
    val st = act.state
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(6.dp))
        SegTabs(listOf("Ay", "Güneş", "Kadraj"), st.plannerTab.intValue) { st.plannerTab.intValue = it }
        Spacer(Modifier.height(10.dp))
        when (st.plannerTab.intValue) {
            0 -> MoonPlanner(act)
            1 -> SunPlanner(act)
            else -> FramePlanner(act)
        }
    }
}

@Composable
private fun DayPicker(st: AppState) {
    val day = st.planDay.longValue
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Önceki gün", tint = TextHi,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { st.planDay.longValue = startOfDay(day - 12 * 3600_000L) }.padding(6.dp))
        Text(dayLabel(day), color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sonraki gün", tint = TextHi,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { st.planDay.longValue = startOfDay(day + 36 * 3600_000L) }.padding(6.dp))
        val today = startOfDay(System.currentTimeMillis())
        if (day != today) {
            Spacer(Modifier.width(6.dp))
            Chip("Bugün", false) { st.planDay.longValue = today }
        }
    }
}

/** Sun-altitude milestones of one morning or evening. */
private class Twilight(val night: Time?, val blue: Time?, val golden: Time?, val sun: Time?, val goldenEnd: Time?)

private fun twilight(st: AppState, morning: Boolean): Twilight {
    val obs = st.observer()
    val day0 = timeOf(st.planDay.longValue)
    val dir = if (morning) Direction.Rise else Direction.Set
    val from = if (morning) day0 else timeOf(st.planDay.longValue + 12 * 3600_000L)
    fun at(alt: Double) = Sky.sunAltitude(dir, from, obs, alt)
    return if (morning) Twilight(at(-18.0), at(-6.0), at(-4.0), at(-0.833), at(6.0))
    else Twilight(at(-18.0), at(-6.0), at(-4.0), at(-0.833), at(6.0))
}

/** The colour bar from the concept: night → blue hour → sunrise → golden hour. */
@Composable
private fun TwilightBar(tw: Twilight, morning: Boolean) {
    val stops = if (morning) listOf(
        "Gece" to tw.night, "Mavi saat" to tw.blue, "Altın saat" to tw.golden, "Gün doğumu" to tw.sun, "Gündüz" to tw.goldenEnd,
    ) else listOf(
        "Gündüz" to tw.goldenEnd, "Gün batımı" to tw.sun, "Altın saat" to tw.golden, "Mavi saat" to tw.blue, "Gece" to tw.night,
    )
    val cols = if (morning) listOf(Color(0xFF0B1638), Color(0xFF2E5BD8), Color(0xFFF59E2E), Color(0xFFFFC94A), Color(0xFF8FC8FF))
    else listOf(Color(0xFF8FC8FF), Color(0xFFFFC94A), Color(0xFFF59E2E), Color(0xFF2E5BD8), Color(0xFF0B1638))
    Column {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Brush.horizontalGradient(cols)))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            stops.forEach { (n, t) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text(hhmm(t), color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MoonPlanner(act: MainActivity) {
    val st = act.state
    val obs = st.observer()
    val day = st.planDay.longValue
    val moon = SkyCatalog.solarSystem[1]
    val data = remember(day, st.lat.doubleValue, st.lon.doubleValue) {
        val start = timeOf(day)
        val rise = Sky.riseSet(moon, start, obs)
        val riseAz = rise.rise?.let { Sky.position(moon, it, obs).azDeg }
        val setAz = rise.set?.let { Sky.position(moon, it, obs).azDeg }
        val ref = rise.rise ?: timeOf(day + 21 * 3600_000L)
        Triple(rise, riseAz to setAz, MoonCalc.info(ref))
    }
    val quarters = remember(day) { MoonCalc.nextQuarters(timeOf(day)) }
    val (rs, azs, mi) = data
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DayPicker(st)
        Panel {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(150.dp)) { drawMoon(center, size.minDimension / 2, mi.phaseDeg) }
                Spacer(Modifier.height(10.dp))
                Text(mi.phaseName, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("%%%.0f aydınlık".format(TR, mi.illuminated * 100), color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Doğuş", hhmm(rs.rise), null, Modifier.weight(1f))
                StatBox("Batış", hhmm(rs.set), null, Modifier.weight(1f))
                StatBox("Doğuş yönü", azs.first?.let { "%.0f°".format(TR, it) } ?: "—", azs.first?.let { dirName(it) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Görünen büyüklük", "%.2f°".format(TR, mi.diameterDeg), mi.sizeNote, Modifier.weight(1f))
                StatBox("Mesafe", "%,.0f km".format(TR, mi.distanceKm), null, Modifier.weight(1f))
            }
        }
        Panel {
            SectionLabel("Sabah ışığı")
            Spacer(Modifier.height(8.dp))
            TwilightBar(remember(day, st.lat.doubleValue) { twilight(st, true) }, true)
            Spacer(Modifier.height(14.dp))
            SectionLabel("Akşam ışığı")
            Spacer(Modifier.height(8.dp))
            TwilightBar(remember(day, st.lat.doubleValue) { twilight(st, false) }, false)
        }
        Panel {
            SectionLabel("Yaklaşan evreler")
            Spacer(Modifier.height(6.dp))
            quarters.forEach { (q, t) ->
                val extra = if (q == 2) {
                    val d = MoonCalc.info(t).distanceKm
                    if (d < 362_000) " · süper ay" else if (d > 400_000) " · mikro ay" else ""
                } else ""
                KV(MoonCalc.quarterName(q), shortDay(t) + extra, if (extra.contains("süper")) Accent2 else TextHi)
            }
        }
        Panel {
            SectionLabel("Çekim ipucu")
            Spacer(Modifier.height(6.dp))
            Text(
                "Dolunay için başlangıç: ISO 100, f/8, 1/250 sn. Hilal ve dördünde 1–2 durak daha uzun pozlayın. " +
                    "Ufuktaki Ay daha büyük görünür ama aslında aynı boydadır; büyüklük etkisi için uzak bir ön planla uzun odak kullanın.",
                color = Muted, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SunPlanner(act: MainActivity) {
    val st = act.state
    val obs = st.observer()
    val day = st.planDay.longValue
    val sun = SkyCatalog.solarSystem[0]
    val rs = remember(day, st.lat.doubleValue, st.lon.doubleValue) { Sky.riseSet(sun, timeOf(day), obs) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DayPicker(st)
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Gün doğumu", hhmm(rs.rise), rs.rise?.let { dirName(Sky.position(sun, it, obs).azDeg) }, Modifier.weight(1f))
                StatBox("Öğle", hhmm(rs.transit), rs.transitAlt?.let { "%.0f° yükseklik".format(TR, it) }, Modifier.weight(1f))
                StatBox("Gün batımı", hhmm(rs.set), rs.set?.let { dirName(Sky.position(sun, it, obs).azDeg) }, Modifier.weight(1f))
            }
            val r = rs.rise; val s = rs.set
            if (r != null && s != null && s.millis() > r.millis()) {
                val mins = (s.millis() - r.millis()) / 60000
                Spacer(Modifier.height(8.dp))
                Text("Gün uzunluğu: ${mins / 60} sa ${mins % 60} dk", color = Muted, fontSize = 13.sp)
            }
        }
        Panel {
            SectionLabel("Sabah")
            Spacer(Modifier.height(8.dp))
            TwilightBar(remember(day, st.lat.doubleValue) { twilight(st, true) }, true)
            Spacer(Modifier.height(14.dp))
            SectionLabel("Akşam")
            Spacer(Modifier.height(8.dp))
            TwilightBar(remember(day, st.lat.doubleValue) { twilight(st, false) }, false)
        }
        Panel {
            Text(
                "Mavi saat: Güneş ufkun 4–6° altındayken. Altın saat: Güneş ufkun 4° altından 6° üstüne kadar. " +
                    "Güneşi asla filtresiz uzun odakla ya da vizörden izlemeyin.",
                color = Muted, fontSize = 13.sp,
            )
        }
    }
}

/** A camera body: sensor size (mm) and its 35 mm-equivalent zoom range. */
data class CameraPreset(val name: String, val sensorW: Double, val sensorH: Double, val minEq: Double, val maxEq: Double) {
    val crop: Double get() = 43.267 / sqrt(sensorW * sensorW + sensorH * sensorH)
    /** Field of view (degrees) at an equivalent focal length. */
    fun fov(eq: Double): Pair<Double, Double> {
        val f = eq / crop
        return Math.toDegrees(2 * atan(sensorW / (2 * f))) to Math.toDegrees(2 * atan(sensorH / (2 * f)))
    }
}

val CAMERAS = listOf(
    CameraPreset("Nikon P1000", 6.17, 4.55, 24.0, 3000.0),
    CameraPreset("Nikon P950", 6.17, 4.55, 24.0, 2000.0),
    CameraPreset("Canon SX70 HS", 6.17, 4.55, 21.0, 1365.0),
    CameraPreset("Tam kare (FF)", 36.0, 24.0, 14.0, 2000.0),
    CameraPreset("APS-C (1,5×)", 23.5, 15.6, 10.0, 1500.0),
    CameraPreset("APS-C Canon (1,6×)", 22.3, 14.9, 10.0, 1500.0),
    CameraPreset("Mikro 4/3", 17.3, 13.0, 8.0, 1200.0),
)

@Composable
private fun FramePlanner(act: MainActivity) {
    val st = act.state
    var camIdx by rememberSaveable { mutableIntStateOf(0) }
    val cam = CAMERAS[camIdx]
    var eq by rememberSaveable(camIdx) { mutableFloatStateOf(cam.maxEq.toFloat()) }
    val targets = remember(st.target.value) {
        val base = listOf("moon", "jupiter", "saturn", "mars", "venus", "M42", "M31", "M45", "M13").mapNotNull { SkyCatalog.byId(it) }
        (listOfNotNull(st.target.value) + base).distinctBy { it.id }
    }
    var tIdx by remember(targets) { mutableIntStateOf(0) }
    val obj = targets[tIdx.coerceIn(0, targets.size - 1)]
    val pos = Sky.position(obj, st.time(), st.observer())
    val phase = remember(st.now.longValue / 60_000) { MoonCalc.info(st.time()).phaseDeg }
    val (fw, fh) = cam.fov(eq.toDouble())
    val objDeg = when (obj.type) {
        ObjType.DSO -> (obj.sizeArcmin.maxOrNull() ?: 0.0) / 60.0
        else -> (pos.diameterArcmin ?: 0.0) / 60.0
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            SectionLabel("Kamera")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CAMERAS.forEachIndexed { i, c -> Chip(c.name, i == camIdx) { camIdx = i } }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Hedef")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEachIndexed { i, o -> Chip(o.name.substringBefore(" ("), i == tIdx) { tIdx = i } }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Odak uzaklığı")
                Spacer(Modifier.weight(1f))
                Text("%.0f mm (35 mm eşdeğeri)".format(TR, eq), color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            // Logarithmic slider: zoom ranges span two orders of magnitude.
            val lo = ln(cam.minEq).toFloat(); val hi = ln(cam.maxEq).toFloat()
            Slider(
                value = ln(eq.toDouble()).toFloat().coerceIn(lo, hi), onValueChange = { eq = exp(it.toDouble()).toFloat() },
                valueRange = lo..hi,
                colors = SliderDefaults.colors(thumbColor = Accent2, activeTrackColor = Accent, inactiveTrackColor = Line),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24.0, 300.0, 1000.0, 2000.0, 3000.0).filter { it in cam.minEq..cam.maxEq }.forEach { f ->
                    Chip("%.0f mm".format(TR, f), kotlin.math.abs(eq - f) < 1) { eq = f.toFloat() }
                }
            }
        }
        Panel {
            FrameView(fw, fh, obj, objDeg, phase, Modifier.fillMaxWidth().aspectRatio((cam.sensorW / cam.sensorH).toFloat()))
            Spacer(Modifier.height(10.dp))
            val ratio = if (objDeg > 0) objDeg / minOf(fw, fh) else 0.0
            val verdict = when {
                objDeg <= 0 -> "Bu cismin boyutu bilinmiyor."
                ratio > 1.0 -> "Kadraja sığmaz — daha kısa odak seçin."
                ratio > 0.8 -> "Kadrajı neredeyse dolduruyor."
                ratio > 0.25 -> "${obj.name.substringBefore(" (")} kadraja rahatça sığar."
                ratio > 0.05 -> "Küçük görünür; daha uzun odak deneyin."
                else -> "Çok küçük; bir nokta gibi görünür."
            }
            Text(verdict, color = if (ratio > 1.0) Warn else Accent2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            KV("Kadraj", "%s × %s".format(TR, fmtAngle(fw), fmtAngle(fh)))
            if (objDeg > 0) KV("Cismin boyutu", fmtAngle(objDeg) + (if (objDeg > 0) " (kadrajın %%%.0f'i)".format(TR, ratio * 100) else ""))
            KV("Şu an", if (pos.altDeg > 0) "%.0f° yüksekte, %s".format(TR, pos.altDeg, dirName(pos.azDeg).lowercase(TR)) else "ufkun altında")
            if (obj.type == ObjType.SUN) Text("Güneşi yalnızca uygun güneş filtresiyle çekin.", color = Warn, fontSize = 13.sp)
        }
        Panel {
            SectionLabel("Odak karşılaştırması")
            Spacer(Modifier.height(8.dp))
            val focals = listOf(cam.minEq, 300.0, 1000.0, cam.maxEq).filter { it in cam.minEq..cam.maxEq }.distinct()
            focals.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { f ->
                        val (w, h) = cam.fov(f)
                        Column(Modifier.weight(1f).clickable { eq = f.toFloat() }) {
                            FrameView(w, h, obj, objDeg, phase, Modifier.fillMaxWidth().aspectRatio((cam.sensorW / cam.sensorH).toFloat()), compact = true)
                            Text("%.0f mm".format(TR, f), color = TextHi, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A sensor-shaped frame with the object drawn at its true angular size. */
@Composable
private fun FrameView(fw: Double, fh: Double, o: SkyObject, objDeg: Double, moonPhase: Double, modifier: Modifier, compact: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF13204A), Color(0xFF050916))))
            .border(1.dp, Line, RoundedCornerShape(10.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pxPerDeg = size.width / fw.toFloat()
            val r = (objDeg / 2 * pxPerDeg).toFloat()
            val c = center
            when (o.type) {
                ObjType.MOON -> drawMoon(c, r.coerceAtLeast(1.5f), moonPhase)
                ObjType.SUN -> drawCircle(Color(0xFFFFD36B), r.coerceAtLeast(1.5f), c)
                ObjType.PLANET -> {
                    drawCircle(typeColor(o), r.coerceAtLeast(1.5f), c)
                    if (o.id == "saturn" && r > 3) drawOval(typeColor(o), Offset(c.x - r * 2.2f, c.y - r * 0.45f), Size(r * 4.4f, r * 0.9f), style = Stroke(width = r * 0.25f))
                }
                ObjType.DSO, ObjType.CONSTELLATION, ObjType.STAR -> {
                    val rr = r.coerceAtLeast(2f)
                    drawCircle(Brush.radialGradient(listOf(typeColor(o).copy(alpha = 0.7f), Color.Transparent), c, rr * 1.2f), rr * 1.2f, c)
                    drawCircle(Color.White, 2f, c)
                }
            }
            if (!compact) {
                // Rule-of-thirds guides
                val g = Color(0x22FFFFFF)
                for (k in 1..2) {
                    drawLine(g, Offset(size.width * k / 3, 0f), Offset(size.width * k / 3, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    drawLine(g, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
        }
    }
}

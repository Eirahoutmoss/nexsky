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
import androidx.compose.runtime.mutableStateOf
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
import kotlin.math.exp
import kotlin.math.ln

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

/** One selectable camera: a body, or one of the phone's own cameras. */
private data class CamOption(val key: String, val label: String, val group: String, val body: Body, val phoneLens: Lens? = null)

@Composable
private fun FramePlanner(act: MainActivity) {
    val st = act.state
    val phone = remember { phoneCameras(act) }
    val options = remember(phone) {
        phone.map { CamOption("phone:${it.id}", it.lens.name, "Bu telefon", it.body, it.lens) } +
            BODIES.map { CamOption(it.name, it.name, it.group, it) }
    }
    var camKey by rememberSaveable { mutableStateOf("Nikon Coolpix P1000") }
    val cam = options.firstOrNull { it.key == camKey } ?: options.first { it.key == "Nikon Coolpix P1000" }
    val body = cam.body
    var lensName by rememberSaveable { mutableStateOf("150–600 mm (Sigma / Tamron)") }
    val lens = LENSES.firstOrNull { it.name == lensName } ?: LENSES[9]
    var tc by rememberSaveable { mutableFloatStateOf(1f) }
    // Real focal length (mm) being previewed.
    var realF by rememberSaveable(camKey, lensName) {
        mutableFloatStateOf(
            when {
                cam.phoneLens != null -> cam.phoneLens.minF.toFloat()
                body.fixedEq != null -> (body.fixedEq.endInclusive / body.crop).toFloat()
                else -> lens.maxF.toFloat()
            },
        )
    }
    val f = when {
        cam.phoneLens != null -> cam.phoneLens.minF
        body.fixedEq != null -> realF.toDouble()
        else -> realF.toDouble() * tc
    }
    val eq = f * body.crop

    val targets = remember(st.target.value) {
        val base = listOf("moon", "jupiter", "saturn", "mars", "venus", "M42", "M31", "M45", "M13").mapNotNull { SkyCatalog.byId(it) }
        (listOfNotNull(st.target.value) + base).distinctBy { it.id }
    }
    var tIdx by remember(targets) { mutableIntStateOf(0) }
    val obj = targets[tIdx.coerceIn(0, targets.size - 1)]
    val pos = Sky.position(obj, st.time(), st.observer())
    val phase = remember(st.now.longValue / 60_000) { MoonCalc.info(st.time()).phaseDeg }
    val (fw, fh) = body.fovReal(f)
    val objDeg = when (obj.type) {
        ObjType.DSO -> (obj.sizeArcmin.maxOrNull() ?: 0.0) / 60.0
        else -> (pos.diameterArcmin ?: 0.0) / 60.0
    }
    val aspect = (body.sensorW / body.sensorH).toFloat()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Dropdown("Kamera", options, cam, { it.label }, { it.group }) { camKey = it.key }
            if (cam.phoneLens == null && body.fixedEq == null) {
                Spacer(Modifier.height(12.dp))
                Dropdown("Objektif / teleskop", LENSES, lens, { it.name }, { it.group }) { lensName = it.name }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Telekonvertör", color = Muted, fontSize = 12.sp)
                    listOf(1f to "yok", 1.4f to "1,4×", 2f to "2×").forEach { (k, l) -> Chip(l, tc == k) { tc = k } }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Hedef")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEachIndexed { i, o -> Chip(o.name.substringBefore(" ("), i == tIdx) { tIdx = i } }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Odak uzaklığı")
                Spacer(Modifier.weight(1f))
                Text(
                    (if (body.fixedEq == null) "%.0f mm · ".format(TR, f) else "") + "%.0f mm eşdeğer".format(TR, eq),
                    color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
            // Zoom: a logarithmic slider; fixed lenses have nothing to slide.
            val range: ClosedFloatingPointRange<Double>? = when {
                cam.phoneLens != null -> null
                body.fixedEq != null -> (body.fixedEq.start / body.crop)..(body.fixedEq.endInclusive / body.crop)
                lens.zoom -> lens.minF..lens.maxF
                else -> null
            }
            if (range != null) {
                val lo = ln(range.start).toFloat(); val hi = ln(range.endInclusive).toFloat()
                Slider(
                    value = ln(realF.toDouble()).toFloat().coerceIn(lo, hi), onValueChange = { realF = exp(it.toDouble()).toFloat() },
                    valueRange = lo..hi,
                    colors = SliderDefaults.colors(thumbColor = Accent2, activeTrackColor = Accent, inactiveTrackColor = Line),
                )
                if (body.fixedEq != null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24.0, 300.0, 1000.0, 2000.0, 3000.0).filter { it in body.fixedEq }.forEach { e ->
                            Chip("%.0f mm".format(TR, e), kotlin.math.abs(eq - e) < 1) { realF = (e / body.crop).toFloat() }
                        }
                    }
                }
            }
        }
        Panel {
            FrameView(fw, fh, obj, objDeg, phase, Modifier.fillMaxWidth().aspectRatio(aspect))
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
            if (objDeg > 0) KV("Cismin boyutu", fmtAngle(objDeg) + " (kadrajın %%%.0f'i)".format(TR, ratio * 100))
            KV("Şu an", if (pos.altDeg > 0) "%.0f° yüksekte, %s".format(TR, pos.altDeg, dirName(pos.azDeg).lowercase(TR)) else "ufkun altında")
            if (obj.type == ObjType.SUN) Text("Güneşi yalnızca uygun güneş filtresiyle çekin.", color = Warn, fontSize = 13.sp)
        }
        Panel {
            SectionLabel("Karşılaştırma")
            Spacer(Modifier.height(8.dp))
            // (label, real focal) pairs to compare
            val cmp: List<Pair<String, Double>> = when {
                cam.phoneLens != null -> phone.map { it.lens.name.substringBefore(" (") to it.lens.minF }
                body.fixedEq != null -> listOf(body.fixedEq.start, 300.0, 1000.0, body.fixedEq.endInclusive)
                    .filter { it in body.fixedEq }.distinct().map { "%.0f mm".format(TR, it) to it / body.crop }
                else -> listOf(
                    "%.0f mm".format(TR, lens.minF) to lens.minF,
                    "%.0f mm".format(TR, lens.maxF) to lens.maxF,
                    "%.0f mm + 1,4×".format(TR, lens.maxF) to lens.maxF * 1.4,
                    "%.0f mm + 2×".format(TR, lens.maxF) to lens.maxF * 2,
                ).distinctBy { it.second }
            }
            val cmpBody = if (cam.phoneLens != null) null else body
            cmp.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { (label, ff) ->
                        val b = cmpBody ?: phone.firstOrNull { it.lens.minF == ff }?.body ?: body
                        val (w, h) = b.fovReal(ff)
                        Column(Modifier.weight(1f)) {
                            FrameView(w, h, obj, objDeg, phase, Modifier.fillMaxWidth().aspectRatio((b.sensorW / b.sensorH).toFloat()), compact = true)
                            Text(label, color = TextHi, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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

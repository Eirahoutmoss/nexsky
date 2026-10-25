package com.eirahoutmoss.nexsky

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Everything the sky view needs in horizontal coordinates (ENU, true north), refreshed every 2 s. */
private class SkyFrame(
    val stars: FloatArray,            // x,y,z per star (ENU)
    val lines: List<FloatArray>,      // constellation polylines, ENU triples
    val labels: List<Pair<String, FloatArray>>,
    val bodies: List<Triple<SkyObject, FloatArray, SkyPos>>,
    val dsos: List<Pair<SkyObject, FloatArray>>,
    val moonPhase: Double,
    val rot: Array<DoubleArray>,      // J2000 → horizontal
)

private fun enu(altDeg: Double, azDeg: Double): FloatArray {
    val a = Math.toRadians(altDeg); val z = Math.toRadians(azDeg)
    return floatArrayOf((cos(a) * sin(z)).toFloat(), (cos(a) * cos(z)).toFloat(), sin(a).toFloat())
}

private fun buildFrame(st: AppState): SkyFrame {
    val t = st.time(); val obs = st.observer()
    val r = Sky.eqjToHor(t, obs).rot
    // J2000 unit vector → horizontal (x north, y west, z up) → ENU.
    fun hor(x: Double, y: Double, z: Double, out: FloatArray, i: Int) {
        val hx = r[0][0] * x + r[1][0] * y + r[2][0] * z
        val hy = r[0][1] * x + r[1][1] * y + r[2][1] * z
        val hz = r[0][2] * x + r[1][2] * y + r[2][2] * z
        out[i] = (-hy).toFloat(); out[i + 1] = hx.toFloat(); out[i + 2] = hz.toFloat()
    }
    fun radec(raDeg: Double, decDeg: Double, out: FloatArray, i: Int) {
        val ra = Math.toRadians(raDeg); val dec = Math.toRadians(decDeg)
        hor(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec), out, i)
    }
    val stars = SkyCatalog.stars
    val sv = FloatArray(stars.size * 3)
    stars.forEachIndexed { i, s -> hor(s.x, s.y, s.z, sv, i * 3) }
    val lines = ArrayList<FloatArray>()
    for (c in SkyCatalog.constellations) for (seg in c.lines) {
        val n = seg.size / 2
        val out = FloatArray(n * 3)
        for (k in 0 until n) radec(seg[2 * k], seg[2 * k + 1], out, k * 3)
        lines += out
    }
    val labels = SkyCatalog.constellations.map { c -> c.name to FloatArray(3).also { radec(c.labelRa, c.labelDec, it, 0) } }
    val bodies = SkyCatalog.solarSystem.map { o ->
        val p = Sky.position(o, t, obs)
        Triple(o, enu(p.altDeg, p.azDeg), p)
    }
    val dsos = SkyCatalog.objects.filter { it.type == ObjType.DSO && (it.mag ?: 99.0) < 8.5 }
        .map { o -> o to FloatArray(3).also { radec(o.raDeg, o.decDeg, it, 0) } }
    return SkyFrame(sv, lines, labels, bodies, dsos, MoonCalc.info(t).phaseDeg, r)
}

private fun starColor(bv: Float): Color = when {
    bv < 0.0f -> Color(0xFFB9CCFF)
    bv < 0.4f -> Color(0xFFE6EDFF)
    bv < 0.8f -> Color(0xFFFFF6E5)
    bv < 1.3f -> Color(0xFFFFDDAA)
    else -> Color(0xFFFFB98A)
}

/** Projects ENU vectors through the phone (or manual) orientation onto the screen. */
private class Projector(m: FloatArray, decl: Float, val w: Float, val h: Float, fovDeg: Float) {
    private val r = m
    private val cd = cos(Math.toRadians(decl.toDouble())).toFloat()
    private val sd = sin(Math.toRadians(decl.toDouble())).toFloat()
    val f = (h / 2f) / tan(Math.toRadians(fovDeg / 2.0)).toFloat()
    val cx = w / 2f; val cy = h / 2f

    /** Returns false if behind the viewer. [out]: screen x, y. */
    fun project(e0: Float, n0: Float, u: Float, out: FloatArray): Boolean {
        // true → magnetic azimuth (sensor world frame is magnetic).
        val e = e0 * cd - n0 * sd
        val n = n0 * cd + e0 * sd
        val dx = r[0] * e + r[3] * n + r[6] * u
        val dy = r[1] * e + r[4] * n + r[7] * u
        val dz = r[2] * e + r[5] * n + r[8] * u
        if (dz > -0.05f) return false
        out[0] = cx + f * dx / -dz
        out[1] = cy - f * dy / -dz
        return true
    }

    fun onScreen(p: FloatArray, margin: Float = 40f) = p[0] > -margin && p[0] < w + margin && p[1] > -margin && p[1] < h + margin

    /** Direction (radians, screen space) toward an off-screen ENU vector. */
    fun edgeAngle(e0: Float, n0: Float, u: Float): Float {
        val e = e0 * cd - n0 * sd
        val n = n0 * cd + e0 * sd
        val dx = r[0] * e + r[3] * n + r[6] * u
        val dy = r[1] * e + r[4] * n + r[7] * u
        return atan2(-dy, dx)
    }
}

/** Builds a device→world matrix for manual mode: looking at [azDeg], [altDeg], screen up = sky up. */
private fun manualMatrix(azDeg: Float, altDeg: Float): FloatArray {
    val a = Math.toRadians(altDeg.toDouble()); val z = Math.toRadians(azDeg.toDouble())
    val fx = (cos(a) * sin(z)).toFloat(); val fy = (cos(a) * cos(z)).toFloat(); val fz = sin(a).toFloat()
    // device z = −forward; device y = world up made orthogonal; device x = y × z
    val zx = -fx; val zy = -fy; val zz = -fz
    var ux = 0f - fx * fz; var uy = 0f - fy * fz; var uz = 1f - fz * fz
    val ul = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-4f); ux /= ul; uy /= ul; uz /= ul
    val xx = uy * zz - uz * zy; val xy = uz * zx - ux * zz; val xz = ux * zy - uy * zx
    return floatArrayOf(xx, ux, zx, xy, uy, zy, xz, uz, zz)
}

private enum class SkyMode(val label: String) { CAMERA("Kamera"), AR("AR Modu"), MAP("Harita") }

/** Live back-camera preview (CameraX) behind the sky overlay. */
@Composable
private fun CameraPreview(modifier: Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            PreviewView(c).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                view = this
            }
        },
    )
    DisposableEffect(owner, view) {
        val v = view
        var provider: ProcessCameraProvider? = null
        if (v != null) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(v.surfaceProvider) }
                    p.unbindAll()
                    p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { try { provider?.unbindAll() } catch (_: Exception) {} }
    }
}

/** Notable objects currently inside the screen, for the "Görüş alanında" row. */
private fun visibleObjects(pr: Projector, fr: SkyFrame): List<Pair<SkyObject, Double>> {
    val p = FloatArray(2)
    val out = ArrayList<Triple<SkyObject, Double, Int>>()
    for ((o, v, sp) in fr.bodies) {
        if (sp.altDeg > -1 && pr.project(v[0], v[1], v[2], p) && pr.onScreen(p, -10f)) out += Triple(o, sp.altDeg, 0)
    }
    for (o in SkyCatalog.objects) {
        val rank = when {
            o.type == ObjType.STAR && (o.mag ?: 9.0) < 2.2 -> 1
            o.type == ObjType.DSO && (o.mag ?: 99.0) < 7.5 -> 2
            o.type == ObjType.CONSTELLATION -> 3
            else -> continue
        }
        val v = targetEnu(o, fr)
        if (v[2] < 0f) continue
        if (pr.project(v[0], v[1], v[2], p) && pr.onScreen(p, -10f)) {
            out += Triple(o, Math.toDegrees(kotlin.math.asin(v[2].toDouble().coerceIn(-1.0, 1.0))), rank)
        }
    }
    return out.sortedWith(compareBy({ it.third }, { it.first.mag ?: 99.0 })).take(14).map { it.first to it.second }
}

@Composable
fun SkyScreen(act: MainActivity) {
    val st = act.state
    val ori = act.orientation
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(if (ori.available) SkyMode.AR else SkyMode.MAP) }
    var camAllowed by remember { mutableStateOf(act.has(Manifest.permission.CAMERA)) }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        camAllowed = ok
        if (ok) mode = SkyMode.CAMERA
    }
    val camFov = remember { mainCameraVerticalFov(ctx) }
    var fov by remember { mutableFloatStateOf(70f) }
    var manAz by remember { mutableFloatStateOf(180f) }
    var manAlt by remember { mutableFloatStateOf(30f) }
    var picked by remember { mutableStateOf<SkyObject?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val measurer = rememberTextMeasurer()
    val sensorMode = mode != SkyMode.MAP
    DisposableEffect(sensorMode) {
        if (sensorMode) ori.start()
        onDispose { ori.stop() }
    }
    val slot = st.now.longValue / 2000
    val frame = remember(slot, st.lat.doubleValue, st.lon.doubleValue) { buildFrame(st) }
    val decl = remember(st.lat.doubleValue, st.lon.doubleValue) { st.declination() }
    val target = st.target.value
    val hits = remember { ArrayList<Pair<SkyObject, Offset>>() }
    val viewFov = if (mode == SkyMode.CAMERA) camFov else fov
    // Refreshed every 2 s without subscribing to the 50 Hz sensor.
    val inView = remember(slot, mode, manAz, manAlt, fov, canvasSize) {
        val m = Snapshot.withoutReadObservation { if (sensorMode) ori.matrix.value else manualMatrix(manAz, manAlt) }
        visibleObjects(Projector(m, if (sensorMode) decl else 0f, canvasSize.width.toFloat(), canvasSize.height.toFloat(), viewFov), frame)
    }

    Box(Modifier.fillMaxSize()) {
        if (mode == SkyMode.CAMERA && camAllowed) CameraPreview(Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(mode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (mode == SkyMode.CAMERA) return@detectTransformGestures
                        fov = (fov / zoom).coerceIn(8f, 110f)
                        if (mode == SkyMode.MAP) {
                            val degPerPx = fov / size.height
                            manAz = ((manAz - pan.x * degPerPx) % 360f + 360f) % 360f
                            manAlt = (manAlt + pan.y * degPerPx).coerceIn(-30f, 90f)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        picked = hits.minByOrNull { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) }
                            ?.takeIf { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) < 60f }?.first
                    }
                },
        ) {
            val m = if (sensorMode) ori.matrix.value else manualMatrix(manAz, manAlt)
            val pr = Projector(m, if (sensorMode) decl else 0f, size.width, size.height, viewFov)
            drawSky(pr, frame, measurer, viewFov, target, hits, overCamera = mode == SkyMode.CAMERA && camAllowed)
        }
        // Top: target card
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp)) {
            val show = picked ?: target
            if (show != null) {
                val p = Sky.position(show, st.time(), st.observer())
                Panel(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ObjIcon(show, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(show.name, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Yükseklik %.0f° · Yön %s (%.0f°)".format(TR, p.altDeg, dirName(p.azDeg).lowercase(TR), p.azDeg) +
                                    (fmtDist(show, p)?.let { " · $it" } ?: "") +
                                    (p.mag?.let { " · kadir %.1f".format(TR, it) } ?: ""),
                                color = Muted, fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Filled.Close, "Kapat", tint = Muted, modifier = Modifier.clickable {
                            if (picked != null) picked = null else st.target.value = null
                        })
                    }
                    if (picked != null && picked != target) {
                        Text("Hedef yap", color = Accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp).clickable { st.target.value = picked; picked = null })
                    }
                }
            }
        }
        // Bottom: what is in view, heading, mode switch
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp)) {
            if (inView.isNotEmpty()) {
                Text("GÖRÜŞ ALANINDA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inView.forEach { (o, alt) ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC0E1A33)).clickable { picked = o }
                                .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ObjIcon(o, 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(o.name.substringBefore(" (") + " · %.0f°".format(TR, alt), color = TextHi, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (sensorMode) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { HeadingLabel(ori, decl) }
            } else {
                Text(
                    "Sürükleyerek gezin, iki parmakla yakınlaştırın",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xCC0E1A33)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SkyMode.entries.forEach { m ->
                    val enabled = m == SkyMode.MAP || ori.available
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (mode == m) Accent else Color.Transparent)
                            .clickable(enabled = enabled && mode != m) {
                                when (m) {
                                    SkyMode.CAMERA -> if (camAllowed) mode = m else askCam.launch(Manifest.permission.CAMERA)
                                    SkyMode.MAP -> {
                                        manAz = ((ori.heading() + decl) % 360f + 360f) % 360f; manAlt = 30f; mode = m
                                    }
                                    SkyMode.AR -> mode = m
                                }
                            }.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(m.label, color = if (enabled) TextHi else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (!ori.available) Text("Bu telefonda yön sensörü yok; Harita modu kullanılıyor.", color = Warn, fontSize = 12.sp)
        }
    }
}

/** Reads the sensor in its own scope so only this label recomposes at sensor rate. */
@Composable
private fun HeadingLabel(ori: Orientation, decl: Float) {
    val hd = ((ori.heading(ori.matrix.value) + decl) % 360f + 360f) % 360f
    Text("Bakış: %s %.0f°".format(TR, dirName(hd.toDouble()), hd), color = TextHi, fontSize = 13.sp)
}

private fun DrawScope.drawSky(
    pr: Projector, fr: SkyFrame, tm: TextMeasurer, fov: Float, target: SkyObject?,
    hits: ArrayList<Pair<SkyObject, Offset>>, overCamera: Boolean = false,
) {
    hits.clear()
    if (!overCamera) drawRect(Brush.verticalGradient(listOf(Color(0xFF050916), Color(0xFF0B1638), Color(0xFF13204A))))
    val p = FloatArray(2); val q = FloatArray(2)
    val small = TextStyle(color = Color(0xFF9FB3D9), fontSize = 11.sp, fontFamily = Inter)
    val label = TextStyle(color = Color(0xFFE8EEF8), fontSize = 12.sp, fontFamily = Inter, fontWeight = FontWeight.SemiBold)

    // Ground: band between horizon and 30° below.
    val ground = Path(); var started = false
    val horizon = Path(); var hStarted = false
    for (az in 0..360 step 4) {
        val v = enu(0.0, az.toDouble())
        if (pr.project(v[0], v[1], v[2], p)) {
            if (!hStarted) { horizon.moveTo(p[0], p[1]); hStarted = true } else horizon.lineTo(p[0], p[1])
        } else hStarted = false
    }
    for (az in 0..360 step 6) {
        val top = enu(0.0, az.toDouble()); val bot = enu(-35.0, az.toDouble())
        val a = FloatArray(2); val b = FloatArray(2)
        val nTop = enu(0.0, az + 6.0); val nBot = enu(-35.0, az + 6.0)
        val c = FloatArray(2); val d = FloatArray(2)
        if (pr.project(top[0], top[1], top[2], a) && pr.project(bot[0], bot[1], bot[2], b) &&
            pr.project(nTop[0], nTop[1], nTop[2], c) && pr.project(nBot[0], nBot[1], nBot[2], d)) {
            ground.moveTo(a[0], a[1]); ground.lineTo(c[0], c[1]); ground.lineTo(d[0], d[1]); ground.lineTo(b[0], b[1]); ground.close()
            started = true
        }
    }
    if (started) drawPath(ground, if (overCamera) Color(0x330A0F1E) else Color(0xCC0A0F1E))
    drawPath(horizon, Color(0xFF3FA9FF), style = Stroke(width = 2f))
    // Cardinal labels
    listOf("K" to 0.0, "KD" to 45.0, "D" to 90.0, "GD" to 135.0, "G" to 180.0, "GB" to 225.0, "B" to 270.0, "KB" to 315.0).forEach { (n, az) ->
        val v = enu(1.5, az)
        if (pr.project(v[0], v[1], v[2], p) && pr.onScreen(p)) {
            val txt = tm.measure(n, TextStyle(color = if (n == "K") Color(0xFFFF6B6B) else Color(0xFF7FD8FF), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Inter))
            drawText(txt, topLeft = Offset(p[0] - txt.size.width / 2f, p[1] - txt.size.height - 4f))
        }
    }

    // Constellation lines + names
    val lineCol = if (overCamera) Color(0x998FC4FF) else Color(0x553F7AD9)
    for (seg in fr.lines) {
        var prevOk = false
        for (k in 0 until seg.size / 3) {
            val ok = pr.project(seg[3 * k], seg[3 * k + 1], seg[3 * k + 2], q)
            if (ok && prevOk && (pr.onScreen(p, 400f) || pr.onScreen(q, 400f))) drawLine(lineCol, Offset(p[0], p[1]), Offset(q[0], q[1]), 1.6f)
            p[0] = q[0]; p[1] = q[1]; prevOk = ok
        }
    }
    if (fov < 100f) for ((name, v) in fr.labels) {
        if (pr.project(v[0], v[1], v[2], p) && pr.onScreen(p)) {
            val txt = tm.measure(name.uppercase(TR), small.copy(color = Color(0x99A9BEE6), letterSpacing = 1.5.sp))
            drawText(txt, topLeft = Offset(p[0] - txt.size.width / 2f, p[1]))
        }
    }

    // Stars: fainter limit when zoomed in.
    val limit = when { fov > 80 -> 4.5f; fov > 45 -> 5.2f; else -> 6.1f }
    val zoomBoost = (70f / fov).coerceIn(0.8f, 2.2f)
    val stars = SkyCatalog.stars
    for (i in stars.indices) {
        val s = stars[i]
        if (s.mag > limit) break // sorted by magnitude
        if (!pr.project(fr.stars[3 * i], fr.stars[3 * i + 1], fr.stars[3 * i + 2], p) || !pr.onScreen(p, 10f)) continue
        val rad = ((4.2f - 0.62f * s.mag).coerceAtLeast(0.9f)) * zoomBoost
        val col = starColor(s.bv)
        if (s.mag < 1.5f) drawCircle(col.copy(alpha = 0.18f), rad * 2.6f, Offset(p[0], p[1]))
        drawCircle(col, rad, Offset(p[0], p[1]))
    }
    // Named bright stars
    for (o in SkyCatalog.objects) {
        if (o.type != ObjType.STAR || (o.mag ?: 9.0) > (if (fov < 40) 3.5 else 1.6)) continue
        val star = targetEnu(o, fr)
        if (pr.project(star[0], star[1], star[2], p) && pr.onScreen(p)) {
            hits += o to Offset(p[0], p[1])
            val txt = tm.measure(o.name.substringBefore(" ("), small)
            drawText(txt, topLeft = Offset(p[0] + 8f, p[1] - txt.size.height / 2f))
        }
    }
    // Deep-sky objects
    if (fov < 90f) for ((o, v) in fr.dsos) {
        if (!pr.project(v[0], v[1], v[2], p) || !pr.onScreen(p)) continue
        val sizePx = ((o.sizeArcmin[0] / 60.0) * pr.f * Math.PI / 180).toFloat().coerceIn(6f, 400f)
        drawCircle(typeColor(o).copy(alpha = 0.55f), sizePx / 2, Offset(p[0], p[1]), style = Stroke(width = 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        hits += o to Offset(p[0], p[1])
        if (fov < 50f) {
            val txt = tm.measure(o.id, small.copy(color = typeColor(o)))
            drawText(txt, topLeft = Offset(p[0] + sizePx / 2 + 4f, p[1] - txt.size.height / 2f))
        }
    }
    // Sun, Moon, planets
    for ((o, v, sp) in fr.bodies) {
        if (!pr.project(v[0], v[1], v[2], p) || !pr.onScreen(p)) continue
        val c = Offset(p[0], p[1])
        val trueR = ((sp.diameterArcmin ?: 0.0) / 120.0 * pr.f * Math.PI / 180).toFloat()
        when (o.type) {
            ObjType.MOON -> drawMoon(c, trueR.coerceAtLeast(14f), fr.moonPhase)
            ObjType.SUN -> { drawCircle(Color(0x55FFC94A), trueR.coerceAtLeast(14f) * 2f, c); drawCircle(Color(0xFFFFD36B), trueR.coerceAtLeast(14f), c) }
            else -> { drawCircle(typeColor(o).copy(alpha = 0.25f), 12f, c); drawCircle(typeColor(o), trueR.coerceAtLeast(5f), c) }
        }
        hits += o to c
        val txt = tm.measure(o.name, label.copy(color = typeColor(o)))
        drawText(txt, topLeft = Offset(c.x + 14f, c.y - txt.size.height - 2f))
    }

    // Target: ring, or an arrow at the edge pointing to it.
    if (target != null) {
        val tv = targetEnu(target, fr)
        run {
            val ok = pr.project(tv[0], tv[1], tv[2], p)
            if (ok && pr.onScreen(p, -30f)) {
                val c = Offset(p[0], p[1])
                drawCircle(Accent2, 34f, c, style = Stroke(width = 3f))
                drawCircle(Accent2.copy(alpha = 0.25f), 46f, c, style = Stroke(width = 8f))
            } else {
                val ang = pr.edgeAngle(tv[0], tv[1], tv[2])
                val cx = size.width / 2; val cy = size.height / 2
                val rx = size.width / 2 - 70f; val ry = size.height / 2 - 150f
                val t = minOf(abs(rx / cos(ang)), abs(ry / sin(ang)))
                val tip = Offset(cx + cos(ang) * t, cy + sin(ang) * t)
                rotate(Math.toDegrees(ang.toDouble()).toFloat(), tip) {
                    val path = Path().apply {
                        moveTo(tip.x + 34f, tip.y); lineTo(tip.x - 14f, tip.y - 22f); lineTo(tip.x - 4f, tip.y); lineTo(tip.x - 14f, tip.y + 22f); close()
                    }
                    drawPath(path, Accent2)
                }
                val txt = tm.measure(target.name.substringBefore(" ("), label)
                drawText(txt, topLeft = Offset(tip.x - txt.size.width / 2f, tip.y + 28f))
            }
        }
    }
    // Centre reticle
    drawCircle(Color(0x66FFFFFF), 10f, center, style = Stroke(width = 1.5f))
}

/** ENU vector (true north) for any object in this frame. */
private fun targetEnu(o: SkyObject, fr: SkyFrame): FloatArray {
    fr.bodies.firstOrNull { it.first.id == o.id }?.let { return it.second }
    val m = fr.rot
    val ra = Math.toRadians(o.raDeg); val dec = Math.toRadians(o.decDeg)
    val x = cos(dec) * cos(ra); val y = cos(dec) * sin(ra); val z = sin(dec)
    val hx = m[0][0] * x + m[1][0] * y + m[2][0] * z
    val hy = m[0][1] * x + m[1][1] * y + m[2][1] * z
    val hz = m[0][2] * x + m[1][2] * y + m[2][2] * z
    return floatArrayOf((-hy).toFloat(), hx.toFloat(), hz.toFloat())
}

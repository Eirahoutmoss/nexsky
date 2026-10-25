package com.eirahoutmoss.nexsky

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import kotlin.math.atan
import kotlin.math.tan

fun initOsm(ctx: Context) {
    Configuration.getInstance().apply {
        userAgentValue = BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME
        val base = File(ctx.filesDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
        tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 220L * 1024 * 1024
    }
}

/** A direction ray drawn from a point on the map. */
data class Ray(val from: GeoPoint, val azDeg: Double, val km: Double, val color: Int, val dashed: Boolean, val label: String)

private class RayOverlay(val rays: () -> List<Ray>, val marks: () -> List<Pair<GeoPoint, Int>>) : Overlay() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; isFakeBoldText = true }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val a = Point(); private val b = Point()
    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mv.projection
        for (r in rays()) {
            val to = r.from.destinationPoint(r.km * 1000, r.azDeg)
            pj.toPixels(r.from, a); pj.toPixels(to, b)
            paint.color = r.color
            paint.pathEffect = if (r.dashed) DashPathEffect(floatArrayOf(22f, 16f), 0f) else null
            c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), paint)
            text.color = r.color
            c.drawText(r.label, b.x + 10f, b.y, text)
        }
        for ((p, col) in marks()) {
            pj.toPixels(p, a)
            fill.color = 0x55FFFFFF; c.drawCircle(a.x.toFloat(), a.y.toFloat(), 30f, fill)
            fill.color = col; c.drawCircle(a.x.toFloat(), a.y.toFloat(), 17f, fill)
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(a.x.toFloat(), a.y.toFloat(), 7f, fill)
        }
    }
}

@Composable
fun MapScreen(act: MainActivity) {
    val st = act.state
    val obs = st.observer()
    val here = GeoPoint(st.lat.doubleValue, st.lon.doubleValue)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var subject by remember { mutableStateOf<GeoPoint?>(null) }
    var subjectH by remember { mutableDoubleStateOf(20.0) }
    var ratio by remember { mutableDoubleStateOf(1.0) }
    var showSun by remember { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current
    val day = st.planDay.longValue

    val moon = SkyCatalog.solarSystem[1]; val sun = SkyCatalog.solarSystem[0]
    val info = remember(day, st.lat.doubleValue, st.lon.doubleValue) {
        val mrs = Sky.riseSet(moon, timeOf(day), obs)
        val srs = Sky.riseSet(sun, timeOf(day), obs)
        val mRise = mrs.rise?.let { Sky.position(moon, it, obs).azDeg }
        val mSet = mrs.set?.let { Sky.position(moon, it, obs).azDeg }
        val sRise = srs.rise?.let { Sky.position(sun, it, obs).azDeg }
        val sSet = srs.set?.let { Sky.position(sun, it, obs).azDeg }
        val diam = MoonCalc.info(mrs.rise ?: timeOf(day + 20 * 3600_000L)).diameterDeg
        listOf(mRise, mSet, sRise, sSet) to Triple(mrs, srs, diam)
    }
    val (az, extra) = info
    val (mrs, srs, moonDiam) = extra
    val target = st.target.value?.takeIf { it.type != ObjType.MOON && it.type != ObjType.SUN }
    val targetAz = target?.let { Sky.position(it, st.time(), obs).azDeg }

    // Where to stand so the rising Moon appears [ratio] × the subject's height behind it.
    val standDist = subjectH / tan(Math.toRadians(moonDiam / ratio))
    val stand = subject?.let { s -> az[0]?.let { s.destinationPoint(standDist, (it + 180) % 360) } }

    val rays: List<Ray> = buildList {
        val origin = stand ?: here
        az[0]?.let { add(Ray(origin, it, if (stand != null) standDist / 1000 + 2 else 4.0, 0xFFFFD66B.toInt(), false, "Ay doğuşu %.0f°".format(TR, it))) }
        az[1]?.let { add(Ray(here, it, 4.0, 0xFFFFB25A.toInt(), true, "Ay batışı")) }
        if (showSun) {
            az[2]?.let { add(Ray(here, it, 3.0, 0xFFFF8A3D.toInt(), false, "Gün doğumu")) }
            az[3]?.let { add(Ray(here, it, 3.0, 0xFFFF5D5D.toInt(), true, "Gün batımı")) }
        }
        if (target != null && targetAz != null) add(Ray(here, targetAz, 3.0, Accent2.toArgb(), false, target.name.substringBefore(" (")))
    }
    val marks = buildList {
        add(here to Accent.toArgb())
        subject?.let { add(it to 0xFFB79CFF.toInt()) }
        stand?.let { add(it to 0xFF2FD18C.toInt()) }
    }

    DisposableEffect(owner, mapView) {
        val mv = mapView
        val obsv = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mv?.onResume()
                Lifecycle.Event.ON_PAUSE -> mv?.onPause()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obsv)
        mv?.onResume()
        onDispose { owner.lifecycle.removeObserver(obsv); mv?.onPause() }
    }
    LaunchedEffect(rays, marks) { mapView?.invalidate() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                initOsm(ctx)
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(13.0)
                    controller.setCenter(here)
                    overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                    overlayManager.tilesOverlay.setLoadingBackgroundColor(Bg.toArgb())
                    setBackgroundColor(Bg.toArgb())
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                        override fun longPressHelper(p: GeoPoint?): Boolean { subject = p; return true }
                    }))
                    mapView = this
                }
            },
            update = { mv ->
                // Keep a single ray overlay with fresh data.
                mv.overlays.removeAll { it is RayOverlay }
                mv.overlays.add(RayOverlay({ rays }, { marks }))
                mv.invalidate()
            },
        )
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp)) {
            Panel(padding = 12.dp) {
                Text(dayLabel(day), color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Ay doğuşu ${hhmm(mrs.rise)}" + (az[0]?.let { " · %.0f° %s".format(TR, it, dirName(it).lowercase(TR)) } ?: "") +
                        "   Gün doğumu ${hhmm(srs.rise)} · batımı ${hhmm(srs.set)}",
                    color = Muted, fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Güneş çizgileri", showSun) { showSun = !showSun }
                    Chip("Yarın", false) { st.planDay.longValue = startOfDay(day + 36 * 3600_000L) }
                    Chip("Bugün", false) { st.planDay.longValue = startOfDay(System.currentTimeMillis()) }
                }
            }
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(48.dp).clip(CircleShape).background(Color(0xE60F1B2E))
                .border(1.dp, Line, CircleShape).clickable { mapView?.controller?.animateTo(here) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.MyLocation, "Konumum", tint = Accent2) }
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp)) {
            Panel(padding = 12.dp) {
                if (subject == null) {
                    Text("Çekim noktası bul", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Haritada ön plana almak istediğin yere (kale, minare, ağaç…) uzun bas. NexSky, doğan Ay'ı onun arkasında görmek için nerede durman gerektiğini hesaplar.",
                        color = Muted, fontSize = 12.sp,
                    )
                } else {
                    Text("Önerilen çekim noktası (yeşil)", color = Good, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val d = FloatArray(2)
                    if (stand != null) {
                        android.location.Location.distanceBetween(here.latitude, here.longitude, stand.latitude, stand.longitude, d)
                        Text(
                            "Ön plandan %.1f km uzakta; sana %.1f km, %s yönünde. Ay %s doğarken ön planın arkasında görünür."
                                .format(TR, standDist / 1000, d[0] / 1000, dirName(((d[1] + 360) % 360).toDouble()).lowercase(TR), hhmm(mrs.rise)),
                            color = TextHi, fontSize = 13.sp,
                        )
                    } else Text("Bu gün Ay doğmuyor.", color = Warn, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Ön plan yüksekliği", color = Muted, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10.0, 20.0, 40.0, 80.0, 150.0).forEach { h -> Chip("${h.toInt()} m", subjectH == h) { subjectH = h } }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Ay'ın boyu, ön plana göre", color = Muted, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5 to "yarısı", 1.0 to "aynı", 2.0 to "2 katı").forEach { (r, l) -> Chip(l, ratio == r) { ratio = r } }
                        Chip("Temizle", false) { subject = null }
                    }
                    Text(
                        "Arazi yüksekliği hesaba katılmaz; ufukta dağ varsa Ay birkaç dakika geç görünür.",
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Text("© OpenStreetMap katkıda bulunanlar", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
    }
}

@Suppress("unused") private fun angDeg(h: Double, d: Double) = Math.toDegrees(atan(h / d))
@Suppress("unused") private val spacer = Modifier.width(1.dp)

package com.eirahoutmoss.nexsky

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val TR: Locale = Locale("tr")

fun hhmm(t: Time?): String = t?.let { SimpleDateFormat("HH:mm", TR).format(Date(it.millis())) } ?: "—"
fun dayLabel(millis: Long): String = SimpleDateFormat("d MMMM EEEE", TR).format(Date(millis))
fun shortDay(t: Time?): String = t?.let { SimpleDateFormat("d MMM HH:mm", TR).format(Date(it.millis())) } ?: "—"

/** Local midnight of the day containing [millis]. */
fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** App-wide state: where the user is, what time it is, what they are looking for. */
class AppState(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("nexsky", Context.MODE_PRIVATE)
    val lat = mutableDoubleStateOf(prefs.getFloat("lat", 37.7648f).toDouble())   // Adıyaman until a fix
    val lon = mutableDoubleStateOf(prefs.getFloat("lon", 38.2786f).toDouble())
    val heightM = mutableDoubleStateOf(prefs.getFloat("h", 670f).toDouble())
    val located = mutableStateOf(prefs.contains("lat"))
    val now = mutableLongStateOf(System.currentTimeMillis())
    val target = mutableStateOf<SkyObject?>(null)
    val planDay = mutableLongStateOf(startOfDay(System.currentTimeMillis()))
    val tab = mutableIntStateOf(0)
    val plannerTab = mutableIntStateOf(0)

    fun observer(): Observer = Sky.observer(lat.doubleValue, lon.doubleValue, heightM.doubleValue.coerceAtLeast(0.0))
    fun time(): Time = timeOf(now.longValue)

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val locListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) { setLocation(loc) }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    private fun setLocation(loc: Location) {
        lat.doubleValue = loc.latitude; lon.doubleValue = loc.longitude
        if (loc.hasAltitude()) heightM.doubleValue = loc.altitude
        located.value = true
        prefs.edit().putFloat("lat", loc.latitude.toFloat()).putFloat("lon", loc.longitude.toFloat())
            .putFloat("h", heightM.doubleValue.toFloat()).apply()
    }

    /** Low-power location: last known fix, then occasional updates. */
    @SuppressLint("MissingPermission")
    fun startLocation() {
        try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull {
                try { lm.getLastKnownLocation(it) } catch (_: Exception) { null }
            }.maxByOrNull { it.time }?.let { setLocation(it) }
            for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 60_000L, 200f, locListener, Looper.getMainLooper())
            }
        } catch (_: Exception) {}
    }

    fun stopLocation() { try { lm.removeUpdates(locListener) } catch (_: Exception) {} }

    /** Magnetic declination here (true = magnetic + declination). */
    fun declination(): Float = GeomagneticField(
        lat.doubleValue.toFloat(), lon.doubleValue.toFloat(), heightM.doubleValue.toFloat(), System.currentTimeMillis(),
    ).declination
}

/** Phone orientation from the rotation-vector sensor (device → world East/North/Up, magnetic). */
class Orientation(ctx: Context) : SensorEventListener {
    private val sm = ctx.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val available get() = sensor != null
    /** Row-major 3×3; recomposes the sky view on every change. */
    val matrix = mutableStateOf(floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f))
    private val tmp = FloatArray(9)
    private var smooth: FloatArray? = null

    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(tmp, e.values)
        val s = smooth ?: tmp.copyOf().also { smooth = it }
        for (i in 0 until 9) s[i] += (tmp[i] - s[i]) * 0.25f
        matrix.value = s.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Compass heading of the back camera (degrees, magnetic). */
    fun heading(m: FloatArray = matrix.value): Float {
        // Camera looks along device −Z: world = −(column 2).
        val e = -m[2]; val n = -m[5]
        return ((Math.toDegrees(kotlin.math.atan2(e.toDouble(), n.toDouble())).toFloat()) + 360f) % 360f
    }
}

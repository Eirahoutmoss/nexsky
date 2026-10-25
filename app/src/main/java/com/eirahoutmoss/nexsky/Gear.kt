package com.eirahoutmoss.nexsky

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * A camera body. [fixedEq] is set for cameras with a built-in zoom lens
 * (35 mm-equivalent range); interchangeable-lens bodies take a [Lens].
 */
data class Body(
    val name: String,
    val group: String,
    val sensorW: Double,
    val sensorH: Double,
    val fixedEq: ClosedFloatingPointRange<Double>? = null,
) {
    val crop: Double get() = 43.267 / sqrt(sensorW * sensorW + sensorH * sensorH)

    /** Field of view (degrees, long × short side) at a real focal length. */
    fun fovReal(f: Double): Pair<Double, Double> =
        Math.toDegrees(2 * atan(sensorW / (2 * f))) to Math.toDegrees(2 * atan(sensorH / (2 * f)))
}

/** A lens or telescope: real focal range in mm. */
data class Lens(val name: String, val group: String, val minF: Double, val maxF: Double) {
    val zoom get() = maxF > minF
}

private const val FF_W = 36.0; private const val FF_H = 24.0
private const val CANON_W = 22.3; private const val CANON_H = 14.9
private const val APSC_W = 23.5; private const val APSC_H = 15.6
private const val M43_W = 17.3; private const val M43_H = 13.0
private const val S23_W = 6.17; private const val S23_H = 4.55   // 1/2.3"
private const val ONE_W = 13.2; private const val ONE_H = 8.8      // 1"

val BODIES = listOf(
    Body("Nikon Coolpix P1000", "Süper zoom", S23_W, S23_H, 24.0..3000.0),
    Body("Nikon Coolpix P950", "Süper zoom", S23_W, S23_H, 24.0..2000.0),
    Body("Canon PowerShot SX70 HS", "Süper zoom", S23_W, S23_H, 21.0..1365.0),
    Body("Panasonic Lumix FZ82", "Süper zoom", S23_W, S23_H, 20.0..1200.0),
    Body("Sony RX10 IV", "Süper zoom", ONE_W, ONE_H, 24.0..600.0),
    Body("Panasonic Lumix FZ1000 II", "Süper zoom", ONE_W, ONE_H, 25.0..400.0),
    Body("Canon EOS R6 Mark II", "Tam kare", FF_W, FF_H),
    Body("Canon EOS R5", "Tam kare", FF_W, FF_H),
    Body("Canon EOS 6D Mark II", "Tam kare", FF_W, FF_H),
    Body("Nikon Z6 III", "Tam kare", FF_W, FF_H),
    Body("Nikon Z8", "Tam kare", FF_W, FF_H),
    Body("Nikon D850", "Tam kare", FF_W, FF_H),
    Body("Sony A7 IV", "Tam kare", FF_W, FF_H),
    Body("Sony A7R V", "Tam kare", FF_W, FF_H),
    Body("Canon EOS R7", "APS-C", CANON_W, CANON_H),
    Body("Canon EOS R10", "APS-C", CANON_W, CANON_H),
    Body("Canon EOS 90D", "APS-C", CANON_W, CANON_H),
    Body("Canon EOS 250D", "APS-C", CANON_W, CANON_H),
    Body("Canon EOS 2000D", "APS-C", CANON_W, CANON_H),
    Body("Nikon D3500", "APS-C", APSC_W, APSC_H),
    Body("Nikon D7500", "APS-C", APSC_W, APSC_H),
    Body("Nikon Z50", "APS-C", APSC_W, APSC_H),
    Body("Sony A6400", "APS-C", APSC_W, APSC_H),
    Body("Sony A6700", "APS-C", APSC_W, APSC_H),
    Body("Fujifilm X-T5", "APS-C", APSC_W, APSC_H),
    Body("Fujifilm X-S20", "APS-C", APSC_W, APSC_H),
    Body("OM System OM-1", "Mikro 4/3", M43_W, M43_H),
    Body("Panasonic Lumix G9 II", "Mikro 4/3", M43_W, M43_H),
    Body("ZWO ASI585MC", "Astro kamera", 11.2, 6.3),
    Body("ZWO ASI533MC Pro", "Astro kamera", 11.31, 11.31),
    Body("ZWO ASI2600MC Pro", "Astro kamera", 23.5, 15.7),
)

val LENSES = listOf(
    Lens("14 mm geniş açı (Samanyolu)", "Objektif", 14.0, 14.0),
    Lens("24 mm f/1.4", "Objektif", 24.0, 24.0),
    Lens("50 mm f/1.8", "Objektif", 50.0, 50.0),
    Lens("18–55 mm kit", "Objektif", 18.0, 55.0),
    Lens("24–70 mm", "Objektif", 24.0, 70.0),
    Lens("70–200 mm", "Objektif", 70.0, 200.0),
    Lens("55–250 mm (Canon)", "Objektif", 55.0, 250.0),
    Lens("70–300 mm", "Objektif", 70.0, 300.0),
    Lens("100–400 mm", "Objektif", 100.0, 400.0),
    Lens("150–600 mm (Sigma / Tamron)", "Objektif", 150.0, 600.0),
    Lens("180–600 mm (Nikon Z)", "Objektif", 180.0, 600.0),
    Lens("200–600 mm (Sony)", "Objektif", 200.0, 600.0),
    Lens("200–800 mm (Canon RF)", "Objektif", 200.0, 800.0),
    Lens("500 mm f/5.6 PF (Nikon)", "Objektif", 500.0, 500.0),
    Lens("800 mm f/11 (Canon RF)", "Objektif", 800.0, 800.0),
    Lens("RedCat 51 (250 mm)", "Teleskop", 250.0, 250.0),
    Lens("Evostar 72ED (420 mm)", "Teleskop", 420.0, 420.0),
    Lens("Evostar 80ED (600 mm)", "Teleskop", 600.0, 600.0),
    Lens("Newton 130/650", "Teleskop", 650.0, 650.0),
    Lens("Newton 150/750", "Teleskop", 750.0, 750.0),
    Lens("C8 + 0,63× düşürücü (1280 mm)", "Teleskop", 1280.0, 1280.0),
    Lens("Maksutov 102 (1300 mm)", "Teleskop", 1300.0, 1300.0),
    Lens("Maksutov 127 (1500 mm)", "Teleskop", 1500.0, 1500.0),
    Lens("Celestron C8 (2032 mm)", "Teleskop", 2032.0, 2032.0),
    Lens("Celestron C11 (2800 mm)", "Teleskop", 2800.0, 2800.0),
)

/** The phone's own back cameras, read from Camera2 (sensor size and focal lengths). */
data class PhoneCam(val id: String, val body: Body, val lens: Lens, val eq: Double)

fun phoneCameras(ctx: Context): List<PhoneCam> = try {
    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cm.cameraIdList.mapNotNull { id ->
        val ch = cm.getCameraCharacteristics(id)
        if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
        val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@mapNotNull null
        val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()?.toDouble() ?: return@mapNotNull null
        val w = maxOf(size.width, size.height).toDouble(); val h = minOf(size.width, size.height).toDouble()
        val body = Body("Bu telefon", "Telefon", w, h)
        val eq = f * body.crop
        val kind = when { eq < 20 -> "Ultra geniş"; eq < 35 -> "Ana kamera"; eq < 100 -> "Telefoto"; else -> "Periskop telefoto" }
        PhoneCam(id, body, Lens("$kind (%.0f mm eşdeğer)".format(TR, eq), "Telefon", f, f), eq)
    }.distinctBy { "%.1f".format(it.eq) }.sortedBy { it.eq }
} catch (_: Exception) {
    emptyList()
}

/** Vertical field of view (portrait screen = sensor's long side) of the main back camera. */
fun mainCameraVerticalFov(ctx: Context): Float {
    val main = phoneCameras(ctx).firstOrNull { it.lens.name.startsWith("Ana") } ?: phoneCameras(ctx).firstOrNull()
    return main?.let { Math.toDegrees(2 * atan(it.body.sensorW / (2 * it.lens.minF))).toFloat() } ?: 65f
}

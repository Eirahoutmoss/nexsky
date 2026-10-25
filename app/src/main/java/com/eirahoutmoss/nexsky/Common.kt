package com.eirahoutmoss.nexsky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sqrt

/** Colour swatch per object type, used for list icons and sky markers. */
fun typeColor(o: SkyObject): Color = when (o.type) {
    ObjType.SUN -> Color(0xFFFFC94A)
    ObjType.MOON -> Color(0xFFDCE3EE)
    ObjType.PLANET -> when (o.id) {
        "mars" -> Color(0xFFE4703F); "jupiter" -> Color(0xFFE8C39A); "saturn" -> Color(0xFFE9D08C)
        "venus" -> Color(0xFFFFF1C2); "mercury" -> Color(0xFFB9B1A6); "uranus" -> Color(0xFF9BE3E8)
        else -> Color(0xFF7FA6FF)
    }
    ObjType.STAR -> Color(0xFFFFFFFF)
    ObjType.DSO -> if (o.kindLabel == "Galaksi") Color(0xFFB79CFF) else if (o.kindLabel.contains("küme")) Color(0xFF8FD3FF) else Color(0xFFFF8AC2)
    ObjType.CONSTELLATION -> Color(0xFF6E86B8)
}

@Composable
fun ObjIcon(o: SkyObject, size: Dp = 34.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = typeColor(o)
            when (o.type) {
                ObjType.MOON -> drawMoon(center, this.size.minDimension / 2, 150.0)
                ObjType.PLANET, ObjType.SUN -> {
                    drawCircle(Brush.radialGradient(listOf(Color.White, c, c.copy(alpha = 0.6f)), center, this.size.minDimension / 2), this.size.minDimension / 2.3f)
                    if (o.id == "saturn") drawOval(c, Offset(2f, this.size.height * 0.42f), androidx.compose.ui.geometry.Size(this.size.width - 4f, this.size.height * 0.16f), alpha = 0.8f)
                }
                ObjType.STAR -> drawStar(center, this.size.minDimension / 2.2f, c)
                ObjType.DSO -> {
                    drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0.0f)), center, this.size.minDimension / 2), this.size.minDimension / 2)
                    drawCircle(Color.White, 2.5f, center)
                }
                ObjType.CONSTELLATION -> {
                    val p = listOf(Offset(0.15f, 0.7f), Offset(0.4f, 0.35f), Offset(0.65f, 0.55f), Offset(0.88f, 0.2f)).map { Offset(it.x * this.size.width, it.y * this.size.height) }
                    for (i in 1 until p.size) drawLine(c, p[i - 1], p[i], 2f)
                    p.forEach { drawCircle(Color.White, 2.5f, it) }
                }
            }
        }
    }
}

/** Four-point sparkle. */
fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val k = r * 0.22f
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticBezierTo(c.x + k, c.y - k, c.x + r, c.y); quadraticBezierTo(c.x + k, c.y + k, c.x, c.y + r)
        quadraticBezierTo(c.x - k, c.y + k, c.x - r, c.y); quadraticBezierTo(c.x - k, c.y - k, c.x, c.y - r); close()
    }
    drawPath(p, color)
}

/**
 * A simple moon: grey disc with darker maria, shadowed by phase.
 * [phaseDeg]: 0 new, 90 first quarter, 180 full, 270 last quarter.
 * Northern-hemisphere view: waxing moon is lit on the right.
 */
fun DrawScope.drawMoon(c: Offset, r: Float, phaseDeg: Double) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF1F1EC), Color(0xFFBFC1C0), Color(0xFF8E9193)), c + Offset(-r * 0.25f, -r * 0.25f), r * 1.6f), r, c)
    val maria = listOf(Triple(-0.25f, -0.3f, 0.28f), Triple(0.15f, -0.38f, 0.2f), Triple(0.3f, -0.05f, 0.22f), Triple(-0.35f, 0.15f, 0.25f), Triple(0.05f, 0.3f, 0.18f), Triple(-0.05f, -0.05f, 0.15f))
    for ((x, y, s) in maria) drawCircle(Color(0x33505660), r * s, c + Offset(x * r, y * r))
    // Terminator: lit fraction from phase.
    val ph = ((phaseDeg % 360) + 360) % 360
    val k = cos(Math.toRadians(ph)).toFloat()           // 1 new … −1 full
    val shadow = Color(0xE6070C1C)
    val waxing = ph < 180
    val path = Path()
    val steps = 48
    // Dark limb half
    for (i in 0..steps) {
        val a = Math.PI * i / steps - Math.PI / 2
        val x = (if (waxing) -1 else 1) * cos(a).toFloat() * r
        val y = kotlin.math.sin(a).toFloat() * r
        if (i == 0) path.moveTo(c.x + x, c.y + y) else path.lineTo(c.x + x, c.y + y)
    }
    // Terminator ellipse back up
    for (i in steps downTo 0) {
        val a = Math.PI * i / steps - Math.PI / 2
        val x = (if (waxing) 1 else -1) * k * cos(a).toFloat() * r
        val y = kotlin.math.sin(a).toFloat() * r
        path.lineTo(c.x + x, c.y + y)
    }
    path.close()
    drawPath(path, shadow)
}

@Composable
fun KV(k: String, v: String, color: Color = TextHi) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, color = Muted, fontSize = 14.sp, modifier = Modifier.width(140.dp))
        Text(v, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatBox(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0E1A33)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        sub?.let { Text(it, color = Muted, fontSize = 11.sp) }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Accent else Color(0xFF13233A))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SegTabs(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E1A33)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, t ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (i == selected) Accent else Color.Transparent)
                    .clickable { onPick(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

fun fmtAngle(deg: Double): String = when {
    deg >= 1 -> "%.2f°".format(TR, deg)
    deg * 60 >= 1 -> "%.1f′".format(TR, deg * 60)
    else -> "%.0f″".format(TR, deg * 3600)
}

fun fmtDist(o: SkyObject, p: SkyPos): String? {
    val au = p.distAu ?: return null
    return if (o.type == ObjType.MOON) "%,.0f km".format(TR, au * AU_KM) else "%.2f AB".format(TR, au)
}

@Suppress("unused") private fun hyp(a: Float, b: Float) = sqrt(a * a + b * b)
@Suppress("unused") private val dot = CircleShape

/** A labelled drop-down with grouped items. */
@Composable
fun <T> Dropdown(
    label: String,
    items: List<T>,
    selected: T?,
    text: (T) -> String,
    group: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(TR), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.size(6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E1A33))
                    .border(1.dp, Line, RoundedCornerShape(12.dp)).clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.let(text) ?: "Seçin", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null, tint = Muted)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.background(Color(0xFF0E1A33)).heightIn(max = 460.dp),
            ) {
                var last: String? = null
                items.forEach { item ->
                    val g = group(item)
                    if (g != last) {
                        last = g
                        Text(g.uppercase(TR), color = Accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(text(item), color = if (item == selected) Accent2 else TextHi, fontSize = 14.sp) },
                        onClick = { onPick(item); open = false },
                    )
                }
            }
        }
    }
}

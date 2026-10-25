package com.eirahoutmoss.nexsky

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HIGHLIGHTS = listOf("M42", "M31", "M45", "M13", "M57", "M27", "M44", "NGC 869", "M8", "M51", "M81", "HIP32349", "HIP11767", "HIP91262", "HIP27989", "HIP24436")

@Composable
fun SearchScreen(act: MainActivity) {
    val st = act.state
    var q by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<SkyObject?>(null) }
    detail?.let { d ->
        BackHandler { detail = null }
        ObjectDetail(act, d) { detail = null }
        return
    }
    val obs = st.observer()
    val t = st.time()
    // Refresh positions every 30 s, not every tick.
    val minute = st.now.longValue / 30_000
    val list: List<Pair<SkyObject, SkyPos>> = remember(q, minute, st.lat.doubleValue, st.lon.doubleValue) {
        val src = if (q.isBlank()) {
            SkyCatalog.solarSystem + HIGHLIGHTS.mapNotNull { SkyCatalog.byId(it) }
        } else SkyCatalog.search(q)
        val withPos = src.map { it to Sky.position(it, t, obs) }
        if (q.isBlank()) withPos.sortedWith(compareBy({ if (it.second.altDeg > 0) 0 else 1 }, { it.first.type.ordinal }, { -it.second.altDeg }))
        else withPos
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0E1A33))
                .border(1.dp, Line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Muted)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Mars, Jüpiter, Orion Bulutsusu, M42, Ülker…", color = Muted, fontSize = 15.sp)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(color = TextHi, fontSize = 15.sp, fontFamily = Inter),
                    cursorBrush = SolidColor(Accent2), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (q.isNotEmpty()) Icon(Icons.Filled.Close, "Temizle", tint = Muted, modifier = Modifier.clickable { q = "" })
        }
        SectionLabel(if (q.isBlank()) "Şu an gökyüzünde" else "${list.size} sonuç")
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.first.id }) { (o, p) ->
                Row(
                    Modifier.fillMaxWidth().clickable { detail = o }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ObjIcon(o)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.name, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            o.kindLabel + " · " + if (p.altDeg > 0) "%.0f° yüksekte, %s".format(TR, p.altDeg, dirName(p.azDeg).lowercase(TR)) else "ufkun altında",
                            color = if (p.altDeg > 0) Muted else Color(0xFF5E6E88), fontSize = 12.sp,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun ActionTile(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF13305A)).clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Accent2)
        Spacer(Modifier.height(4.dp))
        Text(text, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ObjectDetail(act: MainActivity, o: SkyObject, onBack: () -> Unit) {
    val st = act.state
    val obs = st.observer()
    val p = Sky.position(o, st.time(), obs)
    var rs by remember(o.id) { mutableStateOf<RiseSet?>(null) }
    LaunchedEffect(o.id, st.lat.doubleValue) {
        rs = withContext(Dispatchers.Default) { Sky.riseSet(o, timeOf(System.currentTimeMillis() - 12 * 3600_000L), obs) }
    }
    val con = remember(o.id) { if (o.type == ObjType.CONSTELLATION) "" else Sky.constellationName(p.raHours, p.decDeg) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Spacer(Modifier.width(8.dp))
            ObjIcon(o, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(o.name, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(o.kindLabel + if (con.isNotEmpty()) " · $con takımyıldızı" else "", color = Muted, fontSize = 13.sp)
            }
        }
        Panel {
            SectionLabel("Şu an")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("Yön", "%.0f°".format(TR, p.azDeg), dirName(p.azDeg), Modifier.weight(1f))
                StatBox("Yükseklik", "%.0f°".format(TR, p.altDeg), if (p.altDeg > 0) "görünür" else "ufkun altında", Modifier.weight(1f))
            }
        }
        Panel {
            SectionLabel("Bugün")
            Spacer(Modifier.height(6.dp))
            val r = rs
            when {
                r == null -> Text("Hesaplanıyor…", color = Muted)
                r.alwaysUp -> Text("Bu enlemde hiç batmaz (hep ufkun üstünde).", color = TextHi)
                r.neverUp -> Text("Bu enlemden hiç görünmez.", color = Warn)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("Doğuş", hhmm(r.rise), r.rise?.let { dirName(Sky.position(o, it, obs).azDeg) }, Modifier.weight(1f))
                    StatBox("En yüksek", hhmm(r.transit), r.transitAlt?.let { "%.0f°".format(TR, it) }, Modifier.weight(1f))
                    StatBox("Batış", hhmm(r.set), r.set?.let { dirName(Sky.position(o, it, obs).azDeg) }, Modifier.weight(1f))
                }
            }
        }
        Panel {
            SectionLabel("Bilgi")
            Spacer(Modifier.height(6.dp))
            p.mag?.let { KV("Parlaklık (kadir)", "%.1f".format(TR, it)) }
            p.diameterArcmin?.let { KV("Görünen büyüklük", fmtAngle(it / 60)) }
            fmtDist(o, p)?.let { KV("Uzaklık", it) }
            KV("Sağ açıklık / dik açıklık", "%.2fs / %.2f°".format(TR, p.raHours, p.decDeg))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Gökyüzünde", Icons.Filled.AutoAwesome, Modifier.weight(1f)) { st.target.value = o; st.tab.intValue = 1 }
            ActionTile("Kadraj", Icons.Filled.CameraAlt, Modifier.weight(1f)) { st.target.value = o; st.plannerTab.intValue = 2; st.tab.intValue = 2 }
            ActionTile("Haritada", Icons.Filled.Map, Modifier.weight(1f)) { st.target.value = o; st.tab.intValue = 3 }
        }
        Spacer(Modifier.size(8.dp))
    }
}

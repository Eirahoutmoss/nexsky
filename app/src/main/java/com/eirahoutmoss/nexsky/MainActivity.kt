package com.eirahoutmoss.nexsky

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    lateinit var state: AppState
    lateinit var orientation: Orientation
    private val permTick = mutableIntStateOf(0)
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick.intValue++
        state.startLocation()
    }

    fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(this)
        orientation = Orientation(this)
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            askPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        setContent {
            NexTheme {
                permTick.intValue
                App(this)
            }
        }
    }

    override fun onResume() { super.onResume(); state.startLocation() }
    override fun onPause() { state.stopLocation(); orientation.stop(); super.onPause() }
}

private data class Dest(val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("Arama", Icons.Filled.Search),
    Dest("Gökyüzü", Icons.Filled.AutoAwesome),
    Dest("Planlayıcı", Icons.Outlined.EventNote),
    Dest("Harita", Icons.Filled.Map),
)

@Composable
private fun App(act: MainActivity) {
    val st = act.state
    val loaded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            SkyCatalog.load(act)
            MoonImage.load(act)
            MoonImage.setTime(timeOf(System.currentTimeMillis()))
        }
        loaded.value = true
    }
    // One clock for the whole app.
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            st.now.longValue = System.currentTimeMillis()
            if (n++ % 600 == 0) MoonImage.setTime(timeOf(st.now.longValue)) // libration drifts slowly
            delay(1000)
        }
    }
    BackHandler(enabled = st.showAbout.value) { st.showAbout.value = false }
    BackHandler(enabled = !st.showAbout.value && st.tab.intValue != 0) { st.tab.intValue = 0 }
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg2, Bg))).statusBarsPadding().navigationBarsPadding(),
    ) {
        TopBrandBar {
            Text(
                if (st.located.value) "%.2f°, %.2f°".format(TR, st.lat.doubleValue, st.lon.doubleValue) else "konum bekleniyor",
                color = Muted, fontSize = 12.sp,
            )
            Icon(
                Icons.Outlined.Info, "Hakkında", tint = if (st.showAbout.value) Accent2 else TextHi,
                modifier = Modifier.padding(start = 10.dp).clickable { st.showAbout.value = !st.showAbout.value },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            if (st.showAbout.value) {
                AboutScreen(act) { st.showAbout.value = false }
            } else if (loaded.value) {
                when (st.tab.intValue) {
                    0 -> SearchScreen(act)
                    1 -> SkyScreen(act)
                    2 -> PlannerScreen(act)
                    else -> MapScreen(act)
                }
            } else {
                Text("Gök kataloğu yükleniyor…", color = Muted, modifier = Modifier.background(Color.Transparent))
            }
        }
        NavigationBar(containerColor = Color(0xFF0A1226), tonalElevation = 0.dp) {
            DESTS.forEachIndexed { i, d ->
                NavigationBarItem(
                    selected = st.tab.intValue == i,
                    onClick = { st.tab.intValue = i; st.showAbout.value = false },
                    icon = { Icon(d.icon, d.label) },
                    label = { Text(d.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent2, selectedTextColor = Accent2,
                        indicatorColor = Color(0xFF13305A), unselectedIconColor = Muted, unselectedTextColor = Muted,
                    ),
                )
            }
        }
    }
}

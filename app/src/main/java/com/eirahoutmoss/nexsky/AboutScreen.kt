package com.eirahoutmoss.nexsky

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface UpdState {
    data object Idle : UpdState
    data object Checking : UpdState
    data object UpToDate : UpdState
    data class Available(val info: UpdateInfo) : UpdState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdState
    data class Failed(val msg: String) : UpdState
}

@Composable
private fun Btn(text: String, primary: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (primary) Accent else Color(0xFF13305A))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun AboutScreen(act: MainActivity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var upd by remember { mutableStateOf<UpdState>(UpdState.Idle) }
    val version = BuildConfig.VERSION_NAME

    fun check() {
        upd = UpdState.Checking
        scope.launch {
            upd = try {
                withContext(Dispatchers.IO) { Updater.check(version) }?.let { UpdState.Available(it) } ?: UpdState.UpToDate
            } catch (e: Exception) {
                UpdState.Failed("Sunucuya ulaşılamadı: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun install(info: UpdateInfo) {
        if (!Updater.canInstall(act)) {
            Updater.openInstallPermission(act)
            upd = UpdState.Failed("Önce \"Bu kaynaktan yüklemeye izin ver\" seçeneğini açın, sonra tekrar deneyin.")
            return
        }
        upd = UpdState.Downloading(info, 0f)
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    Updater.download(act, info) { p -> scope.launch { upd = UpdState.Downloading(info, p) } }
                }
                Updater.install(act, apk)
                upd = UpdState.Available(info)
            } catch (e: Exception) {
                upd = UpdState.Failed(e.message ?: "İndirme başarısız.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = TextHi, modifier = Modifier.clickable(onClick = onBack).padding(4.dp))
            Spacer(Modifier.width(8.dp))
            Text("Hakkında", color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.nexsky_logo), null, Modifier.size(96.dp))
            Spacer(Modifier.height(10.dp))
            Wordmark(30.sp)
            Text("GÖKYÜZÜ SENİN REHBERİN", color = Muted, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(4.dp))
            Text("Sürüm $version", color = Muted, fontSize = 13.sp)
        }
        Panel {
            Text(
                "Ay ve gökyüzü fotoğrafçıları için ücretsiz, açık kaynak ve internetsiz çalışan gökyüzü rehberi ve çekim planlayıcı. " +
                    "Gezegenleri, yıldızları, bulutsuları ve galaksileri bul; Ayın doğuşunu, evresini ve büyüklüğünü planla; " +
                    "makinenin kadrajında nasıl görüneceğini çekimden önce gör.",
                color = TextHi, fontSize = 14.sp, textAlign = TextAlign.Start,
            )
        }
        Panel {
            SectionLabel("Güncelleme")
            Spacer(Modifier.height(8.dp))
            when (val u = upd) {
                UpdState.Idle -> Btn("Güncellemeleri denetle") { check() }
                UpdState.Checking -> Text("GitHub'da yeni sürüm aranıyor…", color = Muted, fontSize = 14.sp)
                UpdState.UpToDate -> {
                    Text("NexSky güncel (sürüm $version).", color = Good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp)); Btn("Tekrar denetle", false) { check() }
                }
                is UpdState.Available -> {
                    Text("Yeni sürüm: ${u.info.version}", color = Accent2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (u.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(u.info.notes.take(600), color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("İndir ve kur") { install(u.info) }
                        Btn("Sürüm sayfası", false) { Updater.openPage(act, u.info.pageUrl) }
                    }
                }
                is UpdState.Downloading -> {
                    Text("İndiriliyor: ${u.info.version} · %%%.0f".format(TR, u.progress * 100), color = TextHi, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth(), color = Accent2, trackColor = Line)
                }
                is UpdState.Failed -> {
                    Text(u.msg, color = Warn, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp)); Btn("Tekrar dene", false) { check() }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Güncellemeler GitHub'daki resmî sürümden indirilir; dosya bütünlüğü ve imza anahtarı denetlenir, kurulumu Android sizden onaylamanızı ister.",
                color = Muted, fontSize = 12.sp,
            )
        }
        Panel {
            SectionLabel("Geliştiren")
            Spacer(Modifier.height(6.dp))
            Text("Hasan Güler", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Nex uygulama ailesi", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("github.com/${Updater.REPO}", color = Accent2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { Updater.openPage(act, "https://github.com/${Updater.REPO}") })
        }
        Panel {
            SectionLabel("Gizlilik")
            Spacer(Modifier.height(6.dp))
            Text(
                "Konumun yalnızca telefonda gökyüzü hesabı için kullanılır, hiçbir yere gönderilmez. Hesap yok, reklam yok, izleme yok. " +
                    "İnternet yalnızca harita altlığı ve güncelleme denetimi için kullanılır.",
                color = TextHi, fontSize = 13.sp,
            )
        }
        Panel {
            SectionLabel("Lisans ve teşekkür")
            Spacer(Modifier.height(6.dp))
            Text("NexSky, MIT lisansıyla açık kaynaktır. © 2026 Hasan Güler", color = TextHi, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Astronomy Engine — Don Cross (MIT): gök cisimlerinin konum hesapları",
                "d3-celestial — Olaf Frohn (BSD-3): yıldız, takımyıldız ve derin uzay katalogları",
                "OpenStreetMap katkıda bulunanlar (ODbL): harita verisi",
                "osmdroid (Apache 2.0): harita görünümü",
                "Inter — Rasmus Andersson (SIL OFL 1.1): yazı tipi",
            ).forEach { Text("• $it", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

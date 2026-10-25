package com.eirahoutmoss.nexsky

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer release on GitHub. */
data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val sha256: String?, val pageUrl: String)

/**
 * Self-update from GitHub releases. The user taps "Güncellemeleri denetle";
 * if a newer release has an APK, it is downloaded, checked (SHA-256 from
 * GitHub, and the same signing key as the installed app) and handed to the
 * system installer, which asks the user to confirm.
 */
object Updater {
    const val REPO = "Eirahoutmoss/NexSky"
    private const val APK_PREFIX = "NexSky"

    /** "0.10.2" > "0.9.9"; tags may start with "v". */
    fun newer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Blocking; call off the main thread. Null = up to date. Throws on network errors. */
    fun check(current: String): UpdateInfo? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "$APK_PREFIX/$current")
        try {
            if (c.responseCode == 404) return null // no release yet
            if (c.responseCode != 200) throw IllegalStateException("GitHub yanıtı: ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = o.optString("tag_name")
            if (!newer(tag, current)) return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val digest = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
                return UpdateInfo(
                    tag.removePrefix("v"), o.optString("body"), a.getString("browser_download_url"),
                    a.optLong("size"), digest, o.optString("html_url"),
                )
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** Blocking download with progress (0..1). Returns the verified APK. */
    fun download(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit): File {
        val f = File(ctx.cacheDir, "$APK_PREFIX-update.apk")
        f.delete()
        var url = URL(info.apkUrl)
        var c: HttpURLConnection
        // GitHub redirects to its object storage; follow by hand across hosts.
        var hops = 0
        while (true) {
            c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", APK_PREFIX)
            val code = c.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); continue }
            if (code != 200) throw IllegalStateException("İndirme hatası: $code")
            break
        }
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.sizeBytes
        val md = MessageDigest.getInstance("SHA-256")
        c.inputStream.use { inp ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); md.update(buf, 0, n); done += n
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        c.disconnect()
        info.sha256?.let { want ->
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(want, ignoreCase = true)) { f.delete(); throw SecurityException("İndirilen dosya bozuk (SHA-256 uyuşmuyor).") }
        }
        if (!sameSigner(ctx, f)) { f.delete(); throw SecurityException("İndirilen APK farklı bir anahtarla imzalanmış; kurulmadı.") }
        return f
    }

    /** The update must be signed with the same key as the installed app. */
    @Suppress("DEPRECATION")
    private fun sameSigner(ctx: Context, apk: File): Boolean {
        val pm = ctx.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val mine = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
                val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners
                // Some Android versions cannot read an archive's signers; the
                // system installer still refuses a different key in that case.
                if (mine == null || theirs == null) true
                else mine.map { it.toCharsString() }.toSet() == theirs.map { it.toCharsString() }.toSet()
            } else true
        } catch (_: Exception) {
            false
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("$APK_PREFIX.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            s.commit(pi.intentSender)
        }
    }

    fun openPage(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "bilinmeyen hata"
                Toast.makeText(ctx, "Güncelleme kurulamadı: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}

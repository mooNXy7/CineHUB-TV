package br.com.cinehub.tv

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Small, isolated OTA updater for the Kotlin application.
 * The legacy app keeps using update.json; Kotlin reads update-kotlin.json.
 */
class OtaUpdateManager(private val activity: AppCompatActivity) {
    companion object {
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/mooNXy7/CineHUB-TV/main/update-kotlin.json"
        private const val APK_FILE = "cinehub-tv-kotlin-update.apk"
        private const val PREFS = "cinehub-tv-ota"
        private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }

    private val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private var downloadId = preferences.getLong("download_id", -1L)
    private var settingsOpenedForPermission = false
    private var closed = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (completedId == downloadId) processCompletedDownload()
        }
    }

    init {
        ContextCompat.registerReceiver(
            activity,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun checkForUpdate(force: Boolean = false, onResult: ((String) -> Unit)? = null) {
        if (closed) return
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong("last_check", 0L)
        if (!force && now - lastCheck < CHECK_INTERVAL_MS) return

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Servidor de atualização indisponível.")
                }
                val json = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                preferences.edit().putLong("last_check", now).apply()

                val latestCode = json.optInt("versionCode", 0)
                val latestName = json.optString("version", "")
                val apkUrl = json.optString("apkUrl", "")
                val sha256 = json.optString("sha256", "")
                val installedCode = activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode

                if (latestCode > installedCode && latestName.isNotBlank() &&
                    apkUrl.startsWith("https://") && sha256.matches(Regex("(?i)^[a-f0-9]{64}$"))
                ) {
                    activity.runOnUiThread {
                        onResult?.invoke("Atualização $latestName disponível.")
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            AlertDialog.Builder(activity)
                                .setTitle("Atualização do CineHUB TV")
                                .setMessage("A versão $latestName está disponível. Deseja baixar e instalar agora?")
                                .setPositiveButton("Baixar e instalar") { _, _ ->
                                    downloadApk(apkUrl, sha256, latestName)
                                }
                                .setNegativeButton("Depois", null)
                                .show()
                        }
                    }
                } else if (force) {
                    activity.runOnUiThread { onResult?.invoke("Você já está usando a versão mais recente.") }
                }
            } catch (_: Exception) {
                if (force) {
                    activity.runOnUiThread {
                        onResult?.invoke("Não foi possível consultar as atualizações. Confira a internet e tente novamente.")
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun downloadApk(apkUrl: String, expectedSha256: String, versionName: String) {
        try {
            val target = apkFile()
            target.parentFile?.mkdirs()
            target.delete()
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("CineHUB TV $versionName")
                setDescription("Baixando atualização oficial…")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
                setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_FILE)
            }
            downloadId = (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            preferences.edit()
                .putLong("download_id", downloadId)
                .putString("expected_sha256", expectedSha256.lowercase())
                .putString("pending_install", "false")
                .apply()
            Toast.makeText(activity, "Download da atualização iniciado.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "Não foi possível iniciar o download da atualização.", Toast.LENGTH_LONG).show()
        }
    }

    private fun processCompletedDownload() {
        if (closed || downloadId < 0L) return
        executor.execute {
            try {
                val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                val status = cursor?.use {
                    if (it.moveToFirst()) it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    else DownloadManager.STATUS_FAILED
                } ?: DownloadManager.STATUS_FAILED

                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    if (status == DownloadManager.STATUS_FAILED) {
                        preferences.edit().putLong("download_id", -1L).apply()
                        activity.runOnUiThread {
                            Toast.makeText(activity, "O download falhou. Tente novamente em Ajustes.", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@execute
                }

                val file = apkFile()
                val expected = preferences.getString("expected_sha256", "").orEmpty()
                if (!file.isFile || expected.isBlank() || sha256(file) != expected.lowercase()) {
                    file.delete()
                    preferences.edit().putLong("download_id", -1L).putString("pending_install", "false").apply()
                    activity.runOnUiThread {
                        Toast.makeText(activity, "A verificação de integridade falhou. A instalação foi cancelada.", Toast.LENGTH_LONG).show()
                    }
                    return@execute
                }

                preferences.edit().putString("pending_install", "true").apply()
                activity.runOnUiThread { installOrRequestPermission() }
            } catch (_: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Não foi possível validar o arquivo baixado.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun resumeInstallIfReady() {
        if (closed) return
        if (preferences.getString("pending_install", "false") == "true") {
            val file = apkFile()
            if (file.isFile) installOrRequestPermission()
        } else if (downloadId >= 0L) {
            executor.execute {
                val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                val status = cursor?.use {
                    if (it.moveToFirst()) it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    else DownloadManager.STATUS_FAILED
                } ?: DownloadManager.STATUS_FAILED
                if (status == DownloadManager.STATUS_SUCCESSFUL) processCompletedDownload()
            }
        }
    }

    private fun installOrRequestPermission() {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            if (!settingsOpenedForPermission) {
                settingsOpenedForPermission = true
                AlertDialog.Builder(activity)
                    .setTitle("Permitir instalação do CineHUB TV")
                    .setMessage("Ative a permissão para instalar atualizações baixadas pelo próprio aplicativo. Depois, volte ao CineHUB TV.")
                    .setPositiveButton("Abrir configurações") { _, _ ->
                        try {
                            activity.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                            )
                        } catch (_: Exception) {
                            Toast.makeText(activity, "Ative a permissão de instalação de apps nas configurações do Android.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Agora não", null)
                    .show()
            }
            return
        }

        val file = apkFile()
        if (!file.isFile) return
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            preferences.edit().putString("pending_install", "false").putLong("download_id", -1L).apply()
            activity.startActivity(installIntent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Não foi possível abrir o instalador do Android.", Toast.LENGTH_LONG).show()
        }
    }

    private fun apkFile(): File =
        File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun close() {
        closed = true
        try {
            activity.unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
        }
        executor.shutdownNow()
    }
}

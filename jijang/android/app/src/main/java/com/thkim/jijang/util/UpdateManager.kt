package com.thkim.jijang.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.thkim.jijang.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 앱 안에서 새 버전 확인 → 다운로드 → 설치 화면 열기.
 * GitHub Pages의 jijang-version.json의 `code`가 설치된 앱의 versionCode보다 크면 업데이트 대상이다.
 * 데이터(Room DB 등)는 건드리지 않고, 설치는 안드로이드 설치 화면(서명 일치 필요)을 거친다.
 */
object UpdateManager {

    private const val VERSION_URL = "https://thkim3589-web.github.io/zero/apk/jijang-version.json"

    data class Info(val code: Int, val name: String, val notes: List<String>, val apkUrl: String)

    /** @param manual true면 "최신 버전입니다"/실패 안내도 토스트로 보여준다 (자동 확인은 조용히 넘어감) */
    fun check(activity: AppCompatActivity, manual: Boolean) {
        activity.lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { fetchInfo() }.getOrNull() }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            when {
                info == null -> if (manual) toast(activity, "업데이트 정보를 가져오지 못했습니다. 인터넷 연결을 확인해주세요")
                info.code > BuildConfig.VERSION_CODE -> showUpdateDialog(activity, info)
                else -> if (manual) toast(activity, "최신 버전입니다 (v${BuildConfig.VERSION_NAME})")
            }
        }
    }

    private fun fetchInfo(): Info {
        val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.useCaches = false
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val notes = json.optJSONArray("notes")
            return Info(
                code = json.getInt("code"),
                name = json.optString("version"),
                notes = (0 until (notes?.length() ?: 0)).map { notes!!.getString(it) },
                apkUrl = json.getString("apk")
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun showUpdateDialog(activity: AppCompatActivity, info: Info) {
        val message = buildString {
            append("새 버전 v${info.name}을 설치할 수 있습니다.\n(현재 v${BuildConfig.VERSION_NAME})")
            if (info.notes.isNotEmpty()) append("\n\n").append(info.notes.joinToString("\n") { "• $it" })
            append("\n\n입력한 현장 데이터는 그대로 유지됩니다.")
        }
        AlertDialog.Builder(activity)
            .setTitle("업데이트")
            .setMessage(message)
            .setPositiveButton("다운로드 후 설치") { _, _ -> download(activity, info) }
            .setNegativeButton("나중에", null)
            .show()
    }

    private fun download(activity: AppCompatActivity, info: Info) {
        // https만 허용 — 중간에 바뀐 파일을 설치하는 일을 줄인다
        if (!info.apkUrl.startsWith("https://")) {
            toast(activity, "안전하지 않은 다운로드 주소입니다")
            return
        }
        var cancelled = false
        val dialog = AlertDialog.Builder(activity)
            .setTitle("업데이트 다운로드")
            .setMessage("준비 중...")
            .setCancelable(false)
            .setNegativeButton("취소") { _, _ -> cancelled = true }
            .show()

        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(activity.cacheDir, "update").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, "jijang.apk")
                    val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    val total = conn.contentLengthLong
                    var done = 0L
                    var lastPercent = -1
                    conn.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buf = ByteArray(16 * 1024)
                            while (isActive && !cancelled) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        withContext(Dispatchers.Main) { dialog.setMessage("다운로드 중... $percent%") }
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    if (cancelled) { file.delete(); null }
                    else if (total > 0 && done != total) { file.delete(); error("다운로드가 중간에 끊겼습니다") }
                    else file
                }
            }
            dialog.dismiss()
            result.onSuccess { file -> if (file != null) install(activity, file) }
                .onFailure { toast(activity, "다운로드 실패: ${it.message}") }
        }
    }

    private fun install(activity: Activity, file: File) {
        // 안드로이드 8.0+: 이 앱에 "알 수 없는 앱 설치" 허용이 필요하다. 허용 화면으로 안내하고,
        // 다녀온 뒤 다시 "업데이트 확인"을 누르면 된다.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("설치 허용 필요")
                .setMessage("업데이트를 설치하려면 이 앱의 '알 수 없는 앱 설치'를 허용해야 합니다.\n설정에서 허용한 뒤, 돌아와서 메뉴의 '앱 업데이트 확인'을 다시 눌러주세요.")
                .setPositiveButton("설정 열기") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setNegativeButton("취소", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun toast(activity: Activity, text: String) =
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}

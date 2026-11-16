package me.weishu.kernelsu.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.data.registry.ExpectedFile
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import okhttp3.Request
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    expected: ExpectedFile? = null,
    onFailed: (DownloadManager.DownloadState) -> Unit = {},
    mimeType: String? = null,
    completionAction: DownloadCompletionAction = DownloadCompletionAction.INSTALL_MODULE,
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        completionAction = completionAction,
        onCompleted = onDownloaded,
        expected = expected,
    )

    val finalState = DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }[downloadId]
    if (finalState?.status == DownloadManager.Status.FAILED) {
        withContext(Dispatchers.Main) { onFailed(finalState) }
    }
}

/**
 * Downloads a manager update and posts a notification whose Install action hands the APK to
 * the system package installer, so updating never leaves the app.
 *
 * The installer still asks for confirmation: no app can silently replace itself on a
 * non-rooted device, and the APK must be signed with the same key as the installed build.
 */
suspend fun downloadManagerUpdate(
    info: LatestVersionInfo,
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    onFailed: (DownloadManager.DownloadState) -> Unit = {},
) {
    val fileName = info.downloadUrl.substringAfterLast('/')
        .takeIf { it.endsWith(".apk", ignoreCase = true) }
        ?: "noroiSU_${info.versionCode}.apk"
    download(
        url = info.downloadUrl,
        fileName = fileName,
        onDownloading = onDownloading,
        onProgress = onProgress,
        onFailed = onFailed,
        mimeType = APK_MIME_TYPE,
        completionAction = DownloadCompletionAction.INSTALL_APK,
    )
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    val url = "https://api.github.com/repos/re-noroi/KernelSU/releases/latest"
    // default null value if failed
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body.string()
                val json = org.json.JSONObject(body)
                val changelog = json.optString("body")
                val tagName = json.optString("tag_name")

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (!name.endsWith(".apk")) {
                        continue
                    }

                    val regex = Regex("noroiSU_(.+?)_(\\d+)-")
                    val matchResult = regex.find(name) ?: continue
                    val versionCode = matchResult.groupValues[2].toInt()
                    val downloadUrl = asset.getString("browser_download_url")

                    return LatestVersionInfo(
                        versionCode,
                        downloadUrl,
                        changelog,
                        tagName,
                        parseReleaseTagTime(tagName)
                    )
                }

            }
    }
    return defaultValue
}

// CI tags releases with the manager's BUILD_TIME in Asia/Tokyo, e.g. 2026.10.30-081500
private fun parseReleaseTagTime(tag: String): Long = runCatching {
    LocalDateTime.parse(tag, DateTimeFormatter.ofPattern("uuuu.MM.dd-HHmmss"))
        .atZone(ZoneId.of("Asia/Tokyo"))
        .toEpochSecond()
}.getOrDefault(0L)

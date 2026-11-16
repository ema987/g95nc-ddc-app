package com.g95nc.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ReleaseInfo(
    val version: String,
    val url: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class DevBuild(val latestRelease: ReleaseInfo) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

@Serializable
private data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String
)

object UpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/ema987/g95nc-ddc-app/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    suspend fun checkForUpdate(
        currentVersion: String = AppInfo.VERSION,
        fetchLatest: suspend () -> ReleaseInfo = ::fetchLatestRelease
    ): UpdateCheckResult {
        val latest = try {
            fetchLatest()
        } catch (e: Exception) {
            return UpdateCheckResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Could not check for updates")
        }

        if (currentVersion.equals("dev", ignoreCase = true)) {
            return UpdateCheckResult.DevBuild(latest)
        }

        return if (isNewerVersion(latest.version, currentVersion)) {
            UpdateCheckResult.UpdateAvailable(latest)
        } else {
            UpdateCheckResult.UpToDate(currentVersion)
        }
    }

    suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(LATEST_RELEASE_API))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", AppInfo.NAME)
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("GitHub API returned HTTP ${response.statusCode()}")
        }
        parseReleaseJson(response.body())
            ?: throw RuntimeException("Unexpected release response from GitHub")
    }

    internal fun parseReleaseJson(body: String): ReleaseInfo? = try {
        val parsed = json.decodeFromString<GitHubReleaseResponse>(body)
        val version = parsed.tagName.trim().removePrefix("v").removePrefix("V")
        if (version.isBlank() || parsed.htmlUrl.isBlank()) null
        else ReleaseInfo(version = version, url = parsed.htmlUrl)
    } catch (_: Exception) {
        null
    }

    /**
     * Compares dotted numeric versions (e.g. `1.2.0` vs `1.10.0`). Non-numeric builds like `"dev"`
     * return `false` so local builds never trigger false update prompts.
     */
    internal fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = parseNumericParts(latest) ?: return false
        val currentParts = parseNumericParts(current) ?: return false
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun parseNumericParts(version: String): List<Int>? {
        val clean = version.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        if (clean.isBlank()) return null
        val parts = clean.split(".")
        return parts.map { it.toIntOrNull() ?: return null }
    }

    fun openReleasePage(url: String): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}

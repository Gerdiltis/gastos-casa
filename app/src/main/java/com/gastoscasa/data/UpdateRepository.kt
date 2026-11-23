package com.gastoscasa.data

import com.gastoscasa.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdate(val version: String, val notes: String, val apkUrl: String)

@Singleton
class UpdateRepository @Inject constructor(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = BuildConfig.GITHUB_REPO.contains("/")

    /** Devuelve la actualización si la última release de GitHub es más nueva que la app instalada. */
    suspend fun checkForUpdate(): AppUpdate? {
        if (!isConfigured) return null
        val text = http.get("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }.bodyAsText()
        val release = json.decodeFromString(GithubRelease.serializer(), text)
        val remote = release.tag_name.removePrefix("v")
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return if (isNewer(remote, BuildConfig.VERSION_NAME)) {
            AppUpdate(remote, release.body.orEmpty(), apk.browser_download_url)
        } else null
    }

    suspend fun download(update: AppUpdate, target: File): File {
        val bytes = http.get(update.apkUrl).readRawBytes()
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    companion object {
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val l = local.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}

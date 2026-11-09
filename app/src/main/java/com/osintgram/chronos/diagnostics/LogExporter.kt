package com.osintgram.chronos.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports collected logs to .txt, .json, .md or .zip and shares them through
 * the system share sheet.
 *
 * Every format carries the same header: app version, session id, device and
 * Android version, battery, memory, network and storage at export time. Without
 * that, a log is a wall of lines nobody can place on a device.
 */
object LogExporter {

    enum class Format(val ext: String, val mime: String) {
        TXT("txt", "text/plain"),
        MD("md", "text/markdown"),
        JSON("json", "application/json"),
        ZIP("zip", "application/zip"),
    }

    fun export(context: Context, format: Format, appName: String, version: String): File {
        val raw = DiagnosticsLogger.readAll()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "logs-$stamp.${format.ext}")
        val meta = meta(context, appName, version, stamp, raw)
        when (format) {
            Format.TXT -> out.writeText(headerLines(meta) + "\n" + raw)
            Format.MD -> out.writeText(asMarkdown(raw, meta))
            Format.JSON -> out.writeText(asJson(raw, meta))
            Format.ZIP -> asZip(out, raw, meta)
        }
        DiagnosticsLogger.log(
            "Diagnostics",
            "Exported ${format.ext}: ${out.length()} bytes, ${raw.lineSequence().count { it.isNotBlank() }} lines",
        )
        return out
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share diagnostics"))
    }

    // ---- meta --------------------------------------------------------------

    private fun meta(
        context: Context,
        appName: String,
        version: String,
        stamp: String,
        raw: String,
    ): JSONObject {
        val device = DeviceMetrics.snapshot(context, version)
        return JSONObject()
            .put("app", appName)
            .put("version", version)
            .put("exportedAt", stamp)
            .put("session", DiagnosticsLogger.sessionId)
            .put("lines", raw.lineSequence().count { it.isNotBlank() })
            .put("bytes", DiagnosticsLogger.sizeBytes())
            .put("android", device.androidVersion)
            .put("sdk", device.sdk)
            .put("model", device.model)
            .put("abis", device.abis)
            .put("batteryPercent", device.batteryPercent)
            .put("charging", device.charging)
            .put("memoryAvailableMb", device.memoryAvailableMb)
            .put("memoryTotalMb", device.memoryTotalMb)
            .put("lowMemory", device.lowMemory)
            .put("network", device.network)
            .put("storageFreeMb", device.storageFreeMb)
            .put("storageTotalMb", device.storageTotalMb)
    }

    private fun headerLines(meta: JSONObject): String = buildString {
        appendLine("Chronos diagnostics")
        appendLine("app=${meta.optString("app")} version=${meta.optString("version")}")
        appendLine("exported=${meta.optString("exportedAt")} session=${meta.optString("session")}")
        appendLine(
            "android=${meta.optString("android")} sdk=${meta.optInt("sdk")} " +
                "model=${meta.optString("model")} abi=${meta.optString("abis")}",
        )
        appendLine(
            "battery=${meta.optInt("batteryPercent")}% charging=${meta.optBoolean("charging")} " +
                "net=${meta.optString("network")}",
        )
        appendLine(
            "mem=${meta.optLong("memoryAvailableMb")}/${meta.optLong("memoryTotalMb")}MB " +
                "storage=${meta.optLong("storageFreeMb")}/${meta.optLong("storageTotalMb")}MB free",
        )
        appendLine("lines=${meta.optInt("lines")} bytes=${meta.optLong("bytes")}")
    }

    private fun asMarkdown(raw: String, meta: JSONObject): String = buildString {
        appendLine("# Diagnostics export")
        appendLine()
        appendLine("| field | value |")
        appendLine("| --- | --- |")
        meta.keys().forEach { key ->
            appendLine("| $key | ${meta.opt(key).toString().replace("|", "/")} |")
        }
        appendLine()
        appendLine("## Logs")
        appendLine()
        appendLine("```")
        append(raw)
        appendLine()
        appendLine("```")
    }

    private fun asJson(raw: String, meta: JSONObject): String {
        val entries = JSONArray()
        raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            entries.put(
                JSONObject()
                    .put("timestamp", line.substringBefore(" ", missingDelimiterValue = ""))
                    .put("line", line),
            )
        }
        return meta.put("entries", entries).toString(2)
    }

    private fun asZip(out: File, raw: String, meta: JSONObject) {
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("app.log"))
            zip.write((headerLines(meta) + "\n" + raw).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("meta.json"))
            zip.write(meta.toString(2).toByteArray())
            zip.closeEntry()
        }
    }
}

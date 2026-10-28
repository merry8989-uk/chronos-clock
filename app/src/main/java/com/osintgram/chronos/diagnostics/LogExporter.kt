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
 * the system share sheet — all from inside the app (Rule 7).
 */
object LogExporter {

    enum class Format(val ext: String, val mime: String) {
        TXT("txt", "text/plain"),
        JSON("json", "application/json"),
        MD("md", "text/markdown"),
        ZIP("zip", "application/zip"),
    }

    /** Writes the export into the app cache and returns the file. */
    fun export(context: Context, format: Format, appName: String, version: String): File {
        val raw = DiagnosticsLogger.readAll()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "logs-$stamp.${format.ext}")
        when (format) {
            Format.TXT -> out.writeText(raw)
            Format.MD -> out.writeText(asMarkdown(raw, appName, version, stamp))
            Format.JSON -> out.writeText(asJson(raw, appName, version, stamp))
            Format.ZIP -> asZip(out, raw, appName, version, stamp)
        }
        return out
    }

    /** Opens the system share sheet for the exported file. */
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

    private fun asMarkdown(raw: String, appName: String, version: String, stamp: String) =
        buildString {
            appendLine("# Diagnostics export")
            appendLine()
            appendLine("- App: $appName")
            appendLine("- Version: $version")
            appendLine("- Exported: $stamp")
            appendLine()
            appendLine("## Logs")
            appendLine()
            appendLine("```")
            append(raw)
            appendLine()
            appendLine("```")
        }

    private fun asJson(raw: String, appName: String, version: String, stamp: String): String {
        val entries = JSONArray()
        raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            entries.put(
                JSONObject()
                    .put("timestamp", line.substringBefore(" [", missingDelimiterValue = ""))
                    .put("line", line),
            )
        }
        return JSONObject()
            .put("app", appName)
            .put("version", version)
            .put("exportedAt", stamp)
            .put("entries", entries)
            .toString(2)
    }

    private fun asZip(out: File, raw: String, appName: String, version: String, stamp: String) {
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("app.log"))
            zip.write(raw.toByteArray())
            zip.closeEntry()

            val meta = JSONObject()
                .put("app", appName)
                .put("version", version)
                .put("exportedAt", stamp)
                .toString(2)
            zip.putNextEntry(ZipEntry("meta.json"))
            zip.write(meta.toByteArray())
            zip.closeEntry()
        }
    }
}

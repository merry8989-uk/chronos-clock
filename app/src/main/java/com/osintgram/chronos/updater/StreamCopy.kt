package com.osintgram.chronos.updater

import java.io.InputStream
import java.io.OutputStream

/**
 * Copies a download to disk while reporting progress, kept free of Android
 * types so the throttling can be unit-tested.
 *
 * Progress is emitted at most about a hundred times: once per 1% of the total,
 * or per 256 KB when the total is unknown. A 14 MB APK therefore produces a
 * smooth bar without a state write per 16 KB chunk.
 */
object StreamCopy {

    private const val BUFFER = 16 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        total: Long,
        onProgress: (written: Long, total: Long) -> Unit,
    ): Long {
        val step = if (total > 0L) maxOf(total / 100, 256L * 1024) else 512L * 1024
        var written = 0L
        var lastReported = 0L
        onProgress(0L, total)
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            written += read
            if (written - lastReported >= step) {
                lastReported = written
                onProgress(written, total)
            }
        }
        output.flush()
        onProgress(written, total)
        return written
    }
}

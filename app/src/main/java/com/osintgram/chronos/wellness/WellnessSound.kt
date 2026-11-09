package com.osintgram.chronos.wellness

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.osintgram.chronos.data.Settings
import com.osintgram.chronos.data.Tones
import com.osintgram.chronos.diagnostics.DiagnosticsLogger

/**
 * The bell that closes a meditation session. It reuses the bundled tone files
 * that already ship with the alarm feature, so no extra audio is added to the
 * app and the user's chosen tone is respected.
 */
object WellnessSound {

    private var player: MediaPlayer? = null

    fun playBell(context: Context) {
        if (!Settings.wellnessBell.value) return
        val tone = Tones.byId(Settings.wellnessBellTone.value) ?: Tones.byId("classic_bell") ?: return
        release()
        runCatching {
            val uri = Uri.parse(Tones.uriFor(context, tone))
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        }.onFailure {
            DiagnosticsLogger.log("WellnessSound", "Bell could not play: ${it.message}")
            release()
        }
    }

    fun release() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }
}

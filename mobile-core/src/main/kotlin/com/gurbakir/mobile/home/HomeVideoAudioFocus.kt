@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.gurbakir.mobile.home

import android.content.Context
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioFocusRequestCompat
import androidx.media3.common.audio.AudioManagerCompat

/** Home pauses on every focus loss, including duckable loss, and resumes only from an explicit Play. */
internal class HomeVideoAudioFocus(
    context: Context,
    private val player: Player,
    private val session: HomePlaybackSession
) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var held = false
    private val focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.DEFAULT)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            ) {
                player.pause()
                session.pause(HomePlaybackPauseReason.AUDIO_FOCUS_LOST)
                abandon()
            }
        }
        .build()

    fun request(): Boolean {
        if (!held) {
            held =
                AudioManagerCompat.requestAudioFocus(manager, focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return held
    }

    fun abandon() {
        AudioManagerCompat.abandonAudioFocusRequest(manager, focusRequest)
        held = false
    }
}

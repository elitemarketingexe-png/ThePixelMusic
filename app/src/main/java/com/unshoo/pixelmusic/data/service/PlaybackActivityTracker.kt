package com.unshoo.pixelmusic.data.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide flag describing whether a music playback session is currently active.
 *
 * Owned by [MusicService]: set to true when `isPlaying` is true, cleared on
 * stop/destroy/pause. Read by background workers to defer non-urgent work
 * while the user is listening, prioritizing thermal stability and battery.
 */
object PlaybackActivityTracker {
    private val active = AtomicBoolean(false)

    /** True while a [MusicService] playback session is producing audio. */
    val isPlaybackActive: Boolean
        get() = active.get()

    /** Called by MusicService when playback transitions to/from playing. */
    fun setPlaybackActive(active: Boolean) {
        this.active.set(active)
    }
}

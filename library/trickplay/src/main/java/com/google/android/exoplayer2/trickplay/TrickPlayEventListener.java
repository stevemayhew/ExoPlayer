package com.google.android.exoplayer2.trickplay;

import com.google.android.exoplayer2.SimpleExoPlayer;

/**
 * Event callbacks for various interesting state changes to trick play.
 */
public interface TrickPlayEventListener {

    /**
     * Called when the playback state changes or set of tracks available changes.  Indicates if
     * enough metadata (tracks from the playlist) have been read to determine if the current
     * {@link com.google.android.exoplayer2.source.MediaSource} will support high speed playback
     * or not.
     *
     * Once this is signaled with true for isMetadataValid, The updated playback speeds supported
     * can be determined via
     * {@link TrickPlayControl#getSpeedFor(TrickPlayControl.TrickMode)} and {}
     *
     * @param isMetadataValid - true if the playlist was loaded and presence of iframe track is known
     */
    default void playlistMetadataValid(boolean isMetadataValid) {}

    /**
     * Triggered by the call to change the speed {@link TrickPlayControl#setTrickMode(TrickPlayControl.TrickMode)}.
     * or an exit from trick play because a seekable boundry was reached.
     *
     * Dispatched on the listeners Looper, so it will not re-entrantly call the caller of setTrickMode()
     *
     * @param newMode - the trickplay mode currently being played
     * @param prevMode - the previous mode before the change
     */
    default void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {}

    /**
     * Dispatched when a frame is rendered (only frames rendered in trickplay mode other then
     * {@link com.google.android.exoplayer2.trickplay.TrickPlayControl.TrickMode#NORMAL}).
     *
     * This callback is only used internally in the trick play handler, listening to this on the
     * Application Thread {@link SimpleExoPlayer#getApplicationLooper()} is likely to adversely
     * impact performance.
     *
     * @param frameRenderTimeUs the presentation time of the rendered frame
     */
    default void trickFrameRendered(long frameRenderTimeUs) {}
}


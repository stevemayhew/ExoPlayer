package com.tivo.exoplayer.library;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.util.Log;
import java.io.IOException;
import java.util.List;

/**
 * ExoPlayer reports errors via the {@link Player.EventListener#onPlayerError(ExoPlaybackException)}
 * method.  The errors reported to this method may be recovered, the player is transitions to the
 * {@link Player#STATE_IDLE} and playback stops.
 *
 * This handler listens to the {@link AnalyticsListener}  interface
 * which includes events from the {@link Player.EventListener} as well as the
 * {@link com.google.android.exoplayer2.source.MediaSourceEventListener} events which indicate
 * load errors that may eventually become ExoPlaybackExceptions.
 *
 */
public class DefaultExoPlayerErrorHandler implements AnalyticsListener {

  private static final String TAG = "ExoPlayerErrorHandler";
  private final List<PlaybackExceptionRecovery> handlers;

  public interface PlaybackExceptionRecovery {
    boolean recoverFrom(ExoPlaybackException e);
  }

  public DefaultExoPlayerErrorHandler(List<PlaybackExceptionRecovery> handlers) {
    this.handlers = handlers;
  }

  @Override
  public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
    Log.d(TAG, "onPlayerError: eventTime: " + eventTime + ", error: " + error);

    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.recoverFrom(error)) {
        Log.d(TAG, "onPlayerError recovery returned success");
        break;
      }
    }

    recoveryFailed(eventTime, error);
  }

  protected void recoveryFailed(EventTime eventTime, ExoPlaybackException error) {
    Log.d(TAG, "");
  }

  @Override
  public void onLoadError(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo,
      MediaSourceEventListener.MediaLoadData mediaLoadData, IOException error,
      boolean wasCanceled) {
    Log.d(TAG, "onLoadError - URL: " + loadEventInfo.uri + " io error: "+error);
  }
}

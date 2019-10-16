package com.tivo.exoplayer.library;

import androidx.annotation.CallSuper;
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

  /**
   * If you add a handler to the list it must implement this interface.
   */
  public interface PlaybackExceptionRecovery {

    /**
     * Handlers are called with the exception, returning true stops the rest of
     * the handlers on the list from being called
     *
     * @param e the {@link ExoPlaybackException} signaled
     * @return true to stop the recovery chain (assumes your handler recovered from it)
     */
    boolean recoverFrom(ExoPlaybackException e);
  }

  /**
   * If you subclass implement this method. You can choose to add to the list of
   * {@link PlaybackExceptionRecovery} handlers, it is recommmeded you add to the end of the
   * list.  Most all common errors that ExoPlayer indicates can be recovered are handled
   * (eg. {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
   *
   * @param handlers ordred list of {@link PlaybackExceptionRecovery} handlers
   */
  public DefaultExoPlayerErrorHandler(List<PlaybackExceptionRecovery> handlers) {
    this.handlers = handlers;
  }

  @Override
  @CallSuper
  public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {
    Log.w(TAG, "onPlayerError: eventTime: " + eventTime + ", error: " + error);
    boolean recovered = false;

    for (PlaybackExceptionRecovery handler : handlers) {
      if (handler.recoverFrom(error)) {
        Log.d(TAG, "onPlayerError recovery returned success");
        recovered = true;
        break;
      }
    }

    playerErrorProcessed(eventTime, error, recovered);
  }

  /**
   * This is the hook for subclasses to be notified of the playback error
   * from {@link #onPlayerError(EventTime, ExoPlaybackException)} and the status of attempts to
   * recover from the error.
   *
   * @param eventTime time and details for the error event.
   * @param error the acutal reported {@link ExoPlaybackException}
   * @param recovered true if recovery handler handled the error
   */
  protected void playerErrorProcessed(EventTime eventTime, ExoPlaybackException error, boolean recovered) {
    Log.d(TAG, "playerError was processed, " + (recovered ? "recovery succeed" : "recovery failed."));
  }

  @Override
  public void onLoadError(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo,
      MediaSourceEventListener.MediaLoadData mediaLoadData, IOException error,
      boolean wasCanceled) {
    Log.d(TAG, "onLoadError - URL: " + loadEventInfo.uri + " io error: "+error);
  }
}

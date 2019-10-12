package com.tivo.exoplayer.library;

import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Log;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Monitors playback session and powers the stats for geeks overlay for player
 * debug.
 *
 * To make this overlay visible send the intent {@link #DISPLAY_INTENT} to the demo
 * or other android application hosting the player that supports geeks display.
 *
 * To integrate this UI in your ExoPlayer application add a view with this
 */
public class GeekStatsOverlay implements AnalyticsListener, Runnable {

  public static final String DISPLAY_INTENT = "com.tivo.exoplayer.SHOW_GEEKSTATS";
  private static final String TAG = "ExoGeekStat";
  private static final SimpleDateFormat UTC_DATETIME
      = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.S Z", Locale.getDefault());
  private static final SimpleDateFormat UTC_TIME = new SimpleDateFormat("HH:mm:ss.S Z", Locale.getDefault());

  private final View containingView;

  @Nullable
  private SimpleExoPlayer player;

  @Nullable
  private TrickPlayControl trickPlayControl;

  private TextView stateView;
  private TextView currentTimeView;
  private TextView currentLevel;
  private TextView loadingLevel;
  private final TextView playbackRate;

  private final int updateInterval;

  private Format lastLoadedVideoFormat;
  private Format lastDownstreamVideoFormat;
  private long lastPositionReport;

  // Statistic counters, reset on url change
  private long lastTimeUpdate;
  private int levelSwitchCount = 0;
  private int lastPlayState = Player.STATE_IDLE;

  public GeekStatsOverlay(View view, int updateInterval) {
    containingView = view;
    currentLevel = view.findViewById(R.id.current_level);
    loadingLevel = view.findViewById(R.id.loading_level);
    stateView = view.findViewById(R.id.current_state);
    currentTimeView = view.findViewById(R.id.current_time);
    playbackRate = view.findViewById(R.id.playback_rate);
    this.updateInterval = updateInterval;
  }

  public GeekStatsOverlay(View view) {
    this(view, 1000);
  }

  /**
   * Call this to update the player and it's associated TrickPlayControl (if any)
   *
   * @param player current player, or null to remove old player
   * @param trickPlayControl trickplay control for the player (if any)
   */
  public void setPlayer(@Nullable SimpleExoPlayer player, TrickPlayControl trickPlayControl) {
    this.trickPlayControl = trickPlayControl;

    if (player == null && this.player != null) {
      stop();
      this.player = null;
    } else {
      this.player = player;
      start();
    }
  }

  public void toggleVisible() {
    int visibility = containingView.getVisibility();

    if (visibility == View.VISIBLE) {
      containingView.setVisibility(View.INVISIBLE);
    } else {
      containingView.setVisibility(View.VISIBLE);
    }
  }

  private void start() {
    player.addAnalyticsListener(this);
    timedTextUpdate();
  }

  private void stop() {
    player.removeAnalyticsListener(this);
    containingView.removeCallbacks(this);
    resetStats();
  }

  private void timedTextUpdate() {
    currentTimeView.setText(getPositionString());
    currentLevel.setText(getPlayingLevel());
    playbackRate.setText(getPlaybackRateString());
    containingView.removeCallbacks(this);
    containingView.postDelayed(this, updateInterval);
  }

  private void resetStats() {
    levelSwitchCount = 0;
    lastTimeUpdate = C.TIME_UNSET;
  }

  private String getPlayerState() {
    String state = "no-player";
    if (player != null) {
      switch (player.getPlaybackState()) {
        case Player.STATE_BUFFERING:
          state = "buffering";
          break;
        case Player.STATE_IDLE:
          state = "idle";
          break;
        case Player.STATE_ENDED:
          state = "ended";
          break;
        case Player.STATE_READY:
          state = "ready";
          break;
      }
    }
    return state;
  }

  protected String getPlayingLevel() {
    String level = "Playing: ";

    if (player != null) {
      Format format = lastDownstreamVideoFormat
          == null ? player.getVideoFormat() : lastDownstreamVideoFormat;
      DecoderCounters decoderCounters = player.getAudioDecoderCounters();

      level += getFormatString(format) + " - changes: " + levelSwitchCount;

      if (decoderCounters != null) {
        level += " dropped: " + decoderCounters.droppedBufferCount;
      }
    }

    return level;
  }

  private String getFormatString(Format format) {
    return format == null ? "<unknown>" :
      "(id:" + format.id +") - " + format.width + "x" + format.height + " @ " + format.bitrate;
  }

  protected String getPositionString() {
    String time = "";
    long position = 0L;

    if (player != null) {
      position = player.getCurrentPosition();

      Timeline timeline = player.getCurrentTimeline();
      if (! timeline.isEmpty()) {
        int windowIndex = player.getCurrentWindowIndex();
        Timeline.Window currentWindow = timeline.getWindow(windowIndex, new Timeline.Window());
        long absTime;
        DateFormat format;
        if (currentWindow.windowStartTimeMs == C.TIME_UNSET) {
          format = UTC_TIME;
          absTime = position;
        } else {
          format = UTC_DATETIME;
          absTime = currentWindow.windowStartTimeMs + position;
        }
        Date currentMediaTime = new Date(absTime);
        time = format.format(currentMediaTime);
      }

    }

    return time + " (" + position + ")";
  }


  protected String getPlaybackRateString() {
    float playbackRate = 0.0f;
    String rateString = "";

    if (lastTimeUpdate == C.TIME_UNSET) {
        lastTimeUpdate = System.currentTimeMillis();
    } else if (player != null && trickPlayControl != null) {
        long currentPosition = player.getCurrentPosition();
        long positionChange = Math.abs(lastPositionReport - currentPosition);
        long timeChange = System.currentTimeMillis() - lastTimeUpdate;
        lastTimeUpdate = System.currentTimeMillis();
        lastPositionReport = currentPosition;
        playbackRate = (float)positionChange / (float)timeChange;
        float trickSpeed = trickPlayControl.getSpeedFor(trickPlayControl.getCurrentTrickMode());
        rateString = String.format("%.3f", trickSpeed) + "(" + String.format("%.3f", playbackRate) + ")";
    }
    return rateString;
  }

  /**
   * Called this method before calling {@link SimpleExoPlayer#prepare(MediaSource)}
   *
   * This restarts a new data collection session.
   *
   * @param restart - if prepare call is to restart for a playback error set this flag true
   */
  public void startingPrepare(boolean restart) {
    if (! restart) {
      resetStats();
    }
  }

  private boolean isVideoTrack(MediaSourceEventListener.MediaLoadData loadData) {
    return loadData.trackFormat != null && (loadData.trackType == C.TRACK_TYPE_VIDEO || loadData.trackFormat.width > 0);
  }

  // Timer callback

  @Override
  public void run() {
    timedTextUpdate();
  }

  // Implement AnalyticsListener

  @Override
  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
    if (player != null) {
      int currentState = player.getPlaybackState();

      if (lastPlayState != Player.STATE_IDLE && currentState == Player.STATE_IDLE) {
        resetStats();
      }
      lastPlayState = currentState;
      stateView.setText("State: " + getPlayerState());
      timedTextUpdate();
    }
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {
    if (isVideoTrack(mediaLoadData)) {
      lastDownstreamVideoFormat = mediaLoadData.trackFormat;
    }
  }

  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {
    Timeline.Window window = new Timeline.Window();
    if (eventTime.timeline.isEmpty()) {
      Log.d(TAG, "onTimeLineChanged - empty timeline");
    } else {
      eventTime.timeline.getWindow(0, window);
      long windowStartTimeMs = window.windowStartTimeMs;
      Log.d(TAG, "onTimeLineChanged - eventPlaybackPositionMs: " + eventTime.eventPlaybackPositionMs
          + " widowStartTime: " + UTC_DATETIME.format(windowStartTimeMs) + "(" + + windowStartTimeMs + ")");

    }
  }

  @Override
  public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {

  }

  @Override
  public void onLoadCompleted(EventTime eventTime, MediaSourceEventListener.LoadEventInfo loadEventInfo, MediaSourceEventListener.MediaLoadData mediaLoadData) {
    Format format = mediaLoadData.trackFormat;
    if (isVideoTrack(mediaLoadData)) {
      levelSwitchCount += format.equals(lastLoadedVideoFormat) ? 0 : 1;
      lastLoadedVideoFormat = format;

      long kbps = (loadEventInfo.bytesLoaded / loadEventInfo.loadDurationMs) * 8;
      loadingLevel.setText("Loading: " + getFormatString(format) + " - " + kbps + "kbps");
    }
  }
}

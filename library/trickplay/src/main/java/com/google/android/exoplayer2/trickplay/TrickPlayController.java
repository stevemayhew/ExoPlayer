package com.google.android.exoplayer2.trickplay;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaClock;

/**
 * Implements simple trick-play for video fast forward / reverse without sound.
 *
 * Fast modes are three different rates in each direction including normal playback (which re-enables sound)
 */
class TrickPlayController implements TrickPlayControlInternal {

    private static final String TAG = "TRICK-PLAY";

    private @MonotonicNonNull SimpleExoPlayer player;
    private final DefaultTrackSelector trackSelector;

    private final CopyOnWriteArraySet<ListenerRef> listeners;

    private TrickMode currentTrickMode = TrickMode.NORMAL;
    private AnalyticsListener playerEventListener;
    private int lastSelectedAudioTrack = -1;

    private TrickPlayMessageHandler currentHandler = null;
    private ReversibleMediaClock currentMediaClock = null;

    private Map<TrickMode, Float> speedsForMode;

    private boolean isSmoothPlayAvailable;
    private boolean isMetadataValid = false;

    /** To dispatch events on the Players main handler thread
     */
    private Handler playbackHandler;

    /** To dispatch events on the appliation thread that created the ExoPlayer and TrickPlayControl
     */
    private Handler applicatonHandler;

    /** Keep track of the last N rendered frame PTS values for jumpback seek
     */
    private LastNPositions lastRenderPositions = new LastNPositions();

    /**
     * Trick-play direction, fast play forward or reverse.  Or NONE for
     * {@see TrickMode#NORMAL}.
     */
    enum TrickPlayDirection {
        FORWARD, NONE, REVERSE
    }

    TrickPlayController(DefaultTrackSelector trackSelector) {
        this.trackSelector = trackSelector;
        this.listeners = new CopyOnWriteArraySet<>();

        // Default (for now) assume iFrames are available.
        setSpeedsForMode(true);

        addEventListener(new TrickPlayEventListener() {
            @Override
            public void playlistMetadataValid(boolean isMetadataValid) {
                if (isMetadataValid) {
                    setSpeedsForMode(isSmoothPlayAvailable);
                }
            }
        });
    }

    /**
     * Keeps trick play event listener and context to call it with
     */
    private static final class ListenerRef {
        enum CallType {PLAYER, APPLICATION};

        final CallType callType;
        final TrickPlayEventListener listener;

        ListenerRef(CallType callType, TrickPlayEventListener listener) {
            this.callType = callType;
            this.listener = listener;
        }
    }

    /**
     * Keeps track of render position based on the existing ExoPlayer {@link MediaClock}
     * this interface is used by the renderers, so using it will facilite
     * using either seek based of IDR sample flow based hig speed playback.
     *
     */
    private class ReversibleMediaClock implements MediaClock {

        private boolean isForward;
        private Clock clock;
        private long baseElapsedMs;
        private PlaybackParameters playbackParameters;
        private long baseUs;

        /**
         * Construct the clock source with the current position to start and the
         * intended playback direction (as PlaybackParameters does not support speed < 0)
         *
         * @param isForward - true if playback is forward else false.
         * @param positionUs - current position at start of clock
         * @param clock - source of time
         */
        ReversibleMediaClock(boolean isForward, long positionUs, Clock clock) {
            this.isForward = isForward;
            this.clock = clock;
            baseElapsedMs = clock.elapsedRealtime();
            this.baseUs = positionUs;
        }

        @Override
        public long getPositionUs() {
            long positionUs = baseUs;
            long elapsedSinceBaseMs = clock.elapsedRealtime() - baseElapsedMs;
            long elapsedSinceBaseUs = C.msToUs(elapsedSinceBaseMs);
            if (playbackParameters.speed == 1f) {
                positionUs += isForward ? elapsedSinceBaseUs : -elapsedSinceBaseUs;
            } else if (isForward) {
                positionUs += playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
            } else {
                positionUs -= playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
            }
            return positionUs;
        }

        @Override
        public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {

            this.playbackParameters = playbackParameters;
            return playbackParameters;
        }

        @Override
        public PlaybackParameters getPlaybackParameters() {
            return playbackParameters;
        }
    }

    /**
     * Keeps record of the program time of the last N rendered frames in trickplay mode.
     * Returns this list, converted to playback positions
     */
    private class LastNPositions {
        public static final int LAST_RENDER_POSITIONS_MAX = 15;

        private final long[] store;
        private int lastWriteIndex = -1;

        LastNPositions(int size) {
            this.store = new long[size];
            Arrays.fill(this.store, C.TIME_UNSET);
        }

        public LastNPositions() {
            this(LAST_RENDER_POSITIONS_MAX);
        }

        /**
         * Add a rendered time stamp, this is called from the player's thread
         *
         * @param value - render program time of the frame
         */
        synchronized void add(long value) {
            lastWriteIndex = (lastWriteIndex + 1) % store.length;
            store[lastWriteIndex] = value;
        }

        synchronized private List<Long> getLastNRenderPositionsUs() {
            ArrayList<Long> values = new ArrayList<>();
            if (lastWriteIndex >= 0) {
                int readIndex = lastWriteIndex;
                while (values.size() < store.length && store[readIndex] != C.TIME_UNSET) {
                    values.add(store[readIndex]);
                    readIndex--;
                    readIndex = readIndex < 0 ? store.length - 1 : readIndex;
                }
            }
            return values;
        }

        synchronized int getSavedRenderPositionsCount() {
            int count = 0;
            for (long value : store) {
                count += (value != C.TIME_UNSET) ? 1 : 0;
            }
            return count;
        }

        /**
         * Creates a list, in reverse rendered order of the last frame renders converted from
         * program time to time offsets within the current window, in ms.  The values are suitable
         * for calling {@see SimpleExoPlayer#seekTo}.
         *
         * @return list of time player time values, in ms, normalized to the period time
         */
         List<Long> lastNPositions() {
            Timeline timeline = player.getCurrentTimeline();
            Timeline.Window window = timeline.getWindow(player.getCurrentWindowIndex(), new Timeline.Window());

            List<Long> values = Collections.emptyList();

            if (timeline.isEmpty()) {
                Log.w(TAG, "trickplay stopped with empty timeline, should not be possible");
            } else {
                List<Long> lastNRenders = getLastNRenderPositionsUs();

                ListIterator<Long> iter = lastNRenders.listIterator();
                while (iter.hasNext()) {
                    int index = iter.nextIndex();
                    Long renderTimeUs = iter.next();
                    lastNRenders.set(index, C.usToMs(renderTimeUs - window.positionInFirstPeriodUs));
                }

                values = lastNRenders;
            }

            return values;
        }

    }

    private class PlayerEventListener implements AnalyticsListener {
        private int lastPlaybackState = 0;

        @Override
        public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {

            // reset isMetadata valid if transition to an "ended" like state. STATE_READY and
            // STATE_BUFFERING wait until the initial set of tracks are set before indicating valid
            // metadata.
            //
            switch(playbackState) {
                case Player.STATE_IDLE:
                case Player.STATE_ENDED:
                    isMetadataValid = false;
                    dispatchPlaylistMetadataChanged();
                    break;
            }

            TrickMode currentTrickMode = getCurrentTrickMode();
            if (! eventTime.timeline.isEmpty() && currentTrickMode != TrickMode.NORMAL) {
                Timeline.Window currentWindow = new Timeline.Window();
                eventTime.timeline.getWindow(eventTime.windowIndex, currentWindow);
                if (currentWindow.durationUs == C.TIME_UNSET) {
                    Log.d(TAG, "state change to " + playbackState + " but durationUs not known");
                } else {
                    if (! isTrickModePossible(currentWindow, eventTime.currentPlaybackPositionMs, currentTrickMode)) {
                        setTrickMode(TrickMode.NORMAL);
                    }
                }
            }

            lastPlaybackState = playbackState;
        }

        @Override
        public void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            isMetadataValid = true;

            // Each related set of playlists in the master (for HLS at least) creates a TrackGroup in
            // the TrackGroupArray.  The Format's within that group are the adaptations of the playlist.
            //
            // Look for the video trackgroup, if there is one see if it has any trickplay format.
            //
            for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                TrackGroup group = trackGroups.get(groupIndex);
                boolean groupIsVideo = group.length > 0 && isVideoFormat(group.getFormat(0));

                if (groupIsVideo) {
                    isSmoothPlayAvailable = false;
                    for (int trackIndex = 0; trackIndex < group.length && ! isSmoothPlayAvailable; trackIndex++) {
                        Format format = group.getFormat(trackIndex);
                        isSmoothPlayAvailable = (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
                    }
                }
            }

            dispatchPlaylistMetadataChanged();

            TrackSelectionArray selections = player.getCurrentTrackSelections();
            for (int i = 0; i < selections.length; i++) {
              if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && selections.get(i) != null && lastSelectedAudioTrack == -1) {
                lastSelectedAudioTrack = i;
              }
            }
        }

        @Override
        public void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
            Log.d(TAG, "First frame " + getCurrentTrickMode() + " position: " + eventTime.currentPlaybackPositionMs);

            if (currentHandler != null) {
                Message msg = currentHandler.obtainMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_FRAMERENDER, eventTime);
                currentHandler.sendMessage(msg);
            }
        }

        @Override
        public void onSeekStarted(EventTime eventTime) {
            if (currentHandler != null) {
                Message msg = currentHandler.obtainMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_SEEKSTARTED, eventTime);
                currentHandler.sendMessage(msg);
            }
        }

        @Override
        public void onSeekProcessed(EventTime eventTime) {
            if (currentHandler != null) {
                Message msg = currentHandler.obtainMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_SEEKPROCESSED, eventTime);
                currentHandler.sendMessage(msg);
            }
        }
    }

    /**
     * Handles messages in the callers thread (thread that created the {@see TrickPlayController} and called
     * {@see #setTrickMode}.
     */
    private class TrickPlayMessageHandler extends Handler {

        public static final int MSG_TRICKPLAY_STARTSEEK = 1;
        public static final int MSG_TRICKPLAY_FRAMERENDER = 2;
        public static final int MSG_TRICKPLAY_SEEKPROCESSED = 3;
        public static final int MSG_TRICKPLAY_SEEKSTARTED = 3;

        public static final int TARGET_FPS = 3;
        public static final int IDR_INTERVAL_TARGET_MS = 1000 / TARGET_FPS;

        private AnalyticsListener.EventTime lastSeekProcessed;

        TrickPlayMessageHandler(long startingPosition) {
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            float currentSpeed = getSpeedFor(getCurrentTrickMode());
            boolean isForward = currentSpeed > 0.0f;
            Log.d(TAG, "TrickPlayMessageHandler() - mode: " + getCurrentTrickMode() + " start at: " + startingPosition);

            PlaybackParameters playbackParameters = new PlaybackParameters(Math.abs(currentSpeed));
            currentMediaClock = new ReversibleMediaClock(isForward, C.msToUs(startingPosition), Clock.DEFAULT);
            currentMediaClock.setPlaybackParameters(playbackParameters);
        }

        @Override
        public void handleMessage(Message msg) {
            TrickMode currentTrickMode = getCurrentTrickMode();

            switch (msg.what) {
                case MSG_TRICKPLAY_STARTSEEK:

                    if (currentTrickMode != TrickMode.NORMAL) {
                        long contentPosition = player.getContentPosition();
                        long seekTarget = C.usToMs(currentMediaClock.getPositionUs());

                        Log.d(TAG, "handleMessage STARTSEEK - mode " + currentTrickMode + " position: " + contentPosition + " request position " + seekTarget);

                        player.seekTo(seekTarget);
                    } else {
                        player.setSeekParameters(SeekParameters.DEFAULT);
                    }
                    break;

                case MSG_TRICKPLAY_FRAMERENDER:
                    AnalyticsListener.EventTime renderTime = (AnalyticsListener.EventTime) msg.obj;
                    // Compute next due time, <0 is considered now by sendMessageDelayed
                    //
                    long nextFrameDue = IDR_INTERVAL_TARGET_MS - (renderTime.realtimeMs - lastSeekProcessed.realtimeMs);
                    Log.d(TAG, "handleMessage FRAMERENDER - mode " + currentTrickMode + " position: " + renderTime.eventPlaybackPositionMs
                            + " renderTime:" + renderTime.realtimeMs + " lastSeekTime: " + lastSeekProcessed.realtimeMs
                            + " nextFrameDue: " + nextFrameDue);

                    currentHandler.sendEmptyMessageDelayed(MSG_TRICKPLAY_STARTSEEK, nextFrameDue);

                    break;

                case MSG_TRICKPLAY_SEEKPROCESSED:
                    AnalyticsListener.EventTime eventTime = (AnalyticsListener.EventTime) msg.obj;
                    Log.d(TAG, "handleMessage SEEKPROCESSED - mode " + currentTrickMode + " position: " + eventTime.eventPlaybackPositionMs);
                    lastSeekProcessed = eventTime;
                    break;


            }
        }
    }

    /////
    // API methods - TrickPlayControl interface
    /////

    @Override
    public Float getSpeedFor(TrickMode mode) {
        return speedsForMode.get(mode);
    }

    @Override
    public RenderersFactory createRenderersFactory(Context context) {
        return new TrickPlayRendererFactory(context, this);
//        return new DefaultRenderersFactory(context);
    }

    @Override
    public LoadControl createLoadControl(LoadControl delegate) {
        return new AdaptiveLoadControl(this, delegate);
    }

    @Override
    public void setPlayer(@NonNull  SimpleExoPlayer player) {
        setCurrentTrickMode(TrickMode.NORMAL);
        if (this.player != null) {
            removePlayerReference();
        }
        this.player = player;
        playbackHandler = new Handler(player.getPlaybackLooper());
        applicatonHandler = new Handler(player.getApplicationLooper());
        playerEventListener = new PlayerEventListener();
        this.player.addAnalyticsListener(playerEventListener);
    }

    @Override
    public synchronized TrickMode getCurrentTrickMode() {
        return currentTrickMode;
    }

    @Override
    public boolean isSmoothPlayAvailable() {
        return isSmoothPlayAvailable;
    }

    @Override
    public boolean isMetadataValid() {
        return isMetadataValid;
    }

    @Override
    public int setTrickMode(TrickMode newMode) {
        TrickMode previousMode = getCurrentTrickMode();
        int lastRendersCount = -1;

        // TODO disallow transition from forward to reverse without first normal

        if (newMode != previousMode) {
            float speed = getSpeedFor(newMode);

            boolean modeChangePossible = true;
            Timeline timeline = player.getCurrentTimeline();
            if (!timeline.isEmpty()) {
                Timeline.Window currentWindow = new Timeline.Window();
                timeline.getWindow(player.getCurrentWindowIndex(), currentWindow);
                if (currentWindow.durationUs != C.TIME_UNSET) {
                    modeChangePossible = isTrickModePossible(currentWindow, player.getCurrentPosition(), newMode);
                }
            }

            if (modeChangePossible) {
                lastRendersCount = 0;

                // Pause playback
                Log.d(TAG, "setTrickMode(" + newMode + ") pausing playback - previous mode: " + previousMode);

                player.setPlayWhenReady(false);

                setCurrentTrickMode(newMode);

                /*
                 * If speed is in range to play all frames (not just iFrames), disable audio and let
                 * the DefaultMediaClock handle.  Restore audio and reset playback parameters for normal,
                 * otherwise use seek based.
                 *
                 */
                if (newMode == TrickMode.NORMAL) {
                    destroyTrickPlayMessageHandler();
                    player.setPlaybackParameters(PlaybackParameters.DEFAULT);

                    long currentPosition = player.getCurrentPosition();

                    Log.d(TAG, "Stop trickplay at media time " + currentPosition + " parameters: " + player.getPlaybackParameters().speed);

                    enableLastSelectedAudioTrack();
                    player.setPlayWhenReady(true);

                    if (modeIsTrickForward(previousMode)) {
                        List<Long> lastRenders = lastRenderPositions.lastNPositions();
                        Log.d(TAG, "Last rendered frame positions " + lastRenders);
                        lastRendersCount = lastRenders.size();

                        if (lastRendersCount > 0) {
                            long positionMs = lastRenders.get(0);
                            Log.d(TAG, "Seek to previous rendered frame, positions " + positionMs);
                            player.seekTo(positionMs);
                        }
                    }
                } else {

                    if (previousMode == TrickMode.NORMAL) {
                        lastRenderPositions = new LastNPositions();
                    }
                    if (canUsePlaybackSpeed(newMode)) {
                        Log.d(TAG, "Start trickplay " + newMode + " at media time " + player.getCurrentPosition());
                        destroyTrickPlayMessageHandler();
                        switchToTrickPlayTracks();
                        player.setPlayWhenReady(true);
                        player.setPlaybackParameters(new PlaybackParameters(speed));
                    } else {
                        Log.d(TAG, "Start trickplay " + newMode + " at media time " + player.getCurrentPosition());

                        switchToTrickPlayTracks();
                        player.setPlayWhenReady(false);
                        startTrickPlayMessageHandler();
                    }
                }

                dispatchTrickModeChanged(newMode, previousMode);
            } else {
                Log.d(TAG, "setTrickMode(" + newMode + ") not possible because of currentPosition");
            }
        }
        return lastRendersCount;
    }

    @Override
    public boolean seekToNthPlayedTrickFrame(int frameNumber) {
        List<Long> renderPositions = lastRenderPositions.lastNPositions();
        boolean success = frameNumber < renderPositions.size();
        if (success) {
            player.seekTo(renderPositions.get(frameNumber));
        }
        return success;
    }

    /////
    // Internal methods
    /////

    private void removePlayerReference() {
        destroyTrickPlayMessageHandler();
        this.listeners.clear();
        this.player.removeAnalyticsListener(playerEventListener);
        this.player = null;
    }


    private void setSpeedsForMode(boolean isIFramesAvailable) {
        // Keep a seperate map defining the mapping from TrickMode enum to speed (to keep it a pure enum)
        //
        EnumMap<TrickMode, Float> initialSpeedsForMode = new EnumMap<TrickMode, Float>(TrickMode.class);
        for (TrickMode trickMode : TrickMode.values()) {
            initialSpeedsForMode.put(trickMode, getDefaultSpeedForMode(trickMode, isIFramesAvailable));
        }

        this.speedsForMode = Collections.synchronizedMap(initialSpeedsForMode);
    }

    /**
     * Default speeds used to initialize the map, depending on if smooth high speed
     * playback is available.
     *
     * @param mode - the {@link com.google.android.exoplayer2.trickplay.TrickPlayControl.TrickMode} to get
     * @param isIFramesAvailable - if speed should be iFrame based.
     * @return
     */
    private static Float getDefaultSpeedForMode(TrickMode mode, boolean isIFramesAvailable) {
        Float speed = 0.0f;

        switch (mode) {
            case FF1:
                speed = 15.0f;
                break;
            case FR1:
                speed = -15.0f;
                break;

            case FF2:
                speed = 30.0f;
                break;
            case FR2:
                speed = -30.0f;
                break;

            case FF3:
                speed = 60.0f;
                break;
            case FR3:
                speed = -60.0f;
                break;

            case NORMAL:
                speed = 1.0f;
                break;
        }

        if (mode != TrickMode.NORMAL && ! isIFramesAvailable) {
            speed = speed / 6.0f;
        }

        return speed;
    }

    private static TrickPlayDirection directionForMode(TrickMode mode) {
        TrickPlayDirection direction = TrickPlayDirection.NONE;

        switch (mode) {
            case FF1:
            case FF2:
            case FF3:
                direction = TrickPlayDirection.FORWARD;
                break;
            case FR1:
            case FR2:
            case FR3:
                direction = TrickPlayDirection.REVERSE;
                break;
        }
        return  direction;
    }

    private static boolean canUsePlaybackSpeed(TrickMode mode) {
        return directionForMode(mode) == TrickPlayDirection.FORWARD;
    }

    private static boolean modeIsTrickForward(TrickMode mode) {
        return directionForMode(mode) == TrickPlayDirection.FORWARD;
    }

    private synchronized void setCurrentTrickMode(TrickMode mode) {
        currentTrickMode = mode;
    }

    /**
     * Checks if trick-play mode requested (requestedMode) is possible to start (or can
     * continue) based on the current playback position in the timeline.
     *
     * @param currentWindow - current playing Timeline.Window to check seek boundries on
     * @param playbackPositionMs - current playback position, checked against the window
     * @param requestedMode - the mode to check if trick-play can start (continue) in
     * @return true if trick-play in the 'requestedMode' is possible.
     */
    private boolean isTrickModePossible(Timeline.Window currentWindow,
        long playbackPositionMs,
        TrickMode requestedMode) {
        boolean isPossible = true;

        // Last seekable is either the duration (VOD) or the start of the valid live edge,
        // callee must make sure the timeline is valid and duration has been determined for the
        // current window (after mediasouce is prepared.
        //
        long lastSeekablePosition =
            currentWindow.isDynamic ? C.usToMs(currentWindow.defaultPositionUs)
                : C.usToMs(currentWindow.durationUs);

        // TODO - trickplay reverse, as well as being behind the live window can leave the
        // TODO - current position negative, the code below works as expected in this case, but note well.
        //
        TrickPlayDirection direction = directionForMode(requestedMode);
        switch (direction) {
            case FORWARD:
                isPossible = (playbackPositionMs - lastSeekablePosition) <= 3000;
                break;

            case REVERSE:
                isPossible = playbackPositionMs >= 1000;
                break;
        }

        return isPossible;
    }

    private void switchToTrickPlayTracks() {
        DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
        DefaultTrackSelector.ParametersBuilder builder = trackSelectorParameters.buildUpon();

        TrackSelectionArray trackSelections = player.getCurrentTrackSelections();
        for (int i = 0; i < player.getRendererCount() && i < trackSelections.length; i++) {
          if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO && trackSelections.get(i) != null) {
              builder.setRendererDisabled(i, true);
          }
        }
        trackSelector.setParameters(builder);
    }

    private void destroyTrickPlayMessageHandler() {
        currentHandler = null;
        player.setSeekParameters(SeekParameters.DEFAULT);
    }

    private void startTrickPlayMessageHandler() {
        destroyTrickPlayMessageHandler();
        currentHandler = new TrickPlayMessageHandler(player.getContentPosition());
        currentHandler.sendEmptyMessage(TrickPlayMessageHandler.MSG_TRICKPLAY_STARTSEEK);
    }

    private void enableLastSelectedAudioTrack() {
        if (lastSelectedAudioTrack >= 0 && lastSelectedAudioTrack < player.getRendererCount()) {
            DefaultTrackSelector.Parameters trackSelectorParameters = trackSelector.getParameters();
            DefaultTrackSelector.ParametersBuilder builder = trackSelectorParameters.buildUpon();
            builder.setRendererDisabled(lastSelectedAudioTrack, false);
            trackSelector.setParameters(builder);
        }
    }

    private void dispatchPlaylistMetadataChanged() {
        for (ListenerRef listenerRef : listeners) {
            Handler handler = getHandler(listenerRef);
            handler.post(() -> listenerRef.listener.playlistMetadataValid(isMetadataValid));
        }
    }

    private void dispatchTrickModeChanged(TrickMode newMode, TrickMode prevMode) {
        for (ListenerRef listenerRef : listeners) {
            Handler handler = getHandler(listenerRef);
            handler.post(() -> listenerRef.listener.trickPlayModeChanged(newMode, prevMode));
        }
    }

    protected void dispatchTrickFrameRender(long renderedFramePositionUs) {
        lastRenderPositions.add(renderedFramePositionUs);
        for (ListenerRef listenerRef : listeners) {
            Handler handler = getHandler(listenerRef);
            handler.post(() -> listenerRef.listener.trickFrameRendered(renderedFramePositionUs));
        }
    }

    private Handler getHandler(ListenerRef listenerRef) {
        return listenerRef.callType == ListenerRef.CallType.APPLICATION ?
            applicatonHandler : playbackHandler;
    }


    @Override
    public void addEventListener(TrickPlayEventListener eventListener) {
        listeners.add(new ListenerRef(ListenerRef.CallType.APPLICATION, eventListener));
    }

    @Override
    public void removeEventListener(TrickPlayEventListener eventListener) {
        removeListenerWithType(eventListener, ListenerRef.CallType.APPLICATION);
    }

    @Override
    public void addEventListenerInternal(TrickPlayEventListener eventListener) {
        listeners.add(new ListenerRef(ListenerRef.CallType.PLAYER, eventListener));
    }

    @Override
    public void removeEventListenerInternal(TrickPlayEventListener eventListener) {
        removeListenerWithType(eventListener, ListenerRef.CallType.PLAYER);
    }

    private boolean isVideoFormat(Format format) {
        boolean isVideo = false;
        int trackType = MimeTypes.getTrackType(format.sampleMimeType);
        if (trackType != C.TRACK_TYPE_UNKNOWN) {
            isVideo = trackType == C.TRACK_TYPE_VIDEO;
        } else {
            isVideo = MimeTypes.getVideoMediaMimeType(format.codecs) != null;
        }
        return isVideo;
    }

    private void removeListenerWithType(TrickPlayEventListener eventListener, ListenerRef.CallType callType) {
        for (ListenerRef listener : listeners) {
            if (listener.listener == eventListener && listener.callType == callType) {
                listeners.remove(listener);
            }
        }
    }
}

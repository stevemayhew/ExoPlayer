package com.tivo.exoplayer.library;// Copyright 2010 TiVo Inc.  All rights reserved.

import android.net.Uri;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.source.MediaSource;

public interface MediaSourceLifeCycle extends
    DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery {

  /**
   * Stops playback of the current URL and re-starts playback of the indicated URL.
   *
   * This method Creates a {@link MediaSource} and makes it the current mediasource.  If the
   * player is set to play when ready playback will begin as soon as buffering completes.
   *
   * @param uri
   * @param enableChunkless
   */
  void playUrl(Uri uri, boolean enableChunkless);

  @Override
  boolean recoverFrom(ExoPlaybackException e);
}

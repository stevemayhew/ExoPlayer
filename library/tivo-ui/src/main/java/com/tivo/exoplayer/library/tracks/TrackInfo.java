package com.tivo.exoplayer.library.tracks;

import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * ExoPlayer trackselection is based on constraints, that is instead of waiting till
 * the tracks present themselves in the media then choosing from a set of tracks you use contraint based
 * selection using the {@link DefaultTrackSelector#buildUponParameters()} to add constraints, for example to
 * choose english audio use {@link DefaultTrackSelector.ParametersBuilder#setPreferredAudioLanguage(String)}
 *
 * For simple operations, like turning on/off CC and selecting the caption language
 * you can use {@link com.tivo.exoplayer.library.SimpleExoPlayerFactory#setCloseCaption(boolean, String)}
 *
 * The other option is to wait till tracks present themselves then select specific tracks with overrides
 * ({@see https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/trackselection/DefaultTrackSelector.html})
 *
 * This object can be used to facilitate an 'override selection' (using {@link com.tivo.exoplayer.library.SimpleExoPlayerFactory#selectionOverride()}
 * The {@link com.google.android.exoplayer2.SimpleExoPlayer#getTrackInfo}
 * In ExoPlayer every available track (including individual bitrates for an adaptive stream) are represented
 * by a {@link Format} in a @{link {@link com.google.android.exoplayer2.source.TrackGroupArray}}.
 * So it is possible to select a specific bitrate with an
 */
public class TrackInfo {
  public static final int TRACK_TYPE_CC = C.TRACK_TYPE_TEXT;
  public static final int TRACK_TYPE_AUDIO = C.TRACK_TYPE_AUDIO;

  public final int type;
  public final boolean isSelected;
  public final Format format;
  public String desc;

  public TrackInfo(Format format, boolean isSelected) {
    this.isSelected = isSelected;
    this.format = format;

    type = MimeTypes.getTrackType(format.sampleMimeType);

    // TODO probably cleaner to use the TrackNameProvider.
    desc = TextUtils.isEmpty(format.label) ? "" : format.label;
  }

  /**
   * Get default or last set descriptive text
   *
   * @return
   */
  public String getDesc() {
    return desc;
  }

  /**
   * Allow caller to update the descriptive text with something more germane to the UI
   * they are using.  For example, by using the {@link TrackNameProvider} with localized
   * resources.
   *
   * @param desc
   */
  public void setDesc(String desc) {
    this.desc = desc;
  }

  /**
   * Set the track title using a track name provider to provide the name based on the
   * format.  The {@link com.google.android.exoplayer2.ui.DefaultTrackNameProvider} does
   * this with localized resources.
   *
   * @param provider
   * @return updated description
   */
  public String setDescWithProvider(TrackNameProvider provider) {
    desc = provider.getTrackName(format);
    return desc;
  }
}

package com.tivo.exoplayer.demo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple single choice list to present override based track selections
 * based on [@link {@link com.tivo.exoplayer.library.tracks.TrackInfo}
 */
public class TrackInfoSelectionDialog extends DialogFragment {

  private String title;
  private List<TrackInfo> choices;
  private SimpleExoPlayerFactory exoPlayerFactory;
  private TrackNameProvider nameProvider;

  /**
   * Create dialog with default name provider.
   *  @param title
   * @param choices
   * @param exoPlayerFactory
   */
  public static TrackInfoSelectionDialog createForChoices(String title, List<TrackInfo> choices,
      SimpleExoPlayerFactory exoPlayerFactory) {
    return createForChoices(title, choices, exoPlayerFactory, null);
  }

  /**
   * Create dialog
   *  @param title
   * @param choices
   * @param exoPlayerFactory
   * @param nameProvider
   */
  public static TrackInfoSelectionDialog createForChoices(String title, List<TrackInfo> choices,
      SimpleExoPlayerFactory exoPlayerFactory,
      @Nullable TrackNameProvider nameProvider) {
    return new TrackInfoSelectionDialog(title, choices, exoPlayerFactory, nameProvider);
  }

  private TrackInfoSelectionDialog(String title, List<TrackInfo> choices,
        SimpleExoPlayerFactory exoPlayerFactory,
        @Nullable TrackNameProvider nameProvider) {
    super();
    this.title = title;
    this.choices = choices;
    this.exoPlayerFactory = exoPlayerFactory;
    this.nameProvider = nameProvider;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return super.onCreateView(inflater, container, savedInstanceState);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    if (nameProvider == null) {
      nameProvider = new DefaultTrackNameProvider(getResources());
    }
    List<String> trackNames = new ArrayList<>();
    for (TrackInfo choice : choices) {
      trackNames.add(choice.setDescWithProvider(nameProvider));
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(title);
    builder.setItems(trackNames.toArray(new String[0]), (dialog, which) -> {
      exoPlayerFactory.selectTrack(choices.get(which));
    });
    return builder.create();
  }
}

# ExoPlayer Shared UI library module #

## Summary
Library for constructing and using a SimpleExoPLayer with TiVo extensions and debug tools

The TiVo created tenfoot UI demo (demos/tenfoot) demonstrates use of this library with a simple
player.

## Basic Usage
This library depends on the TiVo required ExoPlayer libraries:

1. `library-trickplay` &mdash; provides full screen trickplay with iFrame playlist
2. `library-hls` &mdash; needed for HLS playback (TODO if we need dash)
3. `library-core` &mdash; the core ExoPlayer with TiVo specific mods
4. `library-ui` &mdash; the ExoPlayer ui components library

The main class in this library is the `SimpleExoPlayerFactory` this class creates the `SimpleExoPlayer` and manages it's lifecycle as well as creating all the factories and ancillary objects required by `SimpleExoPlayer` to support trick-play.  This object is very light-weight in itself, you can create the factory and keep it around till your app is destroyed.

~~~java
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Context context = getApplicationContext();
    exoPlayerFactory = new SimpleExoPlayerFactory(context, false);
~~~

Use the factory in your start and stop methods create the player.  Assuming you are using ExoPlayer's `PlayerView` code would look like this

~~~java
  @Override
   public void onStart() {
     super.onStart();
     SimpleExoPlayer player = exoPlayerFactory.createPlayer(true);
     playerView.setPlayer(player);
     geekStats.setPlayer(player, exoPlayerFactory.getCurrentTrickPlayControl());
~~~

And finally to free up the player and resources it uses, do this in onStop:

~~~java
   @Override
   public void onStop() {
     super.onStop();
     exoPlayerFactory.releasePlayer();
~~~

At anytime you can access the player and trick-play interfaces with the calls `exoPlayerFactory.getCurrentPlayer()` and `exoPlayerFactory.getCurrentTrickPlayControl()`.   Use the `exoPlayerFactory.playUrl()` method to play content, this allows this library to manage the `MediaSource` and error recovery thereof.

## Extending

The internals of this module are extensible, two factory methods in the `SimpleExoPlayerFactory` allow clients to add their own or extend the provided `MediaSourceLifeCycle` and error handling.

### MediaSourceLifeCycle
The `SimpleExoPlayerFactory` method `createMediaSourceLifeCycle()` creates the implementation of this interface.  This object is responsible for creating the `MediaSource` and recovering any playback errors that are `MediaSource` related (eg. `BehindLiveWindowException`).  

Clients can either extend `DefaultMediaSourceLifeCycle` to customize the implementation or create their own.

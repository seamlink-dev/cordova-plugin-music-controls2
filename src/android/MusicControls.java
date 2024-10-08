package com.homerours.musiccontrols;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.os.Build;
import android.media.AudioManager;
import android.graphics.Bitmap;
//import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//import android.app.Notification;
//import android.app.Activity;
//import android.app.Service;
//import android.os.Bundle;
//import android.os.IBinder;
//import android.R;
//import android.media.session.MediaSession.Token;
//import android.content.ServiceConnection;
//import android.content.ComponentName;
//import org.apache.cordova.PluginResult;

public class MusicControls extends CordovaPlugin {
	private MusicControlsBroadcastReceiver mMessageReceiver;
	private MusicControlsNotification notification;
	private MediaSessionCompat mediaSessionCompat;
	private PendingIntent mediaButtonPendingIntent;
	private final int notificationID = 7824;
	private AudioManager mAudioManager;
	private boolean mediaButtonAccess = true;
	private long playbackPosition = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
	private android.media.session.MediaSession.Token token;

	private Activity cordovaActivity;

	private MediaSessionCallback mMediaSessionCallback = new MediaSessionCallback();

	private void setMediaPlaybackState(int state) {
		long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE |
				PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
				PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
				PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
				PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;

		float playbackSpeed;
		long position;
		if (state == PlaybackStateCompat.STATE_PLAYING) {
			actions |= PlaybackStateCompat.ACTION_PAUSE;
			// We keep the speed 0 to control the state on the consumer app, avoiding issues
			// of synching
			// playbackSpeed = 1.0f;
			playbackSpeed = 0;
			position = playbackPosition;
		} else {
			actions |= PlaybackStateCompat.ACTION_PLAY;
			playbackSpeed = 0;
			// position = this.mediaSessionCompat.
		}

		if (playbackPosition != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) {
			actions |= PlaybackStateCompat.ACTION_SEEK_TO;
		}

		PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
				.setActions(actions)
				.setState(state, playbackPosition, playbackSpeed)
				.build();
		this.mediaSessionCompat.setPlaybackState(playbackState);
	}

	private void setMediaSession(Context context) {

		this.mediaSessionCompat = new MediaSessionCompat(context, "cordova-music-controls-media-session", null,
				this.mediaButtonPendingIntent);
		this.mediaSessionCompat.setFlags(
				MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

		MediaSessionCompat.Token _token = this.mediaSessionCompat.getSessionToken();
		this.token = (android.media.session.MediaSession.Token) _token.getToken();

		setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

		this.mediaSessionCompat.setActive(true);

		this.mediaSessionCompat.setCallback(this.mMediaSessionCallback);

	}

	private void registerBroadcaster(MusicControlsBroadcastReceiver mMessageReceiver) {
		final Context context = this.cordova.getActivity().getApplicationContext();

		IntentFilter[] receiverFilters = {
				new IntentFilter(CallbackUtils.MUSIC_CONTROLS_PREVIOUS), // music-controls-previous
				new IntentFilter(CallbackUtils.MUSIC_CONTROLS_PAUSE), // music-controls-pause
				new IntentFilter(CallbackUtils.MUSIC_CONTROLS_PLAY), // music-controls-play
				new IntentFilter(CallbackUtils.MUSIC_CONTROLS_NEXT), // music-controls-next
				new IntentFilter(CallbackUtils.MUSIC_CONTROLS_MEDIA_BUTTON), // music-controls-media-button
				new IntentFilter(CallbackUtils.MUSIC_CONTROLS_DESTROY), // music-controls-destroy
				// Listen for headset plug/unplug
				new IntentFilter(Intent.ACTION_HEADSET_PLUG),
				// Listen for bluetooth connection state changes
				new IntentFilter(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED),
		};

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			for (IntentFilter receiverFilter : receiverFilters) {
				context.registerReceiver((BroadcastReceiver) mMessageReceiver, receiverFilter,
						Context.RECEIVER_NOT_EXPORTED);
			}
		} else {
			for (IntentFilter receiverFilter : receiverFilters) {
				context.registerReceiver((BroadcastReceiver) mMessageReceiver, receiverFilter);
			}
		}
	}

	// Register pendingIntent for broadcast
	public void registerMediaButtonEvent() {

		this.mediaSessionCompat.setMediaButtonReceiver(this.mediaButtonPendingIntent);

		/*
		 * if (this.mediaButtonAccess && android.os.Build.VERSION.SDK_INT >=
		 * android.os.Build.VERSION_CODES.JELLY_BEAN_MR2){
		 * this.mAudioManager.registerMediaButtonEventReceiver(this.
		 * mediaButtonPendingIntent);
		 * }
		 */
	}

	public void unregisterMediaButtonEvent() {
		this.mediaSessionCompat.setMediaButtonReceiver(null);
		/*
		 * if (this.mediaButtonAccess && android.os.Build.VERSION.SDK_INT >=
		 * android.os.Build.VERSION_CODES.JELLY_BEAN_MR2){
		 * this.mAudioManager.unregisterMediaButtonEventReceiver(this.
		 * mediaButtonPendingIntent);
		 * }
		 */
	}

	public void destroyPlayerNotification() {
		this.notification.destroy();
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		final Activity activity = this.cordova.getActivity();
		final Context context = activity.getApplicationContext();

		// Notification Killer
		final MusicControlsServiceConnection mConnection = new MusicControlsServiceConnection(activity);

		this.cordovaActivity = activity;

		this.mMessageReceiver = new MusicControlsBroadcastReceiver(this);
		this.registerBroadcaster(mMessageReceiver);

		// Register media (headset) button event receiver
		// Moving this block before the mediaSessionCompat declaration so we have the
		// mediaButtonPendingIntent created.
		try {
			this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			Intent headsetIntent = new Intent(CallbackUtils.MUSIC_CONTROLS_MEDIA_BUTTON); // music-controls-media-button
			headsetIntent.setPackage(context.getPackageName());
			this.mediaButtonPendingIntent = PendingIntent.getBroadcast(
					context, 0, headsetIntent,
					Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
							? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
							: PendingIntent.FLAG_UPDATE_CURRENT);
		} catch (Exception e) {
			this.mediaButtonAccess = false;
			e.printStackTrace();
		}

		this.setMediaSession(context);

		this.notification = new MusicControlsNotification(this.cordovaActivity, this.notificationID, this.token) {
			@Override
			protected void onNotificationUpdated(Notification notification) {
				mConnection.setNotification(notification, this.infos.isPlaying);
			}

			@Override
			protected void onNotificationDestroyed() {
				mConnection.killNotification();
			}
		};

		// Splitting declaration Register media (headset) button event receiver to
		// ensure we have the mediaSessionCompat object created to trigger the
		// registerMediaButton
		try {
			this.registerMediaButtonEvent();
		} catch (Exception e) {
			this.mediaButtonAccess = false;
			e.printStackTrace();
		}

		Intent startServiceIntent = new Intent(activity, MusicControlsNotificationKiller.class);
		startServiceIntent.putExtra("notificationID", this.notificationID);
		activity.bindService(startServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext)
			throws JSONException {
		final Activity activity = this.cordova.getActivity();
		final Context context = this.cordova.getActivity().getApplicationContext();

		switch (action) {
			case "create":
				cordova.getThreadPool().execute(() -> {
					try {
						final MusicControlsInfos infos = new MusicControlsInfos(args);
						final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

						// track title
						metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, infos.track);
						// artists
						metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, infos.artist);
						// album
						metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, infos.album);

						// scrubber
						if (infos.hasScrubbing) {
							metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, infos.duration);
							playbackPosition = infos.elapsed;
						} else {
							playbackPosition = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
						}

						Bitmap art = BitmapUtils.getBitmapCover(context, infos.cover);
						if (art != null) {
							metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
							metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art);
						}

						mediaSessionCompat.setMetadata(metadataBuilder.build());

						// Moving this update after metadataBuilder settings
						notification.updateNotification(infos);

						if (infos.isPlaying)
							setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
						else
							setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

						callbackContext.success("success");
					} catch (JSONException e) {
						e.printStackTrace();
						callbackContext.error(e.getLocalizedMessage());
					}
				});
				break;

			case "updateIsPlaying":
				cordova.getThreadPool().execute(() -> {
					try {
						final JSONObject params = args.getJSONObject(0);
						final boolean isPlaying = params.getBoolean("isPlaying");
						this.notification.updateIsPlaying(isPlaying);

						if (isPlaying)
							setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
						else
							setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);

						callbackContext.success("success");
					} catch (JSONException e) {
						e.printStackTrace();
						callbackContext.error(e.getLocalizedMessage());
					}
				});
				break;

			case "updateShuffle":
				cordova.getThreadPool().execute(() -> {
					try {
						final JSONObject params = args.getJSONObject(0);
						final boolean isShuffle = params.getBoolean("isShuffle");
						mediaSessionCompat.setShuffleMode(
								isShuffle ? PlaybackStateCompat.SHUFFLE_MODE_ALL
										: PlaybackStateCompat.SHUFFLE_MODE_NONE);
						callbackContext.success("success");
					} catch (JSONException e) {
						e.printStackTrace();
						callbackContext.error(e.getLocalizedMessage());
					}
				});
				break;

			case "updateIsBuffering":
				cordova.getThreadPool().execute(() -> {
					try {
						final JSONObject params = args.getJSONObject(0);
						playbackPosition = params.getLong("elapsed");
						final boolean isBuffering = params.getBoolean("isBuffering");
						setMediaPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
						callbackContext.success("success");
					} catch (JSONException e) {
						e.printStackTrace();
						callbackContext.error(e.getLocalizedMessage());
					}
				});
				break;

			case "updateDismissable":
				cordova.getThreadPool().execute(() -> {
					try {
						final JSONObject params = args.getJSONObject(0);
						final boolean dismissable = params.getBoolean("dismissable");
						this.notification.updateDismissable(dismissable);
						callbackContext.success("success");
					} catch (JSONException e) {
						e.printStackTrace();
						callbackContext.error(e.getLocalizedMessage());
					}
				});
				break;

			case "updateElapsed":
				cordova.getThreadPool().execute(() -> {
					try {
						final JSONObject params = args.getJSONObject(0);
						playbackPosition = params.getLong("elapsed");
						final boolean isPlaying = params.getBoolean("isPlaying");
						this.notification.updateIsPlaying(isPlaying);

						if (isPlaying)
							setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
						else
							setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
						callbackContext.success("success");
					} catch (JSONException e) {
						e.printStackTrace();
						callbackContext.error(e.getLocalizedMessage());
					}
				});
				break;

			case "destroy":
				cordova.getThreadPool().execute(() -> {
						this.notification.destroy();
						this.mMessageReceiver.stopListening();
						callbackContext.success("success");
				});
				break;

			case "watch":
				cordova.getThreadPool().execute(() -> {
						mMediaSessionCallback.setCallback(callbackContext);
						mMessageReceiver.setCallback(callbackContext);
				});
				break;
		}

		return true;

	}

	@Override
	public void onDestroy() {
		this.notification.destroy();
		this.mMessageReceiver.stopListening();
		this.unregisterMediaButtonEvent();
		super.onDestroy();
	}

	@Override
	public void onReset() {
		onDestroy();
		super.onReset();
	}

}

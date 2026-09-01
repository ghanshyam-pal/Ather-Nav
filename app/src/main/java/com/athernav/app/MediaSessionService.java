package com.athernav.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.util.List;
import java.util.ArrayList;

/**
 * FIX 1: Extends MediaBrowserServiceCompat instead of plain Service
 * This registers us as a proper media app that Bluetooth devices
 * (including Ather's BLE stack) discover and connect to officially.
 * VLC, Spotify all extend this — that's why they work and we didn't.
 */
public class MediaSessionService extends MediaBrowserServiceCompat {

    private static final String CHANNEL_ID         = "athernav_channel";
    private static final int    NOTIF_ID           = 1001;
    public  static final String ACTION_UPDATE      = "com.athernav.app.UPDATE";
    public  static final String ACTION_SWITCH_MODE = "com.athernav.app.SWITCH_MODE";
    public  static final String EXTRA_NAV_TEXT     = "nav_text";
    public  static final String EXTRA_SOURCE       = "source";
    private static final long   LONG_PRESS_MS      = 800;

    private static MediaSessionCompat activeSession;

    public static MediaSessionCompat getMediaSession() {
        return activeSession;
    }

    private MediaSessionCompat mediaSession;
    private MediaPlayer        silentPlayer;
    private AudioFocusRequest  audioFocusRequest;
    private String             currentText = "ATHERNAV";

    // FIX 2: Incrementing position counter — makes session look "alive"
    private long    fakePosition    = 0;
    private static final long FAKE_DURATION = 999999000L; // ~277 hours
    private final Handler positionHandler = new Handler(Looper.getMainLooper());
    private Runnable positionRunnable;

    // FIX 3: Periodic metadata refresh — keeps our session timestamp newest
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;

    // Mode display handler
    private final Handler modeDisplayHandler = new Handler(Looper.getMainLooper());

    // Button long press tracking
    private long nextPressTime = 0;
    private long prevPressTime = 0;

    // FIX 4: Bluetooth connection receiver
    private BroadcastReceiver bluetoothReceiver;

    // Data fetchers
    private WeatherFetcher weatherFetcher;
    private SystemMonitor  systemMonitor;
    private CricketFetcher cricketFetcher;

    // Last values per mode
    private String lastNavText     = "WAITING";
    private String lastWeatherText = "FETCHING";
    private String lastNotifyText  = "NO-NOTIFY";
    private String lastSystemText  = "SYS-LOAD";
    private String lastSportsText  = "NO-MATCH";
    private String lastMusicText   = "NO-MUSIC";

    // -----------------------------------------------
    // LIFECYCLE
    // -----------------------------------------------
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        requestAudioFocus();
        initMediaSession();
        startSilentPlayer();
        startPositionCounter();   // FIX 2
        startMetadataRefresh();   // FIX 3
        registerBluetoothReceiver(); // FIX 4
        initWeather();
        initSystem();
        initCricket();
        startForeground(NOTIF_ID, buildNotification(currentText));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE.equals(intent.getAction())) {
            String text   = intent.getStringExtra(EXTRA_NAV_TEXT);
            String source = intent.getStringExtra(EXTRA_SOURCE);
            if (text != null && !text.isEmpty()) {
                handleUpdate(text, source != null ? source : "NAV");
            }
        } else if (intent != null && ACTION_SWITCH_MODE.equals(intent.getAction())) {
            switchMode(true);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        positionHandler.removeCallbacks(positionRunnable);
        refreshHandler.removeCallbacks(refreshRunnable);
        if (bluetoothReceiver != null) unregisterReceiver(bluetoothReceiver);
        stopSilentPlayer();
        if (audioFocusRequest != null) {
            ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .abandonAudioFocusRequest(audioFocusRequest);
        }
        weatherFetcher.stop();
        systemMonitor.stop();
        cricketFetcher.stop();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    // -----------------------------------------------
    // FIX 1: MediaBrowserServiceCompat required overrides
    // -----------------------------------------------
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                  int clientUid,
                                  @Nullable Bundle rootHints) {
        // Allow all clients to connect (Ather, BT headsets, etc)
        return new BrowserRoot("athernav_root", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId,
                                @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        // We don't serve a media library — just deliver empty list
        result.sendResult(null);
    }

    // -----------------------------------------------
    // FIX 2: Incrementing position counter
    // -----------------------------------------------
    private void startPositionCounter() {
        positionRunnable = new Runnable() {
            @Override
            public void run() {
                fakePosition += 1000; // +1 second every second
                updatePlaybackState();
                positionHandler.postDelayed(this, 1000);
            }
        };
        positionHandler.postDelayed(positionRunnable, 1000);
    }

    private void updatePlaybackState() {
        if (mediaSession == null) return;
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(PlaybackStateCompat.STATE_PLAYING, fakePosition, 1.0f)
            .build();
        mediaSession.setPlaybackState(state);
    }

    // -----------------------------------------------
    // FIX 3: Periodic metadata refresh every 10 seconds
    // -----------------------------------------------
    private void startMetadataRefresh() {
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // Re-push current text — updates session timestamp
                // making us always the "most recently active" session
                setMetadata(currentText, getModeLabel(), "ATHERNAV");
                refreshHandler.postDelayed(this, 10000);
            }
        };
        refreshHandler.postDelayed(refreshRunnable, 10000);
    }

    //  private void startMetadataRefresh() {
    //     refreshRunnable = new Runnable() {
    //         @Override public void run() {
    //             refreshToggle++;
    //             // Alternate artist field between two values
    //             String artist = (refreshToggle % 2 == 0) ? "ATHERNAV" : "NAVIGATION";
    //             // Append toggling zero-width space so title bytes change
    //             // Ather BLE sees different content → stays connected
    //             String suffix = (refreshToggle % 2 == 0) ? "" : "\u200B";
    //             if (mediaSession != null) {
    //                 mediaSession.setMetadata(new MediaMetadataCompat.Builder()
    //                     .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
    //                         currentText + suffix)
    //                     .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
    //                     .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "ATHERNAV")
    //                     .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, FAKE_DURATION)
    //                     .build());
    //                 updateQueue(currentText + suffix, artist);
    //             }
    //             refreshHandler.postDelayed(this, 30000); // every 30 seconds
    //         }
    //     };
    //     refreshHandler.postDelayed(refreshRunnable, 30000);
    // }

    // -----------------------------------------------
    // FIX 4: Bluetooth connection receiver
    // Recreates MediaSession when Ather connects so our
    // session timestamp is always newer than stale sessions
    // -----------------------------------------------
    private void registerBluetoothReceiver() {
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    // Small delay to let BT stack settle before we announce
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        recreateMediaSession();
                    }, 1500);
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void recreateMediaSession() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        fakePosition = 0;
        initMediaSession();
        // Immediately push current text so dashboard updates right away
        setMetadata(currentText, getModeLabel(), "ATHERNAV");
        updatePlaybackState();
        // Rebuild notification with new session token
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(currentText));
    }

    // -----------------------------------------------
    // AUDIO FOCUS
    // -----------------------------------------------
    private void requestAudioFocus() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    if (ModeManager.getCurrentMode() != ModeManager.Mode.MUSIC) {
                        am.requestAudioFocus(audioFocusRequest);
                    }
                }
            })
            .build();
        am.requestAudioFocus(audioFocusRequest);
    }

    // -----------------------------------------------
    // SILENT PLAYER
    // -----------------------------------------------
    private void startSilentPlayer() {
        silentPlayer = MediaPlayer.create(this, R.raw.silence);
        if (silentPlayer != null) {
            silentPlayer.setLooping(true);
            silentPlayer.setVolume(0f, 0f);
            silentPlayer.start();
        }
    }

    private void stopSilentPlayer() {
        if (silentPlayer != null) {
            try { silentPlayer.stop(); } catch (Exception ignored) {}
            silentPlayer.release();
            silentPlayer = null;
        }
    }

    // -----------------------------------------------
    // MEDIA SESSION INIT
    // -----------------------------------------------
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "AtherNavSession");
        activeSession = mediaSession;

        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        // FIX: Set launchIntent — VLC has this, we were missing it
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent launchPi = PendingIntent.getActivity(this, 0,
            launchIntent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setSessionActivity(launchPi);

        // FIX: Set MediaButtonReceiver — this is what Ather checks
        // Without this, mediaButtonReceiver=null in dumpsys → Ather ignores us
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, AtherNavMediaButtonReceiver.class);
        PendingIntent mediaButtonPi = PendingIntent.getBroadcast(this, 0,
            mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setMediaButtonReceiver(mediaButtonPi);

        // FIX: Add a queue with one item — VLC has queueTitle=Now Playing, size=1
        // Empty queue signals "not a real player" to some BT stacks
        List<MediaSessionCompat.QueueItem> queue = new java.util.ArrayList<>();
        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentText)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getModeLabel())
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "ATHERNAV")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, FAKE_DURATION);
        android.support.v4.media.MediaDescriptionCompat desc =
            new android.support.v4.media.MediaDescriptionCompat.Builder()
                .setTitle(currentText)
                .setSubtitle(getModeLabel())
                .build();
        queue.add(new MediaSessionCompat.QueueItem(desc, 0));
        mediaSession.setQueue(queue);
        mediaSession.setQueueTitle("Now Playing");

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onSkipToNext() {
                long now = System.currentTimeMillis();
                if (ModeManager.getCurrentMode() == ModeManager.Mode.MUSIC) {
                    if ((now - nextPressTime) < LONG_PRESS_MS) {
                        switchMode(true);
                    } else {
                        dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
                    }
                } else {
                    switchMode(true);
                }
                nextPressTime = now;
            }

            @Override
            public void onSkipToPrevious() {
                long now = System.currentTimeMillis();
                if (ModeManager.getCurrentMode() == ModeManager.Mode.MUSIC) {
                    if ((now - prevPressTime) < LONG_PRESS_MS) {
                        switchMode(false);
                    } else {
                        dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                    }
                } else {
                    switchMode(false);
                }
                prevPressTime = now;
            }

            @Override
            public void onPlay() { showModeName(); }

            @Override
            public void onPause() {
                if (ModeManager.getCurrentMode() == ModeManager.Mode.MUSIC) {
                    dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
            }
        });

        setSessionToken(mediaSession.getSessionToken());
        updatePlaybackState();
        setMetadata(currentText, getModeLabel(), "ATHERNAV");
        mediaSession.setActive(true);
    }

    private void dispatchMediaKey(int keyCode) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN, keyCode));
            am.dispatchMediaKeyEvent(new android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP, keyCode));
        }
    }

    // -----------------------------------------------
    // UPDATE HANDLING
    // -----------------------------------------------
    private void handleUpdate(String text, String source) {
        ModeManager.Mode mode = ModeManager.getCurrentMode();
        switch (source) {
            case "NAV":
                lastNavText = text;
                if (mode == ModeManager.Mode.NAV || mode == ModeManager.Mode.RAW)
                    pushToDashboard(text);
                break;
            case "NOTIFY":
                lastNotifyText = text;
                if (mode == ModeManager.Mode.NOTIFY) pushToDashboard(text);
                break;
            case "WEATHER":
                lastWeatherText = text;
                if (mode == ModeManager.Mode.WEATHER) pushToDashboard(text);
                break;
            case "SYSTEM":
                lastSystemText = text;
                if (mode == ModeManager.Mode.SYSTEM) pushToDashboard(text);
                break;
            case "SPORTS":
                lastSportsText = text;
                if (mode == ModeManager.Mode.SPORTS) pushToDashboard(text);
                break;
            case "MUSIC":
                lastMusicText = text;
                if (mode == ModeManager.Mode.MUSIC) pushToDashboard(text);
                break;
        }
    }

    private void pushToDashboard(String text) {
        currentText = NavParser.sanitize(text);
        setMetadata(currentText, getModeLabel(), "ATHERNAV");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(currentText));
        Intent broadcast = new Intent("com.athernav.app.NAV_UPDATE");
        broadcast.putExtra("text", currentText);
        broadcast.putExtra("mode", ModeManager.getModeName());
        sendBroadcast(broadcast);
    }

    private void setMetadata(String title, String artist, String album) {
        if (mediaSession == null) return;
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, FAKE_DURATION)
            .build();
        mediaSession.setMetadata(metadata);

        // Keep queue in sync — Ather may read from queue description too
        List<MediaSessionCompat.QueueItem> queue = new java.util.ArrayList<>();
        android.support.v4.media.MediaDescriptionCompat desc =
            new android.support.v4.media.MediaDescriptionCompat.Builder()
                .setTitle(title)
                .setSubtitle(artist)
                .build();
        queue.add(new MediaSessionCompat.QueueItem(desc, 0));
        mediaSession.setQueue(queue);
        mediaSession.setQueueTitle("Now Playing");
    }

    private String getModeLabel() {
        switch (ModeManager.getCurrentMode()) {
            case NAV:     return "NAVIGATION";
            case RAW:     return "MAPS-RAW";
            case WEATHER: return "WEATHER";
            case NOTIFY:  return "NOTIFY";
            case SYSTEM:  return "SYSTEM";
            case SPORTS:  return "SPORTS";
            case MUSIC:   return "MUSIC";
            default:      return "ATHERNAV";
        }
    }

    // -----------------------------------------------
    // MODE SWITCHING
    // -----------------------------------------------
    private void switchMode(boolean forward) {
        ModeManager.Mode mode = forward ? ModeManager.nextMode() : ModeManager.prevMode();
        showModeName();

        // Pause silent player in MUSIC mode so Spotify isn't blocked
        if (mode == ModeManager.Mode.MUSIC) {
            if (silentPlayer != null && silentPlayer.isPlaying()) silentPlayer.pause();
        } else {
            if (silentPlayer != null && !silentPlayer.isPlaying()) {
                silentPlayer.start();
                ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                    .requestAudioFocus(audioFocusRequest);
            }
        }

        modeDisplayHandler.postDelayed(this::refreshCurrentMode, 2000);

        if (mode == ModeManager.Mode.WEATHER) {
            weatherFetcher.start(text -> {
                lastWeatherText = text;
                if (ModeManager.getCurrentMode() == ModeManager.Mode.WEATHER)
                    pushToDashboard(text);
            });
        }
        if (mode == ModeManager.Mode.SYSTEM) {
            systemMonitor.start(text -> {
                lastSystemText = text;
                if (ModeManager.getCurrentMode() == ModeManager.Mode.SYSTEM)
                    pushToDashboard(text);
            });
        }
        if (mode == ModeManager.Mode.SPORTS) {
            cricketFetcher.start(text -> {
                lastSportsText = text;
                if (ModeManager.getCurrentMode() == ModeManager.Mode.SPORTS)
                    pushToDashboard(text);
            });
        }
    }

    private void showModeName() {
        String modeName = ModeManager.getModeName();
        setMetadata(modeName, "ATHERNAV", "MODE");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(modeName));
        Intent broadcast = new Intent("com.athernav.app.NAV_UPDATE");
        broadcast.putExtra("text", modeName);
        broadcast.putExtra("mode", modeName);
        sendBroadcast(broadcast);
    }

    private void refreshCurrentMode() {
        switch (ModeManager.getCurrentMode()) {
            case NAV:
            case RAW:     pushToDashboard(lastNavText);     break;
            case WEATHER: pushToDashboard(lastWeatherText); break;
            case NOTIFY:  pushToDashboard(lastNotifyText);  break;
            case SYSTEM:  pushToDashboard(lastSystemText);  break;
            case SPORTS:  pushToDashboard(lastSportsText);  break;
            case MUSIC:   pushToDashboard(lastMusicText);   break;
        }
    }

    // -----------------------------------------------
    // FETCHER INIT
    // -----------------------------------------------
    private void initWeather() {
        weatherFetcher = new WeatherFetcher();
        weatherFetcher.start(text -> {
            lastWeatherText = text;
            if (ModeManager.getCurrentMode() == ModeManager.Mode.WEATHER)
                pushToDashboard(text);
        });
    }

    private void initSystem() {
        systemMonitor = new SystemMonitor(this);
        systemMonitor.start(text -> {
            lastSystemText = text;
            if (ModeManager.getCurrentMode() == ModeManager.Mode.SYSTEM)
                pushToDashboard(text);
        });
    }

    private void initCricket() {
        cricketFetcher = new CricketFetcher();
    }

    // -----------------------------------------------
    // NOTIFICATION
    // -----------------------------------------------
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "AtherNav Active", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Navigation mirroring to Ather dashboard");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(String navText) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(navText)
            .setContentText(ModeManager.getModeName() + " - AtherNav")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView())
            .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Let MediaBrowserServiceCompat handle its own binding
        return super.onBind(intent);
    }
}

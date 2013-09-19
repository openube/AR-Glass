/*
 * ControlDroneActivity
 *
 * Created on: May 5, 2011
 * Author: Dmytro Baryskyy
 */

package com.parrot.freeflight.activities;

import android.annotation.SuppressLint;
import android.content.*;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import com.parrot.freeflight.FreeFlightApplication;
import com.parrot.freeflight.R;
import com.parrot.freeflight.activities.base.ParrotActivity;
import com.parrot.freeflight.controllers.Controller;
import com.parrot.freeflight.drone.DroneConfig;
import com.parrot.freeflight.drone.DroneConfig.EDroneVersion;
import com.parrot.freeflight.drone.NavData;
import com.parrot.freeflight.receivers.*;
import com.parrot.freeflight.sensors.DeviceOrientationManager;
import com.parrot.freeflight.service.DroneControlService;
import com.parrot.freeflight.settings.ApplicationSettings;
import com.parrot.freeflight.settings.ApplicationSettings.ControlMode;
import com.parrot.freeflight.settings.ApplicationSettings.EAppSettingProperty;
import com.parrot.freeflight.transcodeservice.TranscodingService;
import com.parrot.freeflight.ui.HudViewController;
import com.parrot.freeflight.ui.SettingsDialogDelegate;

import java.io.File;

@SuppressLint("NewApi")
public class ControlDroneActivity
        extends ParrotActivity
        implements WifiSignalStrengthReceiverDelegate,
        DroneVideoRecordStateReceiverDelegate, DroneEmergencyChangeReceiverDelegate,
        DroneBatteryChangedReceiverDelegate, DroneFlyingStateReceiverDelegate,
        DroneCameraReadyActionReceiverDelegate, DroneRecordReadyActionReceiverDelegate,
        SettingsDialogDelegate {
    private static final int LOW_DISK_SPACE_BYTES_LEFT = 1048576 * 20; // 20 mebabytes
    private static final int WARNING_MESSAGE_DISMISS_TIME = 5000; // 5 seconds

    private static final String TAG = ControlDroneActivity.class.getName();

    private DroneControlService droneControlService;
    private ApplicationSettings settings;
    private SettingsDialog settingsDialog;

    private Controller mController;

    private HudViewController view;

    private WifiSignalStrengthChangedReceiver wifiSignalReceiver;
    private DroneVideoRecordingStateReceiver videoRecordingStateReceiver;
    private DroneEmergencyChangeReceiver droneEmergencyReceiver;
    private DroneBatteryChangedReceiver droneBatteryReceiver;
    private DroneFlyingStateReceiver droneFlyingStateReceiver;
    private DroneCameraReadyChangeReceiver droneCameraReadyChangedReceiver;
    private DroneRecordReadyChangeReceiver droneRecordReadyChangeReceiver;

    private SoundPool soundPool;
    private int batterySoundId;
    private int effectsStreamId;

    private boolean magnetoAvailable;
    private boolean controlLinkAvailable;

    private boolean pauseVideoWhenOnSettings;

    private boolean flying;
    private boolean recording;
    private boolean cameraReady;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            droneControlService = ((DroneControlService.LocalBinder) service).getService();
            onDroneServiceConnected();

            if (view != null && !view.isInTouchMode()) {
                DroneConfig droneConfig = droneControlService.getDroneConfig();
                if (droneConfig == null)
                    return;

                // Increase the yaw speed
                droneConfig.setYawSpeedMax(DroneConfig.YAW_MAX);

                // Increase the vertical speed
                droneConfig.setVertSpeedMax(DroneConfig.VERT_SPEED_MAX);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            droneControlService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isFinishing()) {
            return;
        }

        view = new HudViewController(this);

        settings = getSettings();

        bindService(new Intent(this, DroneControlService.class), mConnection,
                Context.BIND_AUTO_CREATE);

        pauseVideoWhenOnSettings = getResources().getBoolean(
                R.bool.settings_pause_video_when_opened);

        wifiSignalReceiver = new WifiSignalStrengthChangedReceiver(this);
        videoRecordingStateReceiver = new DroneVideoRecordingStateReceiver(this);
        droneEmergencyReceiver = new DroneEmergencyChangeReceiver(this);
        droneBatteryReceiver = new DroneBatteryChangedReceiver(this);
        droneFlyingStateReceiver = new DroneFlyingStateReceiver(this);
        droneCameraReadyChangedReceiver = new DroneCameraReadyChangeReceiver(this);
        droneRecordReadyChangeReceiver = new DroneRecordReadyChangeReceiver(this);

        soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        batterySoundId = soundPool.load(this, R.raw.battery, 1);

        // TODO: Create the controller based on user preference
        mController = Controller.ControllerType.GAMEPAD.getImpl(this);

        DeviceOrientationManager orientationManager = mController.getDeviceOrientationManager();

        if (orientationManager != null && !orientationManager.isAcceleroAvailable()) {
            settings.setControlMode(ControlMode.NORMAL_MODE);
        }

        settings.setFirstLaunch(false);

        view.setCameraButtonEnabled(false);
        view.setRecordButtonEnabled(false);

    }

    public boolean isInTouchMode() {
        return view != null && view.isInTouchMode();
    }

    public HudViewController getHudView() {
        return view;
    }

    public void setDeviceOrientation(int heading, int accuracy) {
        if (droneControlService != null)
            droneControlService.setDeviceOrientation(heading, accuracy);
    }

    public void setDroneRoll(float roll) {
        if (droneControlService != null)
            droneControlService.setRoll(roll);
    }

    public void setDronePitch(float pitch) {
        if (droneControlService != null)
            droneControlService.setPitch(pitch);
    }

    public void setDroneGaz(float gaz) {
        if (droneControlService != null) {
            droneControlService.setGaz(gaz);
        }
    }

    public void setDroneYaw(float yaw) {
        if (droneControlService != null) {
            droneControlService.setYaw(yaw);
        }
    }

    public void setDroneProgressiveCommandEnabled(boolean enable) {
        if (droneControlService != null)
            droneControlService.setProgressiveCommandEnabled(enable);
    }

    public void setDroneProgressiveCommandCombinedYawEnabled(boolean enable) {
        if (droneControlService != null)
            droneControlService.setProgressiveCommandCombinedYawEnabled(enable);
    }

    public void switchDroneCamera(){
        if(droneControlService != null)
            droneControlService.switchCamera();
    }

    public void triggerDroneTakeOff() {
        if (droneControlService != null)
            droneControlService.triggerTakeOff();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mController != null && mController.onGenericMotion(view.getRootView(),
                event) || super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mController != null && mController.onKeyDown(keyCode,
                event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mController != null && mController.onKeyUp(keyCode,
                event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mController != null && mController.onTouch(view.getRootView(),
                event) || super.onTouchEvent(event);
    }

    private void initListeners() {
        view.setSettingsButtonClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        view.setBtnCameraSwitchClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (droneControlService != null) {
                    droneControlService.switchCamera();
                }
            }
        });

        view.setBtnTakeOffClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (droneControlService != null) {
                    droneControlService.triggerTakeOff();
                }
            }
        });

        view.setBtnEmergencyClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (droneControlService != null) {
                    droneControlService.triggerEmergency();
                }
            }

        });

        view.setBtnPhotoClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (droneControlService != null) {
                    onTakePhoto();
                }
            }
        });

        view.setBtnRecordClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onRecord();
            }
        });

        view.setBtnBackClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    public void doLeftFlip() {
        if (droneControlService != null)
            droneControlService.doLeftFlip();
    }

    @Override
    protected void onDestroy() {
        mController.destroy();

        if (view != null) {
            view.onDestroy();
        }

        soundPool.release();
        soundPool = null;

        unbindService(mConnection);

        super.onDestroy();
        Log.d(TAG, "ControlDroneActivity destroyed");
        System.gc();
    }

    private void registerReceivers() {
        // System wide receiver
        registerReceiver(wifiSignalReceiver, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));

        // Local receivers
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager
                .getInstance(getApplicationContext());
        localBroadcastMgr.registerReceiver(videoRecordingStateReceiver, new IntentFilter(
                DroneControlService.VIDEO_RECORDING_STATE_CHANGED_ACTION));
        localBroadcastMgr.registerReceiver(droneEmergencyReceiver, new IntentFilter(
                DroneControlService.DRONE_EMERGENCY_STATE_CHANGED_ACTION));
        localBroadcastMgr.registerReceiver(droneBatteryReceiver, new IntentFilter(
                DroneControlService.DRONE_BATTERY_CHANGED_ACTION));
        localBroadcastMgr.registerReceiver(droneFlyingStateReceiver, new IntentFilter(
                DroneControlService.DRONE_FLYING_STATE_CHANGED_ACTION));
        localBroadcastMgr.registerReceiver(droneCameraReadyChangedReceiver, new IntentFilter(
                DroneControlService.CAMERA_READY_CHANGED_ACTION));
        localBroadcastMgr.registerReceiver(droneRecordReadyChangeReceiver, new IntentFilter(
                DroneControlService.RECORD_READY_CHANGED_ACTION));
    }

    private void unregisterReceivers() {
        // Unregistering system receiver
        unregisterReceiver(wifiSignalReceiver);

        // Unregistering local receivers
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager
                .getInstance(getApplicationContext());
        localBroadcastMgr.unregisterReceiver(videoRecordingStateReceiver);
        localBroadcastMgr.unregisterReceiver(droneEmergencyReceiver);
        localBroadcastMgr.unregisterReceiver(droneBatteryReceiver);
        localBroadcastMgr.unregisterReceiver(droneFlyingStateReceiver);
        localBroadcastMgr.unregisterReceiver(droneCameraReadyChangedReceiver);
        localBroadcastMgr.unregisterReceiver(droneRecordReadyChangeReceiver);
    }

    @Override
    protected void onResume() {
        if (view != null) {
            view.onResume();
        }

        if (droneControlService != null) {
            droneControlService.resume();
        }

        registerReceivers();
        refreshWifiSignalStrength();

        // Start tracking device orientation
        mController.resume();
        DeviceOrientationManager orientationManager = mController.getDeviceOrientationManager();

        magnetoAvailable = orientationManager != null && orientationManager.isMagnetoAvailable();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (view != null) {
            view.onPause();
        }

        if (droneControlService != null) {
            droneControlService.pause();
        }

        unregisterReceivers();

        // Stop tracking device orientation
        mController.pause();

        stopEmergencySound();

        System.gc();
        super.onPause();
    }

    /**
     * Called when we connected to DroneControlService
     */
    protected void onDroneServiceConnected() {
        if (droneControlService != null) {
            droneControlService.resume();
            droneControlService.requestDroneStatus();
        }
        else {
            Log.w(TAG, "DroneServiceConnected event ignored as DroneControlService is null");
        }

        settingsDialog = new SettingsDialog(this, this, droneControlService, magnetoAvailable);

        applySettings(settings);

        initListeners();
        runTranscoding();

        if (droneControlService.getMediaDir() != null) {
            view.setRecordButtonEnabled(true);
            view.setCameraButtonEnabled(true);
        }
    }

    @Override
    public void onDroneFlyingStateChanged(boolean flying) {
        this.flying = flying;
        view.setIsFlying(flying);

        updateBackButtonState();

        if (!view.isInTouchMode())
            droneControlService.setProgressiveCommandEnabled(flying);
    }

    @Override
    @SuppressLint("NewApi")
    public void onDroneRecordReadyChanged(boolean ready) {
        if (!recording) {
            view.setRecordButtonEnabled(ready);
        }
        else {
            view.setRecordButtonEnabled(true);
        }
    }

    protected void onNotifyLowDiskSpace() {
        showWarningDialog(getString(R.string.your_device_is_low_on_disk_space),
                WARNING_MESSAGE_DISMISS_TIME);
    }

    protected void onNotifyLowUsbSpace() {
        showWarningDialog(getString(R.string.USB_drive_full_Please_connect_a_new_one),
                WARNING_MESSAGE_DISMISS_TIME);
    }

    protected void onNotifyNoMediaStorageAvailable() {
        showWarningDialog(getString(R.string.Please_insert_a_SD_card_in_your_Smartphone),
                WARNING_MESSAGE_DISMISS_TIME);
    }

    @Override
    public void onCameraReadyChanged(boolean ready) {
        view.setCameraButtonEnabled(ready);
        cameraReady = ready;

        updateBackButtonState();
    }

    @Override
    public void onDroneEmergencyChanged(int code) {
        view.setEmergency(code);

        if (code == NavData.ERROR_STATE_EMERGENCY_VBAT_LOW ||
                code == NavData.ERROR_STATE_ALERT_VBAT_LOW) {
            playEmergencySound();
        }
        else {
            stopEmergencySound();
        }

        controlLinkAvailable = (code != NavData.ERROR_STATE_NAVDATA_CONNECTION);

        if (!controlLinkAvailable) {
            view.setRecordButtonEnabled(false);
            view.setCameraButtonEnabled(false);
            view.setSwitchCameraButtonEnabled(false);
        }
        else {
            view.setSwitchCameraButtonEnabled(true);
            view.setRecordButtonEnabled(true);
            view.setCameraButtonEnabled(true);
        }

        updateBackButtonState();

        view.setEmergencyButtonEnabled(!NavData.isEmergency(code));
    }

    @Override
    public void onDroneBatteryChanged(int value) {
        view.setBatteryValue(value);
    }

    @Override
    public void onWifiSignalStrengthChanged(int strength) {
        view.setWifiValue(strength);
    }

    @Override
    public void onDroneRecordVideoStateChanged(boolean recording, boolean usbActive,
                                               int remaining) {
        if (droneControlService == null)
            return;

        boolean prevRecording = this.recording;
        this.recording = recording;

        view.setRecording(recording);
        view.setUsbIndicatorEnabled(usbActive);
        view.setUsbRemainingTime(remaining);

        updateBackButtonState();

        if (!recording) {
            if (prevRecording != recording && droneControlService != null
                    && droneControlService.getDroneVersion() == EDroneVersion.DRONE_1) {
                runTranscoding();
                showWarningDialog(
                        getString(R.string
                                .Your_video_is_being_processed_Please_do_not_close_application),
                        WARNING_MESSAGE_DISMISS_TIME);
            }
        }

        if (prevRecording != recording) {
            if (usbActive && droneControlService.getDroneConfig().isRecordOnUsb() &&
                    remaining == 0) {
                onNotifyLowUsbSpace();
            }
        }
    }

    protected void showSettingsDialog() {
        view.setSettingsButtonEnabled(false);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("settings");

        if (prev != null) {
            return;
        }

        ft.addToBackStack(null);

        settingsDialog.show(ft, "settings");

        if (pauseVideoWhenOnSettings) {
            view.onPause();
        }
    }

    @Override
    public void onBackPressed() {
        if (canGoBack()) {
            super.onBackPressed();
        }
    }

    private boolean canGoBack() {
        return !((flying || recording || !cameraReady) && controlLinkAvailable);
    }

    private void applySettings(ApplicationSettings settings) {
        applySettings(settings, false);
    }

    private void applySettings(ApplicationSettings settings, boolean skipJoypadConfig) {
        if (!skipJoypadConfig && mController != null) {
            mController.init();
        }
    }

    public EDroneVersion getDroneVersion() {
        if (droneControlService != null)
            droneControlService.getDroneVersion();

        return EDroneVersion.UNKNOWN;
    }

    public boolean isDroneFlying() {
        return flying;
    }

    public void setMagntoEnabled(boolean enable) {
        if (droneControlService != null)
            droneControlService.setMagnetoEnabled(enable);
    }

    public ApplicationSettings getSettings() {
        return ((FreeFlightApplication) getApplication()).getAppSettings();
    }

    public void refreshWifiSignalStrength() {
        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int signalStrength = WifiManager.calculateSignalLevel(
                manager.getConnectionInfo().getRssi(), 4);
        onWifiSignalStrengthChanged(signalStrength);
    }

    private void showWarningDialog(final String message, final int forTime) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag(message);

        if (prev != null) {
            return;
        }

        ft.addToBackStack(null);

        // Create and show the dialog.
        WarningDialog dialog = new WarningDialog();

        dialog.setMessage(message);
        dialog.setDismissAfter(forTime);
        dialog.show(ft, message);
    }

    private void playEmergencySound() {
        if (effectsStreamId != 0) {
            soundPool.stop(effectsStreamId);
            effectsStreamId = 0;
        }

        effectsStreamId = soundPool.play(batterySoundId, 1, 1, 1, -1, 1);
    }

    private void stopEmergencySound() {
        soundPool.stop(effectsStreamId);
        effectsStreamId = 0;
    }

    private void updateBackButtonState() {
        if (canGoBack()) {
            view.setBackButtonVisible(true);
        }
        else {
            view.setBackButtonVisible(false);
        }
    }

    private void runTranscoding() {
        if (droneControlService.getDroneVersion() == EDroneVersion.DRONE_1) {
            File mediaDir = droneControlService.getMediaDir();

            if (mediaDir != null) {
                Intent transcodeIntent = new Intent(this, TranscodingService.class);
                transcodeIntent.putExtra(TranscodingService.EXTRA_MEDIA_PATH, mediaDir.toString());
                startService(transcodeIntent);
            }
            else {
                Log.d(TAG, "Transcoding skipped SD card is missing.");
            }
        }
    }

    @Override
    public void prepareDialog(SettingsDialog dialog) {
        DeviceOrientationManager orientationManager = mController.getDeviceOrientationManager();
        boolean acceleroAvailable = orientationManager != null && orientationManager
                .isAcceleroAvailable();
        boolean magnetoAvailable = orientationManager != null && orientationManager
                .isMagnetoAvailable();

        dialog.setAcceleroAvailable(acceleroAvailable);
        dialog.setMagnetoAvailable(magnetoAvailable);

        dialog.setFlying(flying);
        dialog.setConnected(controlLinkAvailable);
        dialog.enableAvailableSettings();
    }

    @Override
    public void
    onOptionChangedApp(SettingsDialog dialog, EAppSettingProperty property, Object value) {
        if (value == null || property == null) {
            throw new IllegalArgumentException("Property can not be null");
        }

        switch (property) {
            case LEFT_HANDED_PROP:
            case MAGNETO_ENABLED_PROP:
            case CONTROL_MODE_PROP:
                if (mController != null)
                    mController.init();
                break;

            default:
                // Ignoring any other option change. They should be processed in onDismissed
                break;

        }
    }

    @Override
    public void onDismissed(SettingsDialog settingsDialog) {
        // pauseVideoWhenOnSettings option is not mandatory and is set depending to device in
        // config.xml.
        if (pauseVideoWhenOnSettings) {
            view.onResume();
        }

        AsyncTask<Integer, Integer, Boolean> loadPropTask = new AsyncTask<Integer, Integer,
                Boolean>() {
            @Override
            protected Boolean doInBackground(Integer... params) {
                // Skipping joypad configuration as it was already done in onPropertyChanged
                // We do this because on some devices joysticks re-initialization takes too
                // much time.
                applySettings(getSettings(), true);
                return Boolean.TRUE;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                view.setSettingsButtonEnabled(true);
            }
        };

        loadPropTask.execute();
    }

    private boolean isLowOnDiskSpace() {
        boolean lowOnSpace = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            DroneConfig config = droneControlService.getDroneConfig();
            if (!recording && !config.isRecordOnUsb()) {
                File mediaDir = droneControlService.getMediaDir();
                long freeSpace = 0;

                if (mediaDir != null) {
                    freeSpace = mediaDir.getUsableSpace();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
                        && freeSpace < LOW_DISK_SPACE_BYTES_LEFT) {
                    lowOnSpace = true;
                }
            }
        }
        else {
            // TODO: Provide alternative implementation. Probably using StatFs
        }

        return lowOnSpace;
    }

    public void onRecord() {
        if (droneControlService != null) {
            DroneConfig droneConfig = droneControlService.getDroneConfig();

            boolean sdCardMounted = droneControlService.isMediaStorageAvailable();
            boolean recordingToUsb = droneConfig.isRecordOnUsb() &&
                    droneControlService.isUSBInserted();

            if (recording) {
                // Allow to stop recording
                view.setRecordButtonEnabled(false);
                droneControlService.record();
            }
            else {
                // Start recording
                if (!sdCardMounted) {
                    if (recordingToUsb) {
                        view.setRecordButtonEnabled(false);
                        droneControlService.record();
                    }
                    else {
                        onNotifyNoMediaStorageAvailable();
                    }
                }
                else {
                    if (!recordingToUsb && isLowOnDiskSpace()) {
                        onNotifyLowDiskSpace();
                    }

                    view.setRecordButtonEnabled(false);
                    droneControlService.record();
                }
            }
        }
    }

    public void onTakePhoto() {
        if (droneControlService.isMediaStorageAvailable()) {
            view.setCameraButtonEnabled(false);
            droneControlService.takePhoto();
        }
        else {
            onNotifyNoMediaStorageAvailable();
        }
    }

}

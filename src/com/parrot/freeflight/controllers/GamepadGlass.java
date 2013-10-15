package com.parrot.freeflight.controllers;

import java.util.concurrent.atomic.AtomicBoolean;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.parrot.freeflight.activities.ControlDroneActivity;
import com.parrot.freeflight.drone.DroneConfig;
import com.parrot.freeflight.sensors.DeviceOrientationManager;
import com.parrot.freeflight.ui.hud.Sprite;

/**
 * @author fhuya
 * @date Oct 12, 2013
 */
public class GamepadGlass extends Controller {

    static final String TAG = GamepadGlass.class.getName();

    private final Gamepad mGamepad;
    private final GoogleGlass mGlass;
    private AtomicBoolean mIsGlassMode = new AtomicBoolean(false);

    GamepadGlass(final ControlDroneActivity droneControl) {
        super(droneControl);
        mGamepad = Controller.ControllerType.GAMEPAD.getImpl(droneControl);
        mGlass = Controller.ControllerType.GOOGLE_GLASS.getImpl(droneControl);
    }

    private boolean isGlassMode() {
        return mIsGlassMode.get();
    }

    private void setGlassMode(boolean glassMode) {
        mIsGlassMode.set(glassMode);
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#initImpl()
     */
    @Override
    protected boolean initImpl() {
        return mGamepad.init() && mGlass.init();
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#getSpritesImpl()
     */
    @Override
    protected Sprite[] getSpritesImpl() {
        Sprite[] gamepadSprites = mGamepad.getSprites();
        Sprite[] glassSprites = mGlass.getSprites();

        int gamepadSpritesCount = gamepadSprites.length;
        int glassSpritesCount = glassSprites.length;
        int totalSpritesCount = gamepadSpritesCount + glassSpritesCount;
        if ( totalSpritesCount == 0 )
            return NO_SPRITES;

        Sprite[] sprites = new Sprite[totalSpritesCount];
        System.arraycopy(gamepadSprites, 0, sprites, 0, gamepadSpritesCount);
        System.arraycopy(glassSprites, 0, sprites, gamepadSpritesCount, glassSpritesCount);
        return sprites;
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#getDeviceOrientationManagerImpl()
     */
    @Override
    protected DeviceOrientationManager getDeviceOrientationManagerImpl() {
        // Favor the glass controller device orientation manager
        DeviceOrientationManager dom = mGlass.getDeviceOrientationManager();
        if ( dom == null )
            dom = mGamepad.getDeviceOrientationManager();
        return dom;
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#onKeyDownImpl(int, android.view.KeyEvent)
     */
    @Override
    protected boolean onKeyDownImpl(int keyCode, KeyEvent event) {
        if ( event.getRepeatCount() == 0 ) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_9:
                    // Select button. toggle glass mode
                    final boolean glassMode = !isGlassMode();
                    if ( glassMode ) {
                        // Update the drone tilt for glass mode
                        mDroneControl.setDroneTilt(DroneConfig.TILT_MIN * 2);

                        // Enable progressive command
                        mDroneControl.setDroneProgressiveCommandEnabled(true);

                    }
                    else {
                        // Set the drone tilt to default
                        mDroneControl.setDroneTilt(DroneConfig.TILT_MAX / 2);

                        // Disable progressive command
                        mDroneControl.setDroneProgressiveCommandEnabled(false);
                    }

                    // Reset the drone roll
                    mDroneControl.setDroneRoll(0);

                    setGlassMode(glassMode);
                    return true;
            }
        }

        boolean gamepadResult = mGamepad.onKeyDown(keyCode, event);

        boolean isGlassMode = isGlassMode();
        if ( isGlassMode ) {
            mDroneControl.setDroneProgressiveCommandEnabled(true);
        }

        boolean glassResult = isGlassMode && mGlass.onKeyDown(keyCode, event);

        return gamepadResult || glassResult;
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#onKeyUpImpl(int, android.view.KeyEvent)
     */
    @Override
    protected boolean onKeyUpImpl(int keyCode, KeyEvent event) {
        boolean gamepadResult = mGamepad.onKeyUp(keyCode, event);

        boolean isGlassMode = isGlassMode();
        if ( isGlassMode ) {
            mDroneControl.setDroneProgressiveCommandEnabled(true);
        }

        boolean glassResult = isGlassMode && mGlass.onKeyUp(keyCode, event);

        return gamepadResult || glassResult;
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#onGenericMotionImpl(android.view.View, android.view.MotionEvent)
     */
    @Override
    protected boolean onGenericMotionImpl(View view, MotionEvent event) {
        boolean gamepadResult = mGamepad.onGenericMotion(view, event);

        boolean isGlassMode = isGlassMode();
        if ( isGlassMode ) {
            mDroneControl.setDroneProgressiveCommandEnabled(true);
        }

        boolean glassResult = isGlassMode && mGlass.onGenericMotion(view, event);

        return gamepadResult || glassResult;
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#onTouchImpl(android.view.View, android.view.MotionEvent)
     */
    @Override
    protected boolean onTouchImpl(View view, MotionEvent event) {
        boolean gamepadResult = mGamepad.onTouch(view, event);

        boolean isGlassMode = isGlassMode();
        if ( isGlassMode ) {
            mDroneControl.setDroneProgressiveCommandEnabled(true);
        }

        boolean glassResult = isGlassMode && mGlass.onGenericMotion(view, event);

        return gamepadResult || glassResult;
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#resumeImpl()
     */
    @Override
    protected void resumeImpl() {
        mGamepad.resume();
        mGlass.resume();
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#pauseImpl()
     */
    @Override
    protected void pauseImpl() {
        mGamepad.pause();
        mGlass.pause();
    }

    /* (non-Javadoc)
     * @see com.parrot.freeflight.controllers.Controller#destroyImpl()
     */
    @Override
    protected void destroyImpl() {
        mGamepad.destroy();
        mGlass.destroy();
    }
}
/**
 * @author Fredia Huya-Kouadio
 * @date Sep 14, 2013
 */
package com.ne0fhyklabs.freeflight.controllers;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnTouchListener;

import com.ne0fhyklabs.freeflight.activities.ControlDroneActivity;

public abstract class Controller implements KeyEvent.Callback, OnGenericMotionListener,
        OnTouchListener {

    public enum ControllerType {
        GAMEPAD {
            @Override
            public Gamepad getImpl(final ControlDroneActivity droneControl) {
                return new Gamepad(droneControl);
            }
        },
        GOOGLE_GLASS {
            @Override
            public GoogleGlass getImpl(final ControlDroneActivity droneControl) {
                return new GoogleGlass(droneControl);
            }
        },
        GAMEPAD_AND_GLASS {
            @Override
            public GamepadGlass getImpl(final ControlDroneActivity droneControl) {
                return new GamepadGlass(droneControl);
            }
        };

        public abstract Controller getImpl(final ControlDroneActivity droneControl);
    }

    private boolean mWasDestroyed;
    private boolean mIsActive;
    protected final ControlDroneActivity mDroneControl;

    Controller(final ControlDroneActivity droneControl) {
        mDroneControl = droneControl;
    }

    private void checkIfAlive() {
        if (mWasDestroyed) {
            throw new IllegalStateException("Can't reuse controller after it has been destroyed.");
        }
    }

    public final boolean isActive() {
        return mIsActive;
    }

    public final boolean init() {
        checkIfAlive();
        return initImpl();
    }

    public final void resume() {
        checkIfAlive();
        resumeImpl();
        mIsActive = true;
    }

    public final void pause() {
        checkIfAlive();
        pauseImpl();
        mIsActive = false;
    }

    public final void destroy() {
        checkIfAlive();

        destroyImpl();
        mWasDestroyed = true;
        mIsActive = false;
    }

    public final boolean onCreatePanelMenu(MenuInflater inflater, int featureId, Menu menu) {
        return onCreatePanelMenuImpl(inflater, featureId, menu);
    }

    public final boolean onPreparePanel(int featureId, View view, Menu menu){
        return onPreparePanelImpl(featureId, view, menu);
    }

    public final boolean onMenuItemSelected(int featureId, MenuItem item){
        return onMenuItemSelectedImpl(featureId, item);
    }

    @Override
    public final boolean onGenericMotion(View view, MotionEvent event) {
        checkIfAlive();
        return mIsActive && onGenericMotionImpl(view, event);
    }

    @Override
    public final boolean onKeyDown(int keyCode, KeyEvent event) {
        checkIfAlive();
        return mIsActive && onKeyDownImpl(keyCode, event);
    }

    @Override
    public final boolean onKeyUp(int keyCode, KeyEvent event) {
        checkIfAlive();
        return mIsActive && onKeyUpImpl(keyCode, event);
    }

    @Override
    public final boolean onKeyLongPress(int keyCode, KeyEvent event) {
        checkIfAlive();
        return mIsActive && onKeyLongPressImpl(keyCode, event);
    }

    @Override
    public final boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        checkIfAlive();
        return false;
    }

    @Override
    public final boolean onTouch(View view, MotionEvent event) {
        checkIfAlive();
        return mIsActive && onTouchImpl(view, event);
    }

    protected abstract boolean initImpl();

    protected boolean onCreatePanelMenuImpl(MenuInflater inflater, int featureId, Menu menu) {
        return false;
    }

    protected boolean onPreparePanelImpl(int featureId, View view, Menu menu){
        return false;
    }

    protected boolean onMenuItemSelectedImpl(int featureId, MenuItem item){
        return false;
    }

    protected abstract boolean onKeyDownImpl(int keyCode, KeyEvent event);

    protected boolean onKeyLongPressImpl(int keyCode, KeyEvent event) {
        return false;
    }

    protected abstract boolean onKeyUpImpl(int keyCode, KeyEvent event);

    protected abstract boolean onGenericMotionImpl(View view, MotionEvent event);

    protected abstract boolean onTouchImpl(View view, MotionEvent event);

    protected abstract void resumeImpl();

    protected abstract void pauseImpl();

    protected abstract void destroyImpl();
}
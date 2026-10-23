package com.racas.black;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import java.io.File;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

// Local v1.5.0: import Download/Black/BLACK.iso into the port's app-specific assets folder.
// Keep the Java/JNI class name; only the installable applicationId differs from upstream.
public class BlackActivity extends SDLActivity {


    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL3", "main"};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (View v : new View[] {mSurface, mLayout})
            if (v != null) v.setOnCapturedPointerListener(BlackActivity::onCapturedPointer);
        File own = getExternalFilesDir(null);
        new File(own != null ? own : getFilesDir(), "assets").mkdirs();
    }

    // A controller some phones' game services pass on as a virtual device (MIUI's took a DualShock 4 on a Redmi Note
    // 8 Pro): SDL sees no controller in it and drops its input. Its buttons and sticks go to the game as port 0's
    // here (native/input.cpp); a controller SDL has goes its own way as before.
    private static native void nativeLooseKey(int keyCode, boolean down);
    private static native void nativeLooseAxes(float lx, float ly, float rx, float ry, float lt, float rt, float hx,
                                               float hy);
    private static native void nativeLooseRelease();

    private static boolean loose(InputEvent e, int sources) {
        return !mBrokenLibraries && (e.getSource() & sources) != 0
                && !SDLControllerManager.isDeviceSDLJoystick(e.getDeviceId());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean pad = KeyEvent.isGamepadButton(e.getKeyCode()) || (e.getKeyCode() >= KeyEvent.KEYCODE_DPAD_UP
                && e.getKeyCode() <= KeyEvent.KEYCODE_DPAD_RIGHT);
        if (pad && loose(e, InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) {
            if (e.getRepeatCount() == 0) nativeLooseKey(e.getKeyCode(), e.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (loose(e, InputDevice.SOURCE_JOYSTICK) && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // the right stick on Z and RZ, the triggers on LTRIGGER and RTRIGGER or BRAKE and GAS, as Android has them
            nativeLooseAxes(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
                    e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ),
                    Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)),
                    Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)),
                    e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y));
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    // The mouse while the game holds it (SDL's relative mode, Android's pointer capture). SDL's own handler passes on
    // only moves with no button held and the wheel: clicks and the moves while firing were lost. Each sample of a
    // batch is a move of its own, as SDL's C side takes them.
    private static boolean onCapturedPointer(View view, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                for (int h = 0; h < e.getHistorySize(); h++)
                    onNativeMouse(0, MotionEvent.ACTION_MOVE, e.getHistoricalX(h), e.getHistoricalY(h), true);
                onNativeMouse(0, MotionEvent.ACTION_MOVE, e.getX(), e.getY(), true);
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                onNativeMouse(e.getButtonState(), MotionEvent.ACTION_DOWN, 0, 0, true);
                return true;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                onNativeMouse(e.getButtonState(), MotionEvent.ACTION_UP, 0, 0, true);
                return true;
            case MotionEvent.ACTION_SCROLL:
                onNativeMouse(0, MotionEvent.ACTION_SCROLL, e.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        e.getAxisValue(MotionEvent.AXIS_VSCROLL), false);
                return true;
            default:
                // the down and up a press brings with it: the button events above carry it
                return true;
        }
    }

    // Native menu calls this method through JNI. Local builds never fetch official updates.
    public void checkForUpdates() {
        runOnUiThread(() -> Toast.makeText(this,
                "Versão local privada: atualizações oficiais desativadas.", Toast.LENGTH_LONG).show());
    }

    // SDL's older immersive flags don't always come back after a dialog (MIUI on Android 11 kept the status bar):
    // the bars are hidden again each time the game has the focus, a swipe shows them for a moment
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
        else if (!mBrokenLibraries) nativeLooseRelease();  // its ups go elsewhere while the game is not in front
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT < 30) {
            // Android 10: the older immersive flags
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            return;
        }
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

}

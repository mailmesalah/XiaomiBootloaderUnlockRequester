package sely.phoenix.tapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Calendar;
import java.util.List;

public class UnlockAutomatorService extends AccessibilityService {

    private static final String TAG = "TapperDebug";
    private boolean isRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "=================================================");
        Log.i(TAG, "Accessibility Service Armed & Listening!");
        Log.i(TAG, "=================================================");

        new Thread(() -> {
            while (true) {
                checkTimeAndTriggerUniversal();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Clock thread error", e);
                }
            }
        }).start();
    }

    private void checkTimeAndTriggerUniversal() {
        SharedPreferences prefs = getSharedPreferences("TapperPrefs", Context.MODE_PRIVATE);
        int schYear = prefs.getInt("year", -1);
        int schMonth = prefs.getInt("month", -1);
        int schDay = prefs.getInt("day", -1);
        int schHour = prefs.getInt("hour", -1);
        int schMin = prefs.getInt("minute", -1);

        if (schYear == -1 || schHour == -1 || isRunning) return;

        Calendar now = Calendar.getInstance();
        boolean matchesDate = (now.get(Calendar.YEAR) == schYear)
                && (now.get(Calendar.MONTH) == schMonth)
                && (now.get(Calendar.DAY_OF_MONTH) == schDay);

        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMin = now.get(Calendar.MINUTE);
        int currentSec = now.get(Calendar.SECOND);

        Log.d(TAG, String.format("Tick -> Time: %02d:%02d:%02d | Target: %02d:%02d",
                currentHour, currentMin, currentSec, schHour, schMin));

        if (matchesDate) {
            int targetTriggerMin = (schMin == 0) ? 59 : schMin - 1;
            int targetTriggerHour = (schMin == 0) ? ((schHour == 0) ? 23 : schHour - 1) : schHour;

            if (currentHour == targetTriggerHour && currentMin == targetTriggerMin && currentSec >= 58) {
                isRunning = true;

                String targetText = prefs.getString("button_text", "Apply for unlocking permissions");
                int fallbackX = prefs.getInt("x_coord", 540);
                int fallbackY = prefs.getInt("y_coord", 1850);
                int durationSecs = prefs.getInt("duration_secs", 6);

                Log.i(TAG, "!!! TARGET REACHED !!! Shifting execution to UI Thread.");

                // CRITICAL FIX: Push the entire execution loop onto the main application context thread
                mainHandler.post(() -> startTappingSequenceUI(targetText, fallbackX, fallbackY, durationSecs));
            }
        }
    }

    private void startTappingSequenceUI(String buttonText, int targetX, int targetY, int seconds) {
        long endTime = System.currentTimeMillis() + (seconds * 1000L);

        // We use a safe handler loop execution structure instead of blocking the main thread with Thread.sleep
        Runnable tapRunnable = new Runnable() {
            int currentTaps = 0;

            @Override
            public void run() {
                if (System.currentTimeMillis() >= endTime) {
                    Log.w(TAG, "Sequence Complete! Total loop cycles run: " + currentTaps);

                    // Cool down sequence flags inside UI thread context safely
                    mainHandler.postDelayed(() -> {
                        isRunning = false;
                        Log.i(TAG, "Service ready for next run.");
                    }, 10000);
                    return;
                }

                boolean textClicked = false;
                AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                if (activeRoot != null) {
                    List<AccessibilityNodeInfo> targets = activeRoot.findAccessibilityNodeInfosByText(buttonText);
                    if (targets != null && !targets.isEmpty()) {
                        for (AccessibilityNodeInfo node : targets) {
                            if (node.isClickable()) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                textClicked = true;
                                currentTaps++;
                                Log.v(TAG, "Text tap executed successfully.");
                            }
                        }
                    }
                }

                if (!textClicked) {
                    triggerFallbackTapGestureUI(targetX, targetY);
                    currentTaps++;
                }

                // Loop layout runs every 50ms safely on main execution thread parameters
                mainHandler.postDelayed(this, 50);
            }
        };

        // Start the UI handler chain thread
        mainHandler.post(tapRunnable);
    }

    private void triggerFallbackTapGestureUI(float x, float y) {
        Path tapPath = new Path();
        tapPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 10)); // Restored stable 10ms hold

        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.v(TAG, "CALLBACK_SUCCESS: Tap accepted at (" + x + ", " + y + ")");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "CALLBACK_FAILURE: Android OS blocked the coordinate tap injection!");
            }
        }, mainHandler); // Hooked mainHandler directly into callback checker
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        isRunning = false;
    }
}

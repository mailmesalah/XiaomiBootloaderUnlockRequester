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
import java.util.Locale;

public class UnlockAutomatorService extends AccessibilityService {

    private static final String TAG = "TapperDebug";
    private boolean isRunning = false;
    private static final int DEFAULT_DURATION_SECS = 60;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "==========================================================================");
        Log.i(TAG, "🟢 SERVICE ARMED: Highly-Instrumented Tapper Automation Engine is Online!");
        Log.i(TAG, "==========================================================================");

        new Thread(() -> {
            while (true) {
                checkTimeAndTriggerUniversal();
                try {
                    Thread.sleep(100); // Polling frequency to match exact millisecond calculations
                } catch (InterruptedException e) {
                    Log.e(TAG, "❌ Background clock tracking thread interrupted", e);
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

        if (schYear == -1 || schHour == -1) {
            // Log throttled to avoid cluttering before scheduling is set up
            return;
        }

        if (isRunning) return;

        Calendar now = Calendar.getInstance();
        long currentMillis = now.getTimeInMillis();

        boolean matchesDate = (now.get(Calendar.YEAR) == schYear)
                && (now.get(Calendar.MONTH) == schMonth)
                && (now.get(Calendar.DAY_OF_MONTH) == schDay);

        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMin = now.get(Calendar.MINUTE);
        int currentSec = now.get(Calendar.SECOND);
        int currentMs = now.get(Calendar.MILLISECOND);

        if (matchesDate) {
            int targetTriggerMin = (schMin == 0) ? 59 : schMin - 1;
            int targetTriggerHour = (schMin == 0) ? ((schHour == 0) ? 23 : schHour - 1) : schHour;

            // Trigger comparison logic evaluation window check
            if (currentHour == targetTriggerHour && currentMin == targetTriggerMin && currentSec == 58) {

                // Clock Sync Correction: Delay execution 400ms to offset phone clock drift
                long targetStartMillis = currentMillis + (400 - (currentMillis % 1000));
                isRunning = true;

                String targetText = prefs.getString("button_text", "Apply for unlocking");
                String breakText = prefs.getString("break_text", "quota limit reached");
                int fallbackX = prefs.getInt("x_coord", 450);
                int fallbackY = prefs.getInt("y_coord", 2150);
                int durationSecs = prefs.getInt("duration_secs", DEFAULT_DURATION_SECS);

                /// Dynamically calculate the date strings based on user UI inputs
                // Your phone matches today's India calendar, but the server is already on Beijing Time (+1 day)
                Calendar serverCurrentCal = Calendar.getInstance();
                serverCurrentCal.set(schYear, schMonth, schDay);
                serverCurrentCal.add(Calendar.DAY_OF_MONTH, 1); // Shifting forward to match the server's active baseline day
                String lockBeforeDate = String.format(Locale.US, "%02d/%02d",
                        serverCurrentCal.get(Calendar.MONTH) + 1, serverCurrentCal.get(Calendar.DAY_OF_MONTH));

                // Calculate Day + 2 automatically (handles end of month/year roll smoothly)
                Calendar serverNextCal = Calendar.getInstance();
                serverNextCal.set(schYear, schMonth, schDay);
                serverNextCal.add(Calendar.DAY_OF_MONTH, 2); // Shifting forward to target tomorrow's locked gate pool
                String lockAfterDate = String.format(Locale.US, "%02d/%02d",
                        serverNextCal.get(Calendar.MONTH) + 1, serverNextCal.get(Calendar.DAY_OF_MONTH));

                long delayOffset = targetStartMillis - System.currentTimeMillis();
                if (delayOffset < 0) delayOffset = 0;

                Log.i(TAG, "⏰ ==================== TIMER RESET MATCHED ====================");
                Log.i(TAG, String.format("⏰ Phone Clock Time: %02d:%02d:%02d.%03d", currentHour, currentMin, currentSec, currentMs));
                Log.i(TAG, String.format("⏰ Target Lock Window Constraints: Before Reset = [%s] | After Reset Gate = [%s]", lockBeforeDate, lockAfterDate));
                Log.i(TAG, "⏰ Calculated Sync Offset Delay: " + delayOffset + "ms -> Intercepting target grid at 9:30:00.000 Server Time.");

                mainHandler.postDelayed(() -> {
                    Log.i(TAG, "🚀 SHIFT ENGINE: Sync delay finished. Initiating execution loop parameters on main UI thread context.");
                    startTappingSequenceUI(targetText, breakText, lockBeforeDate, lockAfterDate, fallbackX, fallbackY, durationSecs);
                }, delayOffset);
            }
        }
    }

    private void startTappingSequenceUI(String buttonText, String breakText, String lockBeforeDate,
                                        String lockAfterDate, int targetX, int targetY, int seconds) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (seconds * 1000L);
        Log.i(TAG, "🔥 LOOP INITIALIZED: Execution window active for " + seconds + " seconds.");

        Runnable tapRunnable = new Runnable() {
            int currentTaps = 0;

            @Override
            public void run() {
                long now = System.currentTimeMillis();
                if (now >= endTime) {
                    Log.w(TAG, "🛑 MACRO COMPLETE: Target time duration limit finished. Total fired iterations: " + currentTaps);
                    isRunning = false;
                    return;
                }

                AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                int currentDelay = 35; // Maximum overdrive loop frequency limit parameters

                if (activeRoot != null) {
                    CharSequence statusContent = findTextInNodeTree(activeRoot, lockBeforeDate, lockAfterDate, breakText);
                    if (statusContent != null) {
                        String currentText = statusContent.toString();
                        Log.d(TAG, "🔍 LAYOUT STATE SCANNER: Text node caught -> \"" + currentText + "\"");

                        // 1. DYNAMIC KILL SWITCH ENGAGED: Next calendar pool error or custom cancel token identified
                        if (currentText.contains(lockAfterDate) || currentText.toLowerCase().contains(breakText.toLowerCase())) {
                            Log.w(TAG, "🛑 KILL SWITCH: Server rollover identified! Found terminal text block token: \"" + currentText + "\"");
                            Log.w(TAG, "🛑 KILL SWITCH: Exiting click array sequence loop immediately to safeguard account status.");
                            isRunning = false;
                            return;
                        }

                        // 2. STALE DATE ACTIVE: Maintain rapid fire to bridge transition states
                        if (currentText.contains(lockBeforeDate)) {
                            Log.v(TAG, "⏳ PRE-RESET POOL: Current state reads old date constraint [" + lockBeforeDate + "]. Maintaining max throttle.");
                            currentDelay = 35;
                        }
                    } else {
                        Log.v(TAG, "✨ TRANSITION STATE: UI error component returned completely null/clean structure context.");
                    }
                } else {
                    Log.w(TAG, "⚠️ UI INACCESSIBLE: Active Window layout canvas hierarchy structural reference node returned null.");
                }

                // Execute UI tap interaction arrays
                boolean textClicked = false;
                if (activeRoot != null) {
                    List<AccessibilityNodeInfo> targets = activeRoot.findAccessibilityNodeInfosByText(buttonText);
                    if (targets != null && !targets.isEmpty()) {
                        Log.v(TAG, "🎯 NODE TARGETER: Text string match \"" + buttonText + "\" located inside window element array.");
                        for (AccessibilityNodeInfo node : targets) {
                            if (node.isClickable()) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                textClicked = true;
                                currentTaps++;
                                Log.i(TAG, "🎯 CLICK SUCCESS: Text Node action triggered. Total cycle count: " + currentTaps);
                            }
                        }
                    }
                }

                if (!textClicked) {
                    Log.v(TAG, "📱 FALLBACK TRIGGERED: Text view element unclickable or absent. Injecting input coordinates.");
                    triggerFallbackTapGestureUI(targetX, targetY);
                    currentTaps++;
                    Log.i(TAG, "📱 CLICK SUCCESS: Injected fallback gesture at (" + targetX + ", " + targetY + "). Total cycle count: " + currentTaps);
                }

                // Queue next execution loop utilizing calculated frame intervals
                mainHandler.postDelayed(this, currentDelay);
            }

            private CharSequence findTextInNodeTree(AccessibilityNodeInfo node, String m1, String m2, String m3) {
                if (node == null) return null;
                if (node.getText() != null) {
                    String text = node.getText().toString();
                    if (text.contains(m1) || text.contains(m2) || text.toLowerCase().contains(m3.toLowerCase())) {
                        return node.getText();
                    }
                }
                for (int i = 0; i < node.getChildCount(); i++) {
                    CharSequence childRes = findTextInNodeTree(node.getChild(i), m1, m2, m3);
                    if (childRes != null) return childRes;
                }
                return null;
            }
        };
        mainHandler.post(tapRunnable);
    }
    private void triggerFallbackTapGestureUI(float x, float y) {
        Path tapPath = new Path();
        tapPath.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 8));
        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.v(TAG, "✅ GESTURE INJECTOR: Touch hardware simulation successfully accepted at coordinates.");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "❌ GESTURE INJECTOR ERROR: Android OS internal system input validation frame cancelled execution!");
            }
        }, mainHandler);
    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
// Trace high-level UI focus tracking if necessary
    }
    @Override
    public void onInterrupt() {
        Log.w(TAG, "🛑 SYSTEM WARNING: Accessibility Service interrupted manually or system forced process execution crash.");
        isRunning = false;
    }
}

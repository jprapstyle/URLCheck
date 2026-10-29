package com.trianguloy.urlchecker;

import android.app.Instrumentation;
import android.os.Bundle;
import com.trianguloy.urlchecker.utilities.wrappers.BrowserPriorityChecks;

/** Runs without launching browsers or touching the installed alpha app preferences. */
public final class BrowserChecks extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            runOnMainSync(() -> {
                try { result.putString("stream", BrowserPriorityChecks.run(getTargetContext()) + "\n"); }
                catch (Throwable error) { result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error)); }
            });
            finish(result.getString("stream", "").startsWith("PASS:") ? -1 : 0, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(0, result);
        }
    }
}

package com.trianguloy.urlchecker;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;

import com.trianguloy.urlchecker.modules.companions.LogStore;
import com.trianguloy.urlchecker.modules.list.LogModule;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Device-side regression checks using a separate directory and preferences. */
public final class PerformanceChecks extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    private interface Call<T> { void run(LogStore.Result<T> callback); }
    private static <T> T await(Call<T> call) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Object[] values = new Object[2];
        call.run((value, error) -> { values[0] = value; values[1] = error; done.countDown(); });
        check(done.await(30, TimeUnit.SECONDS), "Worker timeout");
        if (values[1] != null) throw (Exception) values[1];
        return (T) values[0];
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        File directory = new File(getTargetContext().getCacheDir(), "log-regression");
        directory.mkdirs();
        Context isolated = new ContextWrapper(getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public String getPackageName() { return super.getPackageName() + ".log_regression"; }
            @Override public File getFileStreamPath(String name) { return new File(directory, name); }
        };
        try {
            GenericPref.getPrefs(isolated).edit().clear().commit();
            PerformanceChecks.<Void>await(callback -> LogStore.clear(isolated, callback));
            // Legacy migration: large UTF-8/UTF-16 content is preserved, while the preview is bounded.
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 150000; i++) builder.append("한국어😀 URL ").append(i).append('\n');
            String original = builder.toString();
            GenericPref.getPrefs(isolated).edit().putString(LogModule.PREF, original).commit();
            LogStore.Preview preview = await(callback -> LogStore.preview(isolated, callback));
            check(preview.truncated && preview.text.length() <= 65536, "Preview must be bounded");
            check(!preview.text.contains("\ufffd"), "UTF-8 tail boundary corrupted");
            check(!GenericPref.getPrefs(isolated).contains(LogModule.PREF), "Legacy preference remains");
            try (var reader = new InputStreamReader(new FileInputStream(isolated.getFileStreamPath(LogStore.FILE)), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int offset = 0, count;
                while ((count = reader.read(buffer)) != -1) {
                    for (int i = 0; i < count; i++) check(buffer[i] == original.charAt(offset + i), "Migration changed content");
                    offset += count;
                }
                check(offset == original.length(), "Migration lost content");
            }
            boolean rejected = false;
            try { PerformanceChecks.<Void>await(callback -> LogStore.replace(isolated, preview, "bad", callback)); }
            catch (Exception expected) { rejected = true; }
            check(rejected, "Truncated edits must be rejected");

            PerformanceChecks.<Void>await(callback -> LogStore.clear(isolated, callback));
            LogModule.LOG_LIMIT(isolated).set(3);
            for (int i = 0; i < 20; i++) LogStore.append(isolated, "기록" + i);
            LogStore.Preview recent = await(callback -> LogStore.preview(isolated, callback));
            check(recent.text.equals("기록17\n기록18\n기록19"), "Line retention/order failed: " + recent.text);
            LogStore.append(isolated, "changed");
            rejected = false;
            try { PerformanceChecks.<Void>await(callback -> LogStore.replace(isolated, recent, "bad", callback)); }
            catch (Exception expected) { rejected = true; }
            check(rejected, "Stale edits must be rejected");
            LogStore.Preview fresh = await(callback -> LogStore.preview(isolated, callback));
            PerformanceChecks.<Void>await(callback -> LogStore.replace(isolated, fresh, "edited", callback));
            LogStore.Preview edited = await(callback -> LogStore.preview(isolated, callback));
            check(edited.text.equals("edited"), "Small log editing failed");
            result.putString("stream", "PASS: large legacy migration, exact Unicode preservation, bounded preview, truncated edit rejection, ordered append, line retention, stale edit rejection, replacement.\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(0, result);
        } finally {
            GenericPref.getPrefs(isolated).edit().clear().commit();
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
}

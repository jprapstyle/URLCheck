package com.trianguloy.urlchecker;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import com.trianguloy.urlchecker.utilities.OrientationSettings;

/** Applies the saved policy to every screen, including screens returning from settings. */
public final class URLCheckApplication extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() { super.onCreate(); registerActivityLifecycleCallbacks(this); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { OrientationSettings.apply(activity); }
    @Override public void onActivityResumed(Activity activity) { OrientationSettings.apply(activity); }
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}

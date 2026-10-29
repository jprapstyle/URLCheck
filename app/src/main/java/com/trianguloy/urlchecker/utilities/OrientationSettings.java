package com.trianguloy.urlchecker.utilities;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.utilities.Enums;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;

/** App-wide orientation policy; platform large-screen policies may override requests. */
public final class OrientationSettings {
    private OrientationSettings() {}
    public static GenericPref.EnumerationPref<Mode> preference(Context context) {
        return new GenericPref.EnumerationPref<>("screen_orientation", Mode.PORTRAIT, Mode.class, context);
    }
    public static void apply(Activity activity) {
        int requested = preference(activity).get().orientation;
        if (activity.getRequestedOrientation() != requested) activity.setRequestedOrientation(requested);
    }
    public enum Mode implements Enums.IdEnum, Enums.StringEnum {
        SYSTEM(0, ActivityInfo.SCREEN_ORIENTATION_USER, R.string.orientation_system),
        PORTRAIT(1, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, R.string.orientation_portrait),
        LANDSCAPE(2, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, R.string.orientation_landscape),
        AUTOMATIC(3, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, R.string.orientation_automatic);
        private final int id;
        public final int orientation;
        private final int label;
        Mode(int id, int orientation, int label) { this.id = id; this.orientation = orientation; this.label = label; }
        @Override public int getId() { return id; }
        @Override public int getStringResource() { return label; }
    }
}

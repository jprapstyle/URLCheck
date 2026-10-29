package com.trianguloy.urlchecker.modules.companions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.modules.list.OpenModule;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;
import com.trianguloy.urlchecker.utilities.wrappers.IntentApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Fixed-browser fallback with existing learned app preferences taking precedence. Never launches an app. */
public final class BrowserPreference {
    private BrowserPreference() {}

    public static GenericPref.BoolPref enabled(Context context) {
        return new GenericPref.BoolPref("open_fixedBrowserEnabled", false, context);
    }

    public static GenericPref.StringPref componentPref(Context context) {
        return new GenericPref.StringPref("open_fixedBrowser", "", context);
    }

    public static ComponentName component(Context context) {
        return ComponentName.unflattenFromString(componentPref(context).get());
    }

    public static void select(Context context, ComponentName component) {
        componentPref(context).set(component.flattenToString());
        enabled(context).set(true);
    }

    public static void clear(Context context) {
        enabled(context).set(false);
        componentPref(context).clear();
    }

    public static boolean isWebUrl(String url) {
        String scheme = Uri.parse(url).getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    /** Generic web handlers, including browser chooser apps, with the same visible-app rules. */
    public static List<IntentApp> browsers(Context context) {
        var candidates = new LinkedHashMap<ComponentName, IntentApp>();
        for (String scheme : new String[]{"https", "http"}) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://urlcheck.invalid/"));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            for (IntentApp app : IntentApp.getOtherPackages(intent, context)) {
                candidates.put(app.getComponent(), app);
            }
        }
        return new ArrayList<>(candidates.values());
    }

    public static void apply(List<IntentApp> apps, String url, Context context) {
        ComponentName selected = component(context);
        if (enabled(context).get() && selected != null && isWebUrl(url)) {
            Set<ComponentName> generic = new HashSet<>();
            for (IntentApp app : browsers(context)) generic.add(app.getComponent());
            prioritize(apps, selected, generic, url);
        }
        // The original pairwise scores are authoritative. A stable sort retains the
        // dedicated-app/fixed-browser fallback when a pair has no learned preference.
        // Reuse the existing keys and per-domain option, including pre-update history.
        new LastOpened(context).sort(apps, url);
    }

    /** Stable sorting preserves the existing preference order within each priority group. */
    public static void prioritize(List<IntentApp> apps, ComponentName selected,
                                  Set<ComponentName> generic, String url) {
        if (selected == null || !isWebUrl(url)) return;
        Collections.sort(apps, (a, b) -> Integer.compare(rank(a, selected, generic), rank(b, selected, generic)));
    }

    private static int rank(IntentApp app, ComponentName selected, Set<ComponentName> generic) {
        if (!generic.contains(app.getComponent()) && app.isUrlSpecificHandler()) return 0;
        return selected.equals(app.getComponent()) ? 1 : 2;
    }

    public static CharSequence selectedLabel(Context context) {
        ComponentName selected = component(context);
        if (selected == null) return context.getString(R.string.mOpen_fixedBrowserNone);
        try {
            return context.getPackageManager().getActivityInfo(selected, 0)
                    .loadLabel(context.getPackageManager());
        } catch (PackageManager.NameNotFoundException | SecurityException e) {
            return context.getString(R.string.mOpen_fixedBrowserUnavailable, selected.getPackageName());
        }
    }

    /** Choosing a browser only changes the preference; it leaves the URL popup open. */
    public static void showPicker(Activity activity, Runnable onChanged) {
        var apps = browsers(activity);
        Set<String> hidden = OpenModule.getHiddenPackages(activity);
        com.trianguloy.urlchecker.utilities.methods.JavaUtils.removeIf(apps, app -> hidden.contains(app.getPackage()));
        Collections.sort(apps, (a, b) -> a.getLabel(activity).toString()
                .compareToIgnoreCase(b.getLabel(activity).toString()));
        if (apps.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle(R.string.mOpen_fixedBrowserTitle)
                    .setMessage(R.string.mOpen_fixedBrowserNoApps)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        CharSequence[] labels = new CharSequence[apps.size()];
        int checked = -1;
        for (int i = 0; i < apps.size(); i++) {
            IntentApp app = apps.get(i);
            labels[i] = app.getLabel(activity) + "\n(" + app.getPackage() + ")";
            if (app.getComponent().equals(component(activity))) checked = i;
        }
        new AlertDialog.Builder(activity).setTitle(R.string.mOpen_fixedBrowserTitle)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    select(activity, apps.get(which).getComponent());
                    dialog.dismiss();
                    if (onChanged != null) onChanged.run();
                })
                .setNeutralButton(R.string.mOpen_fixedBrowserClear, (dialog, which) -> {
                    clear(activity);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}

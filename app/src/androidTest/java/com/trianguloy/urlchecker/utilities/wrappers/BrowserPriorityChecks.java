package com.trianguloy.urlchecker.utilities.wrappers;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.Switch;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.modules.companions.BrowserPreference;
import com.trianguloy.urlchecker.modules.companions.LastOpened;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Offline priority checks using synthetic Android resolution results and separate preferences. */
public final class BrowserPriorityChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static IntentApp app(String name, int match) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "checks." + name;
        info.activityInfo.name = "Main";
        info.match = match;
        return new IntentApp(info);
    }

    private static ArrayList<IntentApp> order(IntentApp... apps) {
        return new ArrayList<>(Arrays.asList(apps));
    }

    public static String run(Context context) {
        assertions = 0;
        Context isolated = new ContextWrapper(context) {
            @Override public String getPackageName() { return super.getPackageName() + ".browser_checks"; }
        };
        GenericPref.getPrefs(isolated).edit().clear().commit();
        try {
            IntentApp other = app("other", IntentFilter.MATCH_CATEGORY_SCHEME);
            IntentApp pinned = app("pinned", IntentFilter.MATCH_CATEGORY_SCHEME);
            IntentApp dedicated = app("dedicated", IntentFilter.MATCH_CATEGORY_HOST);
            IntentApp second = app("second", IntentFilter.MATCH_CATEGORY_PATH);
            var generic = new HashSet<>(Arrays.asList(other.getComponent(), pinned.getComponent()));

            check(!BrowserPreference.enabled(isolated).get(), "Feature must default to off");
            check(BrowserPreference.component(isolated) == null, "No implicit browser selection");
            var apps = order(other, dedicated, pinned);
            BrowserPreference.apply(apps, "https://example.com/", isolated);
            check(apps.equals(order(other, dedicated, pinned)), "Disabled feature changed old ordering");

            BrowserPreference.select(isolated, pinned.getComponent());
            check(BrowserPreference.enabled(isolated).get(), "Choosing must enable the feature");
            check(pinned.getComponent().equals(BrowserPreference.component(isolated)), "Component not persisted");
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/");
            check(apps.equals(order(dedicated, pinned, other)), "Dedicated app must precede fixed browser");

            apps = order(other, pinned);
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "HTTP://example.com/");
            check(apps.equals(order(pinned, other)), "Fixed browser lost to recent browser");

            apps = order(other, second, dedicated, pinned);
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/");
            check(apps.equals(order(second, dedicated, pinned, other)), "Order within specific handlers was not stable");

            apps = order(other, dedicated);
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/");
            check(apps.equals(order(dedicated, other)), "Missing/hidden/rejected browser must never be re-added");
            apps = order(other);
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/");
            check(apps.equals(order(other)), "Missing fixed browser prevented fallback");

            apps = order(other, dedicated, pinned);
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "mailto:someone@example.com");
            check(apps.equals(order(other, dedicated, pinned)), "Non-web intent was reordered");
            BrowserPreference.prioritize(apps, null, generic, "https://example.com/");
            check(apps.equals(order(other, dedicated, pinned)), "Null browser was not a no-op");

            apps.clear();
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/");
            check(apps.isEmpty(), "Empty candidate list changed");

            // A browser can also advertise domain-specific filters; it must not outrank the pin.
            apps = order(dedicated, pinned);
            generic.add(dedicated.getComponent());
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/");
            check(apps.equals(order(pinned, dedicated)), "Generic browser misclassified as a dedicated app");

            IntentApp media = app("media", IntentFilter.MATCH_CATEGORY_TYPE);
            apps = order(pinned, media);
            BrowserPreference.prioritize(apps, pinned.getComponent(), generic, "https://example.com/video.mp4");
            check(apps.equals(order(media, pinned)), "MIME-specific handler must precede browser");

            BrowserPreference.enabled(isolated).set(false);
            check(pinned.getComponent().equals(BrowserPreference.component(isolated)), "Toggle must retain the selected browser");
            BrowserPreference.clear(isolated);
            check(!BrowserPreference.enabled(isolated).get() && BrowserPreference.component(isolated) == null,
                    "Clear must remove selection and disable the feature");
            BrowserPreference.componentPref(isolated).set("invalid-component");
            check(BrowserPreference.component(isolated) == null, "Invalid restored preference must be safe");
            BrowserPreference.componentPref(isolated).set("checks.missing/Removed");
            check(BrowserPreference.selectedLabel(isolated).toString().contains("checks.missing"),
                    "Removed browser must have a readable fallback");

            for (String language : new String[]{"en", "ko"}) {
                Configuration config = new Configuration(context.getResources().getConfiguration());
                config.setLocale(new Locale(language));
                Context localized = context.createConfigurationContext(config);
                String expected = language.equals("ko") ? "고정 브라우저" : "Fixed browser";
                check(expected.equals(localized.getString(R.string.mOpen_fixedBrowserTitle)), "Missing translation: " + language);
                Context themed = new ContextThemeWrapper(localized, android.R.style.Theme_DeviceDefault);
                var layout = LayoutInflater.from(themed).inflate(R.layout.config_open, null);
                check(layout.findViewById(R.id.fixed_browser_enabled) instanceof Switch, "Missing settings switch");
                check(layout.findViewById(R.id.fixed_browser_choose) != null, "Missing browser picker setting");
                check(localized.getString(R.string.mOpen_fixedBrowserDesc).length() > 20, "Missing localized explanation");
            }
            checkLearnedPreferences(context);
            return "PASS: " + assertions + " browser preference checks (priority, learned history, domain scope, fallback, persistence, EN/KO settings).";
        } finally {
            GenericPref.getPrefs(isolated).edit().clear().commit();
        }
    }
    private static void checkLearnedPreferences(Context context) {
        Context isolated = new ContextWrapper(context) {
            @Override public String getPackageName() { return super.getPackageName() + ".browser_history_checks"; }
        };
        var prefs = GenericPref.getPrefs(isolated);
        prefs.edit().clear().commit();
        try {
            IntentApp chosenBrowser = app("choosebrowser", IntentFilter.MATCH_CATEGORY_SCHEME);
            IntentApp pinned = app("fixed", IntentFilter.MATCH_CATEGORY_SCHEME);
            IntentApp coupang = app("coupang", IntentFilter.MATCH_CATEGORY_HOST);
            String url = "https://www.coupang.com/item/1";
            BrowserPreference.select(isolated, pinned.getComponent());
            var history = new LastOpened(isolated);
            var apps = order(chosenBrowser, pinned, coupang);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.equals(order(coupang, pinned, chosenBrowser)), "No-history fallback must prefer dedicated app then fixed browser");

            // Reproduce the user's example with the same preference API used by OpenDialog.
            history.prefer(chosenBrowser.getComponent(), apps, url);
            apps = order(coupang, pinned, chosenBrowser);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.get(0) == chosenBrowser, "A learned browser must override a dedicated app with the feature enabled");
            check(BrowserPreference.enabled(isolated).get(), "Learning must not disable the fixed browser");
            check(BrowserPreference.component(isolated).equals(pinned.getComponent()), "Learning must not replace the fixed-browser setting");
            apps = order(pinned, coupang, chosenBrowser);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.get(0) == chosenBrowser, "Learned preference must survive another popup");

            apps = order(pinned, coupang, chosenBrowser);
            BrowserPreference.apply(apps, "https://another.example/item", isolated);
            check(apps.get(0) == chosenBrowser, "Default global history must keep its original scope");

            LastOpened.PERDOMAIN_PREF(isolated).set(true);
            apps = order(chosenBrowser, pinned, coupang);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.get(0) == coupang, "Global history must not leak into domain-specific history");
            history.prefer(chosenBrowser.getComponent(), apps, url);
            apps = order(coupang, pinned, chosenBrowser);
            BrowserPreference.apply(apps, "https://m.coupang.com/item/2", isolated);
            check(apps.get(0) == chosenBrowser, "Existing subdomain grouping must be preserved");
            apps = order(chosenBrowser, pinned, coupang);
            BrowserPreference.apply(apps, "https://coupa.ng/short", isolated);
            check(apps.get(0) == coupang, "Unlearned domains must retain the fallback order");

            BrowserPreference.enabled(isolated).set(false);
            apps = order(coupang, pinned, chosenBrowser);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.get(0) == chosenBrowser, "Turning off the feature must retain original history sorting");
            BrowserPreference.enabled(isolated).set(true);
            apps = order(coupang, pinned); // remembered choice is hidden/rejected/uninstalled
            BrowserPreference.apply(apps, url, isolated);
            check(apps.equals(order(coupang, pinned)), "Learning must never re-add an excluded browser");

            prefs.edit().clear().commit();
            BrowserPreference.select(isolated, pinned.getComponent());
            apps = order(chosenBrowser, pinned, coupang);
            for (int i = 0; i < 5; i++) history.prefer(coupang.getComponent(), apps, url);
            history.prefer(chosenBrowser.getComponent(), apps, url);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.get(0) == coupang, "Original accumulated scores must not become last-click-wins");
            for (int i = 0; i < 3; i++) history.prefer(chosenBrowser.getComponent(), apps, url);
            BrowserPreference.apply(apps, url, isolated);
            check(apps.get(0) == chosenBrowser, "Repeated choices must reverse the original accumulated preference");
        } finally {
            prefs.edit().clear().commit();
        }
    }

}

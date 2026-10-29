package com.trianguloy.urlchecker.modules.list;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.activities.ModulesActivity;
import com.trianguloy.urlchecker.dialogs.MainDialog;
import com.trianguloy.urlchecker.modules.AModuleConfig;
import com.trianguloy.urlchecker.modules.AModuleData;
import com.trianguloy.urlchecker.modules.AModuleDialog;
import com.trianguloy.urlchecker.modules.AutomationRules;
import com.trianguloy.urlchecker.modules.companions.CTabs;
import com.trianguloy.urlchecker.modules.companions.BrowserPreference;
import android.widget.Switch;
import com.trianguloy.urlchecker.modules.companions.Flags;
import com.trianguloy.urlchecker.modules.companions.Incognito;
import com.trianguloy.urlchecker.modules.companions.LastOpened;
import com.trianguloy.urlchecker.modules.companions.ShareUtility;
import com.trianguloy.urlchecker.modules.companions.Size;
import com.trianguloy.urlchecker.url.UrlData;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.BoolPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.EnumerationPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.ListStringPref;
import com.trianguloy.urlchecker.utilities.methods.AndroidUtils;
import com.trianguloy.urlchecker.utilities.methods.JavaUtils;
import com.trianguloy.urlchecker.utilities.methods.PackageUtils;
import com.trianguloy.urlchecker.utilities.wrappers.IntentApp;
import com.trianguloy.urlchecker.utilities.wrappers.RejectionDetector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** This module contains an open and share buttons */
public class OpenModule extends AModuleData {

    public static BoolPref CLOSEOPEN_PREF(Context cntx) {
        return new BoolPref("open_closeopen", true, cntx);
    }

    public static BoolPref NOREFERRER_PREF(Context cntx) {
        return new BoolPref("open_noReferrer", false, cntx);
    }

    public static BoolPref REJECTED_PREF(Context cntx) {
        return new BoolPref("open_rejected", true, cntx);
    }

    public static EnumerationPref<Size> ICONSIZE_PREF(Context cntx) {
        return new EnumerationPref<>("open_iconsize", Size.NORMAL, Size.class, cntx);
    }

    public static ListStringPref HIDDEN_PREF(Context cntx) {
        return new ListStringPref("open_hidden", "\n", List.of(), cntx);
    }

    /** Returns the packages that should not be shown in the open list */
    public static Set<String> getHiddenPackages(Context cntx) {
        var hidden = new HashSet<>(HIDDEN_PREF(cntx).get());
        hidden.remove("");
        return hidden;
    }

    /**
     * Shows a dialog to choose which of [packages] are hidden.
     * Hidden packages not present in [packages] are kept as they are.
     */
    static void showHiddenDialog(Activity activity, Collection<String> packages, Runnable onChanged) {
        var pm = activity.getPackageManager();
        var hidden = getHiddenPackages(activity);

        // candidates (current hidden ones included), sorted by label
        var candidates = new ArrayList<>(new LinkedHashSet<>(packages));
        var labels = new HashMap<String, String>();
        for (var pkg : candidates) {
            String label;
            try {
                label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
            } catch (Exception e) {
                // not installed anymore
                label = pkg;
            }
            labels.put(pkg, label);
        }
        java.util.Collections.sort(candidates, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));

        var items = new CharSequence[candidates.size()];
        var checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            var pkg = candidates.get(i);
            items[i] = labels.get(pkg).equals(pkg) ? pkg : labels.get(pkg) + "\n(" + pkg + ")";
            checked[i] = hidden.contains(pkg);
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.mOpen_hidden)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // keep hidden packages not shown, replace the shown ones
                    hidden.removeAll(candidates);
                    for (int i = 0; i < candidates.size(); i++) {
                        if (checked[i]) hidden.add(candidates.get(i));
                    }
                    HIDDEN_PREF(activity).set(new ArrayList<>(hidden));
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public String getId() {
        return "open";
    }

    @Override
    public int getName() {
        return R.string.mOpen_name;
    }

    @Override
    public AModuleDialog getDialog(MainDialog cntx) {
        return new OpenDialog(cntx);
    }

    @Override
    public AModuleConfig getConfig(ModulesActivity cntx) {
        return new OpenConfig(cntx);
    }

    @Override
    public List<AutomationRules.Automation<AModuleDialog>> getAutomations() {
        return (List<AutomationRules.Automation<AModuleDialog>>) (List<?>) OpenDialog.AUTOMATIONS;
    }
}

class OpenDialog extends AModuleDialog {

    static final List<AutomationRules.Automation<OpenDialog>> AUTOMATIONS = List.of(
            new AutomationRules.Automation<>("open", R.string.auto_open, (dialog, args) -> {
                // open with component and/or package, or the first app if none present
                var component = args.optString("component");
                var packageName = args.optString("package");
                if (component.isEmpty() && packageName.isEmpty()) dialog.openUrl(0);
                else dialog.openUrl(
                        component.isEmpty() ? null : ComponentName.unflattenFromString(component),
                        packageName.isEmpty() ? null : packageName);
            }),
            new AutomationRules.Automation<>("share", R.string.auto_share, dialog ->
                    dialog.shareUtility.shareUrl()),
            new AutomationRules.Automation<>("copy", R.string.auto_copy, dialog ->
                    dialog.shareUtility.copyUrl()),
            new AutomationRules.Automation<>("ctabs", R.string.auto_ctabs, dialog ->
                    dialog.cTabs.setState(true)),
            new AutomationRules.Automation<>("incognito", R.string.auto_incognito, dialog ->
                    dialog.incognito.setState(true)),
            new AutomationRules.Automation<>("close", R.string.auto_close, dialog -> dialog.getActivity().finish())
    );

    private final BoolPref closeOpenPref;
    private final BoolPref noReferrerPref;
    private final BoolPref rejectedPref;
    private final EnumerationPref<Size> iconSizePref;

    private final LastOpened lastOpened;
    private final CTabs cTabs;
    private final Incognito incognito;
    private final RejectionDetector rejectionDetector;
    private final ShareUtility.Dialog shareUtility;

    private List<IntentApp> intentApps;
    private Button btn_open;
    private ImageButton btn_openWith;
    private View openParent;
    private Menu menu;
    private PopupMenu popup;

    public OpenDialog(MainDialog dialog) {
        super(dialog);
        lastOpened = new LastOpened(dialog);
        cTabs = new CTabs(dialog);
        incognito = new Incognito(dialog);
        rejectionDetector = new RejectionDetector(dialog);
        shareUtility = new ShareUtility.Dialog(dialog);
        closeOpenPref = OpenModule.CLOSEOPEN_PREF(dialog);
        noReferrerPref = OpenModule.NOREFERRER_PREF(dialog);
        rejectedPref = OpenModule.REJECTED_PREF(dialog);
        iconSizePref = OpenModule.ICONSIZE_PREF(dialog);
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_open;
    }

    @Override
    public void onInitialize(View views) {
        var intent = getActivity().getIntent();

        // ctabs
        cTabs.initFrom(intent, views.findViewById(R.id.ctabs));

        // incognito
        incognito.initFrom(intent, views.findViewById(R.id.mode_incognito));

        // init open
        openParent = views.findViewById(R.id.open_parent);
        btn_open = views.findViewById(R.id.open);
        btn_open.setOnClickListener(v -> openUrl(0));
        btn_open.setOnLongClickListener(v -> showBrowserActions());

        // init openWith
        btn_openWith = views.findViewById(R.id.open_with);
        btn_openWith.setOnClickListener(v -> showList());
        btn_openWith.setOnLongClickListener(v -> showBrowserActions());

        // init openWith popup
        popup = new PopupMenu(getActivity(), btn_open);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == -1) {
                BrowserPreference.showPicker(getActivity(), () -> updateSpinner(getUrl()));
                return true;
            }
            openUrl(item.getItemId());
            return false;
        });
        menu = popup.getMenu();

        // share
        shareUtility.onInitialize(views);
    }

    @Override
    public void onDisplayUrl(UrlData urlData) {
        updateSpinner(urlData.url);
    }

    // ------------------- Spinner -------------------

    /** Populates the spinner with the apps that can open it, in preference order */
    private void updateSpinner(String url) {
        intentApps = IntentApp.getOtherPackages(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), getActivity());

        // remove referrer
        if (noReferrerPref.get()) {
            var referrer = AndroidUtils.getReferrer(getActivity());
            JavaUtils.removeIf(intentApps, ri -> Objects.equals(ri.getPackage(), referrer));
        }

        // remove hidden
        var hidden = OpenModule.getHiddenPackages(getActivity());
        JavaUtils.removeIf(intentApps, ri -> hidden.contains(ri.getPackage()));

        // remove rejected if desired (and is not a non-view action, like share)
        // note: this will be called each time, so a rejected package will not be rejected again if the user changes the url and goes back. This is expected
        if (rejectedPref.get() && Intent.ACTION_VIEW.equals(getActivity().getIntent().getAction())) {
            var rejected = rejectionDetector.getPrevious(url);
            JavaUtils.removeIf(intentApps, ri -> Objects.equals(ri.getComponent(), rejected));
        }

        // check no apps
        if (intentApps.isEmpty()) {
            btn_open.setText(R.string.mOpen_noapps);
            btn_open.setCompoundDrawables(null, null, null, null);
            AndroidUtils.setEnabled(openParent, false);
            btn_open.setEnabled(false);
            btn_openWith.setVisibility(View.GONE);
            return;
        }

        // sort
        BrowserPreference.apply(intentApps, url, getActivity());

        // set
        var label = intentApps.get(0).getLabel(getActivity());
//        label = getActivity().getString(R.string.mOpen_with, label);
        btn_open.setText(label);
        btn_open.setCompoundDrawables(intentApps.get(0).getIcon(getActivity(), iconSizePref.get()), null, null, null);
        AndroidUtils.setEnabled(openParent, true);
        btn_open.setEnabled(true);
        // Resolve labels and build the secondary menu only when it is opened.
        btn_openWith.setVisibility(View.VISIBLE);

    }

    /** Long press exposes the current browser without opening the URL. */
    private boolean showBrowserActions() {
        var actions = new PopupMenu(getActivity(), btn_open);
        IntentApp selected = intentApps == null || intentApps.isEmpty() ? null : intentApps.get(0);
        boolean canPin = false;
        if (selected != null && BrowserPreference.isWebUrl(getUrl())) {
            for (var browser : BrowserPreference.browsers(getActivity())) {
                if (browser.getComponent().equals(selected.getComponent())) { canPin = true; break; }
            }
        }
        if (canPin) {
            actions.getMenu().add(0, 0, 0, getActivity().getString(
                    R.string.mOpen_fixedBrowserPinCurrent, selected.getLabel(getActivity())));
        }
        actions.getMenu().add(0, 1, 1, R.string.mOpen_fixedBrowserTitle);
        if (BrowserPreference.component(getActivity()) != null)
            actions.getMenu().add(0, 2, 2, R.string.mOpen_fixedBrowserClear);
        actions.getMenu().add(0, 3, 3, R.string.mOpen_hidden);
        actions.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0 -> { BrowserPreference.select(getActivity(), selected.getComponent()); updateSpinner(getUrl()); }
                case 1 -> BrowserPreference.showPicker(getActivity(), () -> updateSpinner(getUrl()));
                case 2 -> { BrowserPreference.clear(getActivity()); updateSpinner(getUrl()); }
                case 3 -> showHiddenDialog();
                default -> { return false; }
            }
            return true;
        });
        actions.show();
        return true;
    }

    // ------------------- Buttons -------------------

    /** Opens the url in a specific app provided by the index on the list of available ones */
    private void openUrl(int index) {
        // get
        if (index < 0 || index >= intentApps.size()) return;
        var chosenComponent = intentApps.get(index).getComponent();

        // update as preferred over the rest
        lastOpened.prefer(chosenComponent, intentApps, getUrl());

        openUrl(chosenComponent, null);
    }

    /** Opens the url in a specific app given by component and/or package */
    private void openUrl(ComponentName component, String packageName) {

        // open
        var intent = new Intent(getActivity().getIntent());
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // preserve original VIEW intent
            intent.setData(Uri.parse(getUrl()));
        } else {
            // replace with new VIEW intent
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getUrl()));
        }
        intent.setComponent(component);
        intent.setPackage(packageName);

        // ctabs
        cTabs.apply(intent);

        // incognito
        incognito.apply(intent);

        // apply flags from global data (probably set by flags module, if active) or by default
        Flags.applyGlobalFlags(intent, this);

        // rejection detector: mark as open
        if (component != null) rejectionDetector.markAsOpen(getUrl(), component);

        // open
        PackageUtils.startActivity(intent, R.string.toast_noApp, getActivity());

        // finish activity
        if (closeOpenPref.get()) {
            this.getActivity().finish();
        }
    }

    /** Shows the dialog to hide apps that can open the current url */
    private boolean showHiddenDialog() {
        var packages = new ArrayList<String>();
        for (var app : IntentApp.getOtherPackages(new Intent(Intent.ACTION_VIEW, Uri.parse(getUrl())), getActivity())) {
            packages.add(app.getPackage());
        }
        OpenModule.showHiddenDialog(getActivity(), packages, () -> updateSpinner(getUrl()));
        return true;
    }

    /** Show the popup with the rest of the apps */
    private void showList() {
        if (intentApps == null || intentApps.isEmpty()) return;
        menu.clear();
        for (int i = 1; i < intentApps.size(); i++) {
            menu.add(Menu.NONE, i, i, intentApps.get(i).getLabel(getActivity()));
        }
        menu.add(1, -1, 65535, R.string.mOpen_fixedBrowserTitle);
        popup.show();
    }

}

class OpenConfig extends AModuleConfig {

    public OpenConfig(ModulesActivity activity) {
        super(activity);
    }

    @Override
    public int getLayoutId() {
        return R.layout.config_open;
    }

    @Override
    public void onInitialize(View views) {
        CTabs.PREF(getActivity()).attachToSpinner(views.findViewById(R.id.ctabs_pref), null);
        Incognito.PREF(getActivity()).attachToSpinner(views.findViewById(R.id.incognito_pref), null);
        OpenModule.CLOSEOPEN_PREF(getActivity()).attachToSwitch(views.findViewById(R.id.closeopen_pref));
        OpenModule.NOREFERRER_PREF(getActivity()).attachToSwitch(views.findViewById(R.id.noReferrer));
        OpenModule.REJECTED_PREF(getActivity()).attachToSwitch(views.findViewById(R.id.rejected));
        LastOpened.PERDOMAIN_PREF(getActivity()).attachToSwitch(views.findViewById(R.id.perDomain));
        OpenModule.ICONSIZE_PREF(getActivity()).attachToSpinner(views.findViewById(R.id.iconsize_pref), null);
        views.findViewById(R.id.hidden_apps).setOnClickListener(v -> showHiddenDialog());
        Switch fixed = views.findViewById(R.id.fixed_browser_enabled);
        Button choose = views.findViewById(R.id.fixed_browser_choose);
        Runnable refreshBrowser = () -> {
            fixed.setChecked(BrowserPreference.enabled(getActivity()).get());
            choose.setText(getActivity().getString(R.string.mOpen_fixedBrowserSelected,
                    BrowserPreference.selectedLabel(getActivity())));
        };
        refreshBrowser.run();
        fixed.setOnCheckedChangeListener((button, checked) -> {
            if (checked && BrowserPreference.component(getActivity()) == null) {
                fixed.setChecked(false);
                BrowserPreference.showPicker(getActivity(), refreshBrowser);
            } else BrowserPreference.enabled(getActivity()).set(checked);
        });
        choose.setOnClickListener(v -> BrowserPreference.showPicker(getActivity(), refreshBrowser));

        // share
        ShareUtility.onInitializeConfig(views, getActivity());
    }

    /** Some urls to find the apps that usually open links */
    private static final List<String> SAMPLE_URLS = List.of(
            "https://example.com/",
            "http://example.com/",
            "https://example.com/video.mp4",
            "https://example.com/video.mkv",
            "https://example.com/stream.m3u8",
            "https://example.com/audio.mp3",
            "https://example.com/document.pdf"
    );

    /** Shows the dialog to hide apps, from the ones that can open common links plus the already hidden ones */
    private void showHiddenDialog() {
        var packages = new LinkedHashSet<String>();
        for (var url : SAMPLE_URLS) {
            for (var app : IntentApp.getOtherPackages(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), getActivity())) {
                packages.add(app.getPackage());
            }
        }
        packages.addAll(OpenModule.getHiddenPackages(getActivity()));
        OpenModule.showHiddenDialog(getActivity(), packages, null);
    }
}


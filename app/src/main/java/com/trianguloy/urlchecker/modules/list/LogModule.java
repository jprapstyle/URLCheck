package com.trianguloy.urlchecker.modules.list;

import android.app.AlertDialog;
import android.content.Context;
import android.text.util.Linkify;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.trianguloy.urlchecker.modules.companions.LogStore;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.activities.ModulesActivity;
import com.trianguloy.urlchecker.dialogs.MainDialog;
import com.trianguloy.urlchecker.modules.AModuleConfig;
import com.trianguloy.urlchecker.modules.AModuleData;
import com.trianguloy.urlchecker.modules.AModuleDialog;
import com.trianguloy.urlchecker.url.UrlData;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.IntPref;
import com.trianguloy.urlchecker.utilities.generics.GenericPref.StringPref;
import com.trianguloy.urlchecker.utilities.methods.JavaUtils;

import java.text.DateFormat;
import java.util.Date;

/** A module that logs all urls that passes through it */
public class LogModule extends AModuleData {

    public static final String PREF = "log_data";

    public static StringPref LOG_DATA(Context cntx) {
        return new StringPref(PREF, "", cntx);
    }

    public static IntPref LOG_LIMIT(Context cntx) {
        return new IntPref("log_limit", 0, cntx); // 0 means unlimited
    }

    @Override
    public String getId() {
        return "log";
    }

    @Override
    public int getName() {
        return R.string.mLog_name;
    }

    @Override
    public AModuleDialog getDialog(MainDialog cntx) {
        return new LogDialog(cntx);
    }

    @Override
    public AModuleConfig getConfig(ModulesActivity cntx) {
        return new LogConfig(cntx);
    }
}

class LogDialog extends AModuleDialog {


    public LogDialog(MainDialog dialog) {
        super(dialog);
    }

    @Override
    public int getLayoutId() {
        return -1;
    }

    @Override
    public void onInitialize(View views) {
        // new instance, log date
        addLine("--- " + DateFormat.getDateTimeInstance().format(new Date()) + " ---");
    }

    @Override
    public void onPrepareUrl(UrlData urlData) {
        // new url, log it
        addLine("> " + urlData.url);
    }

    private void addLine(String line) {
        LogStore.append(getActivity(), line);
    }

}

class LogConfig extends AModuleConfig {


    public LogConfig(ModulesActivity activity) {
        super(activity);
    }

    @Override
    public int getLayoutId() {
        return R.layout.config_log;
    }

    @Override
    public void onInitialize(View views) {
        views.findViewById(R.id.view).setOnClickListener(v -> showLog(false));
        views.findViewById(R.id.edit).setOnClickListener(v -> showLog(true));

        LogModule.LOG_LIMIT(getActivity()).attachToEditText(views.findViewById(R.id.limit), 0);
    }

    /** Display the log, editable or clickable */
    public void showLog(boolean editable) {
        LogStore.preview(getActivity(), (preview, error) -> getActivity().runOnUiThread(() -> {
            if (getActivity().isFinishing() || getActivity().isDestroyed()) return;
            if (error != null) {
                Toast.makeText(getActivity(), R.string.mLog_ioError, Toast.LENGTH_LONG).show();
                return;
            }
            showPreview(editable && !preview.truncated, preview);
        }));
    }

    private void showPreview(boolean editable, LogStore.Preview preview) {
        // init textview with content
        // on editable: an editText
        // on non-editable: a textview with links
        TextView textView = editable ? new EditText(getActivity()) : new TextView(getActivity());
        textView.setText(!preview.text.isEmpty() ? preview.text
                : editable ? "" : getActivity().getString(R.string.mLog_empty));
        if (!editable) Linkify.addLinks(textView, Linkify.WEB_URLS);

        // wrap into a padded scrollview for nice scrolling
        int pad = getActivity().getResources().getDimensionPixelSize(R.dimen.smallPadding);
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.addView(textView);
        scrollView.setPadding(pad, pad, pad, pad);
        scrollView.post(() -> scrollView.scrollTo(0, textView.getHeight())); // start at bottom (new)

        // common dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.mLog_name)
                .setView(scrollView)
                .setNegativeButton(R.string.close, null);
        if (preview.truncated) builder.setMessage(R.string.mLog_recentOnly);

        if (editable) {
            // editable: add save and clear buttons
            builder = builder
                    .setPositiveButton(R.string.save, (dialog, which) ->
                            LogStore.replace(getActivity(), preview, textView.getText().toString(), (value, error) -> showWriteError(error))
                    )
                    .setNeutralButton(R.string.clear, null); // set below
        }

        if (!editable) builder.setNeutralButton(R.string.clear, (d, w) ->
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.mLog_clearConfirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.clear, (confirm, which) ->
                                LogStore.clear(getActivity(), (value, error) -> showWriteError(error)))
                        .show());
        // show
        AlertDialog dialog = builder.show();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // prepare more dialog
        // these are configured here to disable automatic auto-closing when they are pressed
        if (editable) {
            // editable: configure clear button
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                // clear content
                textView.setText("");
            });
        }

    }
    private void showWriteError(Exception error) {
        if (error != null) getActivity().runOnUiThread(() -> {
            if (!getActivity().isFinishing())
                Toast.makeText(getActivity(), R.string.mLog_ioError, Toast.LENGTH_LONG).show();
        });
    }

}
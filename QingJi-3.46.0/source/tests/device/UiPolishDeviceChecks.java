package me.minis.ledger;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;

/** Opt-in real-device UI checks; creates only labelled fixtures and undoes them. */
final class UiPolishDeviceChecks {
    private final Instrumentation instrumentation;
    private WebView web;

    UiPolishDeviceChecks(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    String run(android.os.Bundle options) throws Exception {
        Intent intent = instrumentation.getTargetContext().getPackageManager()
                .getLaunchIntentForPackage(instrumentation.getTargetContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Activity activity = instrumentation.startActivitySync(intent);
        instrumentation.runOnMainSync(() -> web = findWeb(activity.findViewById(android.R.id.content)));
        if (web == null) throw new AssertionError("App WebView not found");
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (!"true".equals(js("document.readyState==='complete' && typeof toolsRequest==='function'"))) {
            if (SystemClock.uptimeMillis() > deadline) throw new AssertionError("App page did not load");
            SystemClock.sleep(100);
        }
        boolean release = options.getString("suite", "").equals("release");
        android.content.SharedPreferences metrics = instrumentation.getTargetContext().getSharedPreferences("release-check", 0);
        if (release && options.getString("phase", "").equals("baseline")) {
            if (!metrics.edit().putString("before", ledgerMetrics()).commit()) throw new AssertionError("Could not persist the read-only acceptance baseline");
            return "PASS: Existing ledger counts and amount totals recorded privately for read-only acceptance\n";
        }
        String script = release ? "release-ui.js" : options.getString("suite", "").equals("features") ? "features-ui.js" : "ui-polish.js";
        try (InputStream in = instrumentation.getContext().getAssets().open(script)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int size;
            while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
            js("window.polishPhase=" + org.json.JSONObject.quote(options.getString("phase", "all")) + ";window.polishCleanupMarker=" + org.json.JSONObject.quote(options.getString("cleanup", "")) + ";" + out.toString("UTF-8"));
        }
        deadline = SystemClock.uptimeMillis() + 90000;
        String result;
        while ("null".equals(result = js("window.polishResult || null"))) {
            if (SystemClock.uptimeMillis() > deadline) throw new AssertionError("UI test still running; inspect fixture cleanup before rerun");
            SystemClock.sleep(200);
        }
        String message = new JSONArray("[" + result + "]").getString(0);
        if (!message.startsWith("PASS")) throw new AssertionError(message);
        if (release) {
            String before = metrics.getString("before", "");
            if (before.isEmpty()) throw new AssertionError("Acceptance baseline is missing, so no comparison was possible");
            if (!before.equals(ledgerMetrics())) throw new AssertionError("Ledger aggregate counts or amounts changed during read-only acceptance");
            metrics.edit().clear().commit();
            message += "\nExisting ledger counts and original amount totals unchanged during read-only acceptance";
        }
        return message + "\n";
    }

    private String ledgerMetrics() {
        StringBuilder values = new StringBuilder();
        try (android.database.sqlite.SQLiteDatabase database = android.database.sqlite.SQLiteDatabase.openDatabase(instrumentation.getTargetContext().getDatabasePath("ledger.db").getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
             android.database.Cursor cursor = database.rawQuery("SELECT status,kind,count(*),sum(cents) FROM entries GROUP BY status,kind ORDER BY status,kind", null)) {
            while (cursor.moveToNext()) values.append(cursor.getString(0)).append('|').append(cursor.getString(1)).append('|').append(cursor.getLong(2)).append('|').append(cursor.getLong(3)).append(';');
        }
        return values.toString();
    }

    private String js(String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        String[] value = new String[1];
        instrumentation.runOnMainSync(() -> web.evaluateJavascript(script, result -> {
            value[0] = result;
            done.countDown();
        }));
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("WebView callback timed out");
        return value[0];
    }

    private WebView findWeb(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWeb(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}

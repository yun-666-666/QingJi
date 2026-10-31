package me.minis.ledger;
import android.app.Application;import android.content.*;
public final class LedgerApp extends Application {
 @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);SummaryNotice.changed(this);LedgerWidget.changed(this);}
 public void onCreate(){super.onCreate();SharedPreferences p=LedgerDb.prefs(this);if(!p.contains("sr_initialized"))p.edit().putBoolean("sr_initialized",true).putBoolean("summary_on",false).apply();PowerProbe.init(this);new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{IgnoredCleanup.start(this);SummaryNotice.changed(this);LedgerWidget.changed(this);},1800);
 IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_DATE_CHANGED);f.addAction(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);f.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){IgnoredCleanup.request(c);if(Intent.ACTION_USER_PRESENT.equals(i.getAction()))SummaryNotice.checkDate(c);else SummaryNotice.changed(c);LedgerWidget.changed(c);PowerProbe.sample();}},f);
 }
}

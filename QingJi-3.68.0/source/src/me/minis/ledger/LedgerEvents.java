package me.minis.ledger;
import android.content.*;
/** Local package-only event; no repeating job, alarm or wake lock. */
public final class LedgerEvents {
 public static final String ACTION="me.minis.ledger.ENTRIES_CHANGED";
 private static final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
 public static long revision(){return revision.get();}
 public static void changed(Context c){revision.incrementAndGet();try{SummaryNotice.changed(c);LedgerWidget.changed(c);c.sendBroadcast(new Intent(ACTION).setPackage(c.getPackageName()));}catch(RuntimeException e){PowerDetail.count("change_notify_error");}}
}

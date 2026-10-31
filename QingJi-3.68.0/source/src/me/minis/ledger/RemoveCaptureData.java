package me.minis.ledger;
import android.content.Context;import java.io.File;
/** Only legacy app-owned capture files, never ledger DB or exported user documents. */
final class RemoveCaptureData {
 static void run(Context c){try{remove(new File(c.getFilesDir(),"visual-debug"));LedgerDb.prefs(c).edit().remove("ocr_consent").remove("visual_debug").remove("visual_debug_error").apply();}catch(Exception ignored){}}
 private static void remove(File f){if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)remove(x);}if(f.exists())f.delete();}
}

package me.minis.ledger;

import android.content.*;
import java.util.*;

final class CaptureSources {
 static boolean allowed(Context context,String pkg){
  if(!PaymentApps.allowed(pkg)||pkg.equals(context.getPackageName()))return false;
  SharedPreferences p=LedgerDb.prefs(context);if(p.contains("source_"+pkg))return p.getBoolean("source_"+pkg,false);
  if(p.getBoolean("capture_all",true))return true;
  for(int i=0;i<LedgerDb.SOURCES.length;i++)if(pkg.equals(LedgerDb.SOURCES[i]))return i<3;
  return false;
 }
 static String[] packages(Context context){
  SharedPreferences p=LedgerDb.prefs(context);if(p.getBoolean("capture_all",true))return null;
  Set<String> out=new LinkedHashSet<>();for(String pkg:LedgerDb.SOURCES)if(allowed(context,pkg))out.add(pkg);
  for(String key:p.getAll().keySet())if(key.startsWith("source_")&&allowed(context,key.substring(7)))out.add(key.substring(7));
  // An empty filter means every app to Android. Use our excluded package to express none.
  if(out.isEmpty())out.add(context.getPackageName());return out.toArray(new String[0]);
 }
}

package me.minis.ledger;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

final class ScreenProfiles {
 private final List<ScreenProfile> profiles=new ArrayList<>();
 ScreenProfiles(Context context){
  try(InputStream input=context.getAssets().open("screen-profiles.json")){
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
   while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
   JSONArray rules=new JSONObject(bytes.toString("UTF-8")).getJSONArray("profiles");
   for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);JSONArray ids=rule.optJSONArray("paidViewIds");String[] suffixes=new String[ids==null?0:ids.length()];for(int j=0;j<suffixes.length;j++)suffixes[j]=ids.getString(j);
    profiles.add(new ScreenProfile(rule.getString("id"),rule.optString("package"),rule.optLong("minVersion"),rule.optLong("maxVersion"),suffixes));
   }
  }catch(IOException|JSONException e){profiles.clear();}
 }
 ScreenProfile select(PaymentSnapshot snapshot){for(ScreenProfile rule:profiles)if(!rule.pkg.isEmpty()&&rule.matches(snapshot))return rule;for(ScreenProfile rule:profiles)if(rule.matches(snapshot))return rule;return ScreenProfile.SEMANTIC;}
}

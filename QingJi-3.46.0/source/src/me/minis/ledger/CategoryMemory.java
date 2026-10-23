package me.minis.ledger;
import android.content.*;import java.util.*;
/** Explicit user opt-in only. Preferences separate from ledger; no historical rewrite. */
public final class CategoryMemory {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("merchant_categories",Context.MODE_PRIVATE);}
 static String token(String merchant,String kind){String key=ExpenseCategory.key(merchant,kind);return key.isEmpty()?"":LedgerDb.hash(key);}
 public static String get(Context c,String merchant,String kind){String key=token(merchant,kind);if(key.isEmpty())return "";String base=MerchantIdentity.groupName(merchant),group=token(base,kind);SharedPreferences p=prefs(c);String value=p.getString("group:"+group,"");if(!value.isEmpty())return value;value=p.getString(key,"");if(!value.isEmpty())return value;String found="";for(String suffix:new String[]{"","校园版","商家版","个人版"}){String name=base+suffix;if(!MerchantIdentity.groupName(name).equals(base))continue;String v=p.getString(token(name,kind),"");if(!v.isEmpty()){if(!found.isEmpty()&&!found.equals(v))return "";found=v;}}return found;}
 public static void set(Context c,String merchant,String kind,String category){String key=token(merchant,kind);if(key.isEmpty()||!Arrays.asList(LedgerDb.CATEGORIES).contains(category))throw new IllegalArgumentException("请填写具体商户后再记住分类");prefs(c).edit().putString("group:"+token(MerchantIdentity.groupName(merchant),kind),category).apply();}
 public static void clear(Context c){prefs(c).edit().clear().apply();}
}

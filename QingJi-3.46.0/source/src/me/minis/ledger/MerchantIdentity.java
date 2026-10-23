package me.minis.ledger;
import java.text.Normalizer;
import java.util.Locale;
/** Comparison only: never rewrite a merchant globally or erase store/branch numbers. */
public final class MerchantIdentity {
 public static String exact(String s){return s==null?"":Normalizer.normalize(s,Normalizer.Form.NFKC).trim().replaceAll("[\\s\\u3000]+","").toLowerCase(Locale.ROOT);}
 public static String groupName(String s){String v=s==null?"":s.trim();for(String suffix:new String[]{"校园版","商家版","个人版"})if(v.endsWith(suffix)&&v.length()-suffix.length()>=4)return v.substring(0,v.length()-suffix.length());return v;}
 public static String groupSql(String column){String v="trim("+column+")";return "CASE WHEN length("+v+")>=7 AND substr("+v+",-3) IN ('校园版','商家版','个人版') THEN substr("+v+",1,length("+v+")-3) ELSE "+v+" END";}
 public static boolean same(String a,String b){String x=exact(a);return !x.isEmpty()&&x.equals(exact(b));}
 // Only weak summaries may use product-edition suffixes. Not a general merchant alias.
 public static boolean previewCompatible(String a,String b){String x=exact(a),y=exact(b);if(x.isEmpty()||y.isEmpty())return false;if(x.equals(y))return true;String xs=x.replaceFirst("(?:校园版|商家版|个人版)$",""),ys=y.replaceFirst("(?:校园版|商家版|个人版)$","");return xs.length()>=4&&xs.equals(ys);}
}

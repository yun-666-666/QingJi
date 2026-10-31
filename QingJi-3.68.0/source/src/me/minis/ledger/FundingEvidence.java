package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Only bank name + last four digits; no full account or unrelated page text retained. */
public final class FundingEvidence {
 private static final Pattern TAIL=Pattern.compile("(?:尾号|账户|账号|卡号)(?:为)?[\\s*＊]*([0-9]{4})(?![0-9])|[（(]([0-9]{4})[）)]");
 static final String[][] BANKS={{"ccb","建设银行","com.chinamworld.main"},{"cmb","招商银行","cmb.pb"},{"icbc","工商银行","com.icbc"},{"abc","农业银行","com.android.bankabc"},{"boc","中国银行","com.chinamworld.bocmbci"},{"bocom","交通银行","com.bankcomm.Bankcomm"},{"psbc","邮储银行","com.yitong.mbank.psbc"}};
 static String bank(String source,String raw){String found="";for(String[] b:BANKS)if(source.equals(b[2])||raw.contains(b[1])){if(!found.isEmpty()&&!found.equals(b[0]))return "";found=b[0];}return found;}
 public static boolean wallet(String s){return s.equals("com.tencent.mm")||s.equals("com.tencent.mm:screen");}
 public static boolean bankSource(String s){for(String[] b:BANKS)if(s.equals(b[2]))return true;return s.equals("com.android.mms")||s.equals("com.google.android.apps.messaging");}
 static String tail(String s){Matcher m=TAIL.matcher(s);String found="";while(m.find()){String x=m.group(1)==null?m.group(2):m.group(1);if(!found.isEmpty()&&!found.equals(x))return "";found=x;}return found;}
 public static String notification(String source,String raw){if(!bankSource(source)||raw==null)return "";String b=bank(source,raw),t=tail(raw);return b.isEmpty()||t.isEmpty()?"":"bank|"+b+"|"+t;}
 public static String page(List<String> lines){String found="";for(int i=0;i<lines.size();i++){String s=lines.get(i).trim();if(s.matches("付款方式|支付方式")){if(i+1>=lines.size())continue;s=lines.get(i+1);}else if(!(s.startsWith("使用")&&s.contains("支付")||s.startsWith("付款方式：")||s.startsWith("支付方式：")))continue;
  if(s.contains("零钱")||s.contains("余额"))return "";String b=bank("",s),t=tail(s);if(b.isEmpty()||t.isEmpty())continue;String f="wx|"+b+"|"+t;if(!found.isEmpty()&&!found.equals(f))return "";found=f;}return found;}
 public static boolean valid(String s){return s.isEmpty()||s.matches("(?:wx|bank)\\|(?:ccb|cmb|icbc|abc|boc|bocom|psbc)\\|[0-9]{4}");}
 static boolean opposite(String a,String b){return wallet(a)&&bankSource(b)||wallet(b)&&bankSource(a);}
 static boolean strong(String a,String b){return !a.isEmpty()&&!b.isEmpty()&&!a.split("\\|")[0].equals(b.split("\\|")[0])&&a.substring(a.indexOf('|')).equals(b.substring(b.indexOf('|')));}
 static boolean conflict(String a,String b){return !a.isEmpty()&&!b.isEmpty()&&!strong(a,b);}
}

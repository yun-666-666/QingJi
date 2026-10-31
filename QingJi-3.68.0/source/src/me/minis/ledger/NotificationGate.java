package me.minis.ledger;
import java.util.*;
/** Exact key + original post time + text only; same-amount new trades are never collapsed. */
public final class NotificationGate {
 private final LinkedHashMap<String,String> recent=new LinkedHashMap<String,String>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>128;}};
 public synchronized boolean duplicate(String key,long time,String raw){String k=key+"|"+time;String old=recent.put(k,raw);return raw.equals(old);}
 public static boolean hasMoneyUnit(String s){return s!=null&&(s.indexOf('元')>=0||s.indexOf('¥')>=0||s.indexOf('￥')>=0||s.contains("人民币")||s.toUpperCase(Locale.ROOT).contains("RMB")||s.toUpperCase(Locale.ROOT).contains("CNY")||explicitPaid(s));}
 public synchronized void failed(String key,long time,String raw){String k=key+"|"+time;if(raw.equals(recent.get(k)))recent.remove(k);}
 private static boolean explicitPaid(String s){if(s.length()>16000)return false;for(String line:s.split("[\\r\\n]+")){String value=PaymentPageFields.value(line.trim(),PaymentPageRules.PAID);if(value!=null&&(value.isEmpty()||PaymentPageFields.cash(value)>0))return true;}return false;}
 public synchronized void clear(){recent.clear();}
}

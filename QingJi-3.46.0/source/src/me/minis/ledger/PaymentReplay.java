package me.minis.ledger;
import java.util.*;
/** Revisit the same complete page without a new checkout action: reuse its ingestion key. */
public final class PaymentReplay {
 private static final long TTL=180000;private final LinkedHashMap<String,Item> recent=new LinkedHashMap<>();private static class Item{String key;long at;Item(String k,long t){key=k;at=t;}}
 public static boolean startsPayment(String s){return s!=null&&s.trim().matches("确认支付|立即支付|立即付款|确认付款|去支付|去付款|提交订单|提交支付|确认购票|立即购票|确认购买|立即购买|付款|支付|确认转账|立即转账|塞钱进红包");}
 public void newPayment(){recent.clear();}
 public String lookup(String frame,long now){Item i=recent.get(frame);if(i==null)return "";if(now<i.at||now-i.at>TTL){recent.remove(frame);return "";}return i.key;}
 public void remember(String frame,String key,long now){if(recent.size()>=16)recent.remove(recent.keySet().iterator().next());recent.put(frame,new Item(key,now));}
 public void failed(String frame,String key){Item i=recent.get(frame);if(i!=null&&i.key.equals(key))recent.remove(frame);}
 public static String frame(String pkg,String activity,List<String> lines){StringBuilder b=new StringBuilder(pkg).append('|').append(activity);for(String s:lines)b.append('\n').append(s.trim());return b.toString();}
}

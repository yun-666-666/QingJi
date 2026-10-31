package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Identity follows transaction evidence and checkout, never a window's lifetime. */
public final class PaymentObservation {
 private static final Pattern ID_FIELD=Pattern.compile("^(交易号|交易单号|支付宝交易号|微信支付单号|订单号|商户订单号)\\s*[:：]?\\s*([A-Za-z0-9_-]{12,64})?$"),ID_VALUE=Pattern.compile("[A-Za-z0-9_-]{12,64}"),SPACE=Pattern.compile("\\s+");
 public static String transactionId(List<String> lines){String found="";for(int i=0;i<lines.size();i++){String s=lines.get(i).trim();Matcher m=ID_FIELD.matcher(s);if(!m.matches())continue;String value=m.group(2);if(value==null&&i+1<lines.size())value=lines.get(i+1).trim();if(value==null||!ID_VALUE.matcher(value).matches())continue;String id=m.group(1)+":"+value;if(!found.isEmpty()&&!found.equals(id))return "";found=id;}return found;}
 public static String signature(String pkg,PaymentParser.Result r,List<String> lines){String id=transactionId(lines);return LedgerDb.hash(pkg+"|"+r.kind+"|"+r.cents+"|"+MerchantIdentity.exact(r.merchant)+"|"+r.funding+"|"+id);}
 public static String page(List<String> lines){TreeSet<String> text=new TreeSet<>();for(String field:lines)if(field!=null)for(String line:field.split("[\\r\\n]+")){String s=SPACE.matcher(line.trim()).replaceAll(" ");if(!s.isEmpty())text.add(s);}return LedgerDb.hash(String.join("\n",text));}
}

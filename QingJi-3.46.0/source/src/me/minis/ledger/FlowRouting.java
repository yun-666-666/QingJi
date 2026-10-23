package me.minis.ledger;
import java.util.*;
/** Pure routing: transaction content outranks an event Activity, which can lag a window transition. */
public final class FlowRouting {
 private static boolean has(List<String> lines,String... keys){for(String s:lines)for(String k:keys)if(s.trim().equals(k))return true;return false;}
 public static String explicitType(MoneyFlow.Frame f){if(f.chat||f.truncated)return "";String own=OwnReceiptEvidence.detailType(f);if(!own.isEmpty())return own;boolean packet=OwnReceiptEvidence.packetTitle(f.text)||has(f.text,"发红包","塞钱进红包","红包详情","微信红包","领取红包","开红包","拼手气红包","普通红包","红包已领取","红包发送成功","红包已发送","红包金额");boolean transfer=has(f.text,"转账金额","转账详情","微信转账","转账成功","转账已发送","确认转账","立即转账","转账时间");if(packet&&transfer)return "conflict";return packet?"红包":transfer?"转账":"";}
 public static boolean personal(MoneyFlow.Frame f){return !explicitType(f).isEmpty();}
 public static boolean paid(MoneyFlow.Frame f){return has(f.text,"支付成功","付款成功");}
 public static boolean merchant(MoneyFlow.Frame f){for(String s:f.text)if(s.trim().matches("(?:商户名称|商家|商户|收款商户|订单号|商品|商品说明|返回商家|返回商户)(?:[:：].*|)"))return true;return false;}
 public static boolean genericLinked(MoneyFlow.Frame f,long expected){if(f.chat||f.truncated||personal(f)||!paid(f)||merchant(f)||expected<=0)return false;long actual=MoneyFlow.receiptAmount(f,"expense","转账");return actual==expected;}
}

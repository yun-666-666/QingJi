package me.minis.ledger;
import java.util.regex.Pattern;
/** Every app remains eligible, but a price alone is not a transaction. */
public final class NotificationEvidence {
 private static final Pattern FIELD=Pattern.compile("(?:支出|收入|消费|扣款|存入|转入|转出|入账|到账|付款|支付|收款)(?:金额)?\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*[0-9]",Pattern.CASE_INSENSITIVE);
 public static boolean transaction(String raw){return raw!=null&&(FIELD.matcher(raw).find()||raw.matches("(?s).*你有一笔[0-9]+(?:\\.[0-9]{1,2})?元的支出[，,。].*")||PaymentParser.has(raw,"支付成功","付款成功","成功支付","成功付款","已支付","已付款","交易成功","转账成功","已扣费","已自动支付","扣款成功","消费成功","收款到账","收款成功","收到转账","转账已收款","转账收入","转账支出","已收款","红包已领取","红包收入","退款成功","退款到账","退款已到账","已退款","退回到账","退款入账")||PaymentParser.autoDebit(raw));}
 public static boolean media(String category,String template,boolean session){return session||"transport".equals(category)||template!=null&&template.contains("MediaStyle");}
}

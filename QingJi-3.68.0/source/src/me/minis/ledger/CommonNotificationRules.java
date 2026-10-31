package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Generated from rules/common-notifications-v1.json. Independent implementation. */
public final class CommonNotificationRules {
 public static final String VERSION="2026.10.12.2";
 public static final String SHA256="53847d9c0991f1d6a1979900743625df31743fbd6145f55f71680b1488e0c111";
 private static final class Rule {final String id,source,kind;final Pattern pattern;final boolean confirm;Rule(String i,String s,String k,String re,boolean c){id=i;source=s;kind=k;pattern=Pattern.compile(re);confirm=c;}}
 private static final Rule[] RULES={
  new Rule("alipay-expense-notice","alipay","expense","你有一笔\\s*([0-9]{1,9}(?:\\.[0-9]{1,2})?)\\s*元的支出(?=[，,。]|$)",false),
  new Rule("bank-parenthesis-expense","bank","expense","(?:支出|扣款|消费)(?:金额)?\\s*(?:[（(][^）)\\n]{1,50}[）)])?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("bank-parenthesis-income","bank","income","(?:收入|入账|存入|转入)(?:金额)?\\s*(?:[（(][^）)\\n]{1,50}[）)])?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("bank-event-debit","bank","expense","发生(?:快捷支付|银联|网上支付)?扣款[，,：:\\s]*(?:人民币|RMB|CNY|[¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:元)?(?=[，,。；;\\s]|$)",true),
  new Rule("bank-event-refund","bank","refund","发生(?:快捷支付|银联|网上支付)?退款[，,：:\\s]*(?:人民币|RMB|CNY|[¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:元)?(?=[，,。；;\\s]|$)",true),
  new Rule("bank-online-purchase","bank","expense","(?:网上支付|网上支付支取|银联扣款)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("bank-credited-funds","bank","income","入账款项[，,：:\\s]*(?:人民币|RMB|CNY|[¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:元)?(?=[，,。；;\\s]|$)",true),
  new Rule("bank-completed-income","bank","income","完成(?:转存|转入|存入)交易\\s*(?:人民币|RMB|CNY|[¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:元)?(?=[，,。；;\\s]|$)",true),
  new Rule("bank-completed-expense","bank","expense","完成(?:支付|扣款|消费)交易\\s*(?:人民币|RMB|CNY|[¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?:元)?(?=[，,。；;\\s]|$)",true),
  new Rule("bank-returned-deposit","bank","refund","消费退货存入(?:人民币|RMB|CNY|[¥￥])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("transport-tap-completed","nfc","expense","刷卡完成\\s+交易金额\\s*[:：]\\s*([0-9]{1,9}(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("bank-debit-completed","bank","expense","(?:已扣款|已支出|已消费)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("bank-income-completed","bank","income","(?:已入账|已收入)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("bank-refund-completed","bank","refund","(?:退货入账|退款入账|退款已到账|退款到账)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("alipay-code-collection","alipay","income","(?:收款成功|成功收款)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("alipay-transfer-credited","alipay","income","(?:转账到账|转账已到账|转账收款成功)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("alipay-refund-credited","alipay","refund","(?:退款到账|退款已到账|退款成功)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
  new Rule("alipay-auto-debit-completed","alipay","expense","(?:自动扣款成功|代扣成功|免密支付成功)\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元(?=[，,。；;\\s]|$)",true),
 };
 private static boolean accepts(String source,String pkg,String raw){if(source.equals("alipay"))return "com.eg.android.AlipayGphone".equals(pkg);if(source.equals("nfc"))return "com.miui.tsmclient".equals(pkg);return raw.matches("(?s).*(?:您|你的|您的|你尾号).*(?:账户|尾号|银行卡|借记卡|信用卡).*")&&!"com.tencent.mm".equals(pkg);}
 public static PaymentParser.Result parse(String pkg,String raw){
  if(raw==null||PaymentParser.rejected(raw)||PaymentParser.has(raw,"信用卡还款","还款到账","花呗还款","借款已发放","贷款发放","提现到账","转入余额宝","余额宝转出"))return null;
  Set<Long> amounts=new HashSet<>();Set<String> kinds=new HashSet<>();boolean confirmed=true;String id="";
  for(Rule rule:RULES)if(accepts(rule.source,pkg,raw)){Matcher m=rule.pattern.matcher(raw);while(m.find()){long n=MoneyFlow.amount(m.group(1));if(n>0){amounts.add(n);kinds.add(rule.kind);confirmed&=rule.confirm;id=rule.id;}}}
  if(amounts.isEmpty())return null;if(kinds.contains("refund")){kinds.remove("income");}
  if(amounts.size()!=1||kinds.size()!=1){PaymentParser.Result r=PaymentParser.parse(raw);if(r!=null){r.confirmed=false;r.reason="规则包匹配多个金额或不同方向，请核对";}return r;}
  long cents=amounts.iterator().next();String kind=kinds.iterator().next();String marker=kind.equals("refund")?"退款成功":kind.equals("income")?"收入人民币":"支出人民币";
  PaymentParser.Result r=PaymentParser.parse(raw+"\n"+marker+String.format(Locale.US,"%.2f",cents/100.0)+"元");if(r==null)return null;r.cents=cents;r.kind=kind;
  if(!confirmed){r.confirmed=false;r.reason="通知有明确支出候选，但无唯一交易身份；请核对是否与支付页面为同一笔";}
  ExpenseCategory.enrich(r,raw);return r;
 }
}

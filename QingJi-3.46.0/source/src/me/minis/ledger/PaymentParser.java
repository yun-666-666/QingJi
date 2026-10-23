package me.minis.ledger;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Pure Java. Ambiguity must stay in review, never silently become expense. */
public final class PaymentParser {
 public static final class Result {
  public String kind="expense", merchant="", category="其他", reason="", transactionId="", funding="";
  public String pageSignature="",pageFrame="",checkout="";public boolean pageStrong;
  public long cents, observedAt; public boolean confirmed; public boolean paidEvidence; public boolean categoryExplicit;
  String categoryMerchant=null;
 }
 private static final String NUM="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,9})(?:\\.[0-9]{1,2})?";
 private static final Pattern MONEY=Pattern.compile("(?:(?:[¥￥]|人民币|RMB|CNY)\\s*("+NUM+")(?![0-9.,])|(?<![0-9.,\\-－])("+NUM+")\\s*(?:元|人民币|CNY|RMB))",Pattern.CASE_INSENSITIVE);
 private static final Pattern MERCHANT=Pattern.compile("(?:商户(?:名称)?|商家(?:名称)?|收款方(?:名称)?|收款商户(?:名称)?|对方)[ \\t]*(?:[:：][ \\t]*|\\n[ \\t]*)([^\\n，,。；;]{1,60})");
 private static final Pattern DIRECTED=Pattern.compile("(?:支出|收入|消费|扣款|存入|转入|转出|付款|支付|收款|入账|到账)(?:金额)?\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*("+NUM+")(?![0-9.,])",Pattern.CASE_INSENSITIVE);
 private static final Pattern BALANCE=Pattern.compile("(?:可用余额|账户余额|余额)\\s*[:：为]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*("+NUM+")(?![0-9.,])",Pattern.CASE_INSENSITIVE);
 private static final Pattern PAYEE=Pattern.compile("(?:向|在)\\s*([^\\n，,。:：]{1,32}?)\\s*(?:支付|付款|消费)"),AUTO_PAYEE=Pattern.compile("你在([^\\n]{1,80}?)有一笔");
 public static Result parseNotification(String source,String title,String raw) {
  if(raw!=null&&nonTransaction(raw))return null;
  if("com.tencent.mm".equals(source)&&!(title==null?"":title.trim()).matches("^(?:微信支付|微信收款助手|收款助手)(?:[:：\\s（(].*)?$"))return null;
  if(NotificationSemantics.blocked(raw)||NotificationSemantics.zeroPaid(source,raw))return null;
  Result movement=NotificationSemantics.reviewOnly(raw);if(movement!=null)return movement;
  Result paid=NotificationSemantics.paid(source,raw);if(paid!=null)return NotificationSemantics.apply(raw,paid);
  Result automatic=WechatAutoDebit.notification(source,title,raw);if(automatic!=null)return NotificationSemantics.apply(raw,automatic);
  Result common=CommonNotificationRules.parse(source,raw);if(common!=null)return NotificationSemantics.apply(raw,common);
  if(!NotificationEvidence.transaction(raw))return null;
  Result r=parse(raw);
  if(r!=null&&"com.tencent.mm".equals(source)) {
   String t=title==null?"":title.trim();
   if(!t.matches("^(?:微信支付|微信收款助手|收款助手)(?:[:：\\s（(].*)?$")){r.confirmed=false;r.reason="聊天或合并通知中的金额，请核对是否为实际交易";}
  }
  return NotificationSemantics.apply(raw,r);
 }
 public static boolean nonTransaction(String s){return has(s,"交易上限","每日交易上限","免密限额","调整该限额","支付限额","额度调整","授信额度","可借额度","[链接]","【链接】","领券购买","复制口令","满减优惠");}
 public static boolean autoDebit(String s){return s.matches("(?s).*你在[^\\n]{1,80}有一笔[0-9]+(?:\\.[0-9]{1,2})?元的免密[/／]自动扣款支付.*")&&!has(s,"即将","预计","申请","失败","待支付");}
 public static boolean rejected(String s){return s==null||s.trim().isEmpty()||nonTransaction(s)||has(s,"验证码","校验码","优惠券","领红包","待付款","等待付款","待支付","支付失败","付款失败","交易失败","退款失败","余额不足","支付取消","付款取消","免密支付已开通","账单日","还款提醒","自动扣款提醒","扣款提醒","预计扣款","即将扣款","支付请求","收款请求","向你转账","待收款","请收款","待领取","转账待确认","支付处理中","付款处理中","退款处理中","退款申请","申请退款","退款审核","尚未支付","未支付","尚未付款","未付款","支付未成功","付款未成功","支付不成功","扣款失败","未扣款","未收款","收款失败","昨日消费","本月消费","累计消费","月度账单","年度账单");}
 public static Result parse(String raw) {
  if(raw==null) return null;
  String s=raw.replace('\r','\n').trim();
  if(s.isEmpty()||nonTransaction(s))return null;
  if(rejected(s))return null;
  boolean action=has(s,"支付","付款","消费","扣款","支出","收款","到账","入账","退款","转账","红包","收入");
  // Every app is eligible. Money-only notifications are candidates, not confirmed expenses.
  Set<Long> amounts=new LinkedHashSet<>(); Matcher m=MONEY.matcher(s);
  while(m.find()) {try{String v=m.group(1)!=null?m.group(1):m.group(2);long c=new BigDecimal(v.replace(",","")).movePointRight(2).longValueExact();if(c>0&&c<=99999999999L)amounts.add(c);}catch(Exception ignored){}}
  if(amounts.isEmpty())return null;
  Set<Long> directed=new LinkedHashSet<>();
  Matcher dm=DIRECTED.matcher(s);
  while(dm.find()){try{long c=new BigDecimal(dm.group(1).replace(",","")).movePointRight(2).longValueExact();if(amounts.contains(c))directed.add(c);}catch(Exception ignored){}}
  // Only discard extra amounts when they are explicitly account balances.
  Set<Long> balances=new LinkedHashSet<>();Matcher bm=BALANCE.matcher(s);
  while(bm.find()){try{balances.add(new BigDecimal(bm.group(1).replace(",","")).movePointRight(2).longValueExact());}catch(Exception ignored){}}
  if(directed.size()==1&&amounts.size()>1){Set<Long> extras=new LinkedHashSet<>(amounts);extras.removeAll(directed);if(balances.containsAll(extras))amounts=directed;}
  Result r=new Result();r.cents=directed.size()==1?directed.iterator().next():amounts.iterator().next();
  boolean refund=has(s,"退款成功","退款到账","退款已到账","已退款","退回到账","退款入账");
  boolean income=has(s,"收款到账","收款成功","收款金额","收到转账","转账已收款","转账收入","已收款","收入金额","收入人民币","收入到账","红包已领取","红包收入");
  boolean expense=has(s,"支付成功","付款成功","扣款成功","消费成功","已支付","已付款","扣款金额","支出金额","消费金额","付款金额","支付金额","成功支付","成功付款","转账支出","支出人民币","完成扣款人民币");
  income=income||s.matches("(?s).*(?:收入|存入|转入|入账)\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*[0-9].*");
  expense=expense||s.matches("(?s).*(?:支出|消费|扣款|转出)\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*[0-9].*");
  expense=expense||autoDebit(s);
  if(refund)r.kind="refund";else if(income)r.kind="income";else r.kind="expense";
  boolean uncertainRefund=has(s,"退款")&&!refund;
  r.confirmed=amounts.size()==1&&!uncertainRefund&&(refund||income||expense)&&!(income&&expense);
  if(amounts.size()>1)r.reason="通知包含多个金额，请核对实际交易金额";
  else if(income&&expense)r.reason="通知同时含收入和支出线索";
  else if(uncertainRefund)r.reason="退款状态不明确";
  else if(!r.confirmed)r.reason="未识别到明确的交易完成/收支字段";
  if(has(s,"美元","港元","港币","日元","欧元","澳元","新加坡元","USD","HKD","JPY","EUR")){r.confirmed=false;r.reason="含外币单位，本账本按人民币汇总，请换算并核对金额";}
  String evidence=String.join("\n",ExpenseCategory.fields(s));Matcher mm=MERCHANT.matcher(evidence);Set<String> names=new LinkedHashSet<>();boolean labelled=false,bad=false;while(mm.find()){labelled=true;String name=mm.group(1).trim();if(name.length()<2||!name.matches(".*[\\p{L}].*")||has(name,"：",":","支付成功","付款成功","支付金额","付款金额","订单号"))bad=true;else names.add(name);}if(!bad&&names.size()==1)r.merchant=names.iterator().next();
  if(!labelled){Matcher who=PAYEE.matcher(evidence);if(who.find())r.merchant=who.group(1).trim();}
  if(!labelled&&autoDebit(evidence)){Matcher am=AUTO_PAYEE.matcher(evidence);if(am.find())r.merchant=am.group(1);}
  r.category=category(r.merchant);ExpenseCategory.enrich(r,s);
  return r;
 }
 public static boolean has(String s,String... keys){for(String k:keys)if(s.contains(k))return true;return false;}
 public static String category(String s){return ExpenseCategory.infer(s);}
 /** Same notification update: only collapse identical semantics within bounded time. */
 public static boolean sameUpdate(String oldKey,String newKey,long oldTime,long newTime,String oldSemantic,String newSemantic){return oldKey.equals(newKey)&&newTime==oldTime&&oldSemantic.equals(newSemantic);}
}

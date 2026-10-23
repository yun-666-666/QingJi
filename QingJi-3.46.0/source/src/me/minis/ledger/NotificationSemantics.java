package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Personal ledger semantics learned from official field definitions, not merchant API access. */
public final class NotificationSemantics {
 private static final String NUM="(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,9})(?:\\.[0-9]{1,2})?";
 public static boolean blocked(String s){return s!=null&&(PaymentParser.has(s,"交易关闭","订单已关闭","订单已取消","付款已取消","交易已撤销","支付已撤销","待扣款","等待扣款","等待买家付款","等待付款中","等待收款","退款受理成功","退款申请已受理","退款审核通过","商家同意退款","退款未到账","预计到账","退款待处理","等待退款","退货申请","订单待支付")||s.matches("(?s).*(?:申请|预计|将于|即将).{0,12}(?:支付|付款|扣款|到账).*"));}
 public static String ambiguity(String raw){if(raw==null)return "";if(PaymentParser.has(raw,"小荷包","亲情卡","亲属卡","家庭账户","共同账户"))return "共同资金或代付通知，未确定是否为本人实际收支，请核对操作者和是否已记账";
  if(PaymentParser.has(raw,"提现","充值","余额宝转入","转入余额宝","余额宝转出","零钱通转入","转入零钱通","零钱通转出","信用卡还款","还信用卡","花呗还款","白条还款","贷款到账","借款到账","贷款发放","借款已发放","基金赎回","理财赎回","购买基金","理财购买","预授权","资金冻结","资金解冻","冻结金额"))return "资金移动、负债结算或预授权，不等同新增消费/收入；请核对账本口径，未自动计入";return "";
 }
 public static PaymentParser.Result reviewOnly(String raw){if(ambiguity(raw).isEmpty()||!PaymentParser.has(raw,"成功","到账","已支付","已付款","已消费","已扣款","已入账","已提现","已冻结","已解冻","预授权冻结","冻结金额"))return null;PaymentParser.Result r=PaymentParser.parse(raw);return apply(raw,r);}
 public static boolean zeroPaid(String pkg,String raw){return raw!=null&&(pkg.equals("com.eg.android.AlipayGphone")||pkg.equals("com.tencent.mm")||pkg.equals("com.unionpay"))&&raw.matches("(?s).*(?:^|[\\n，,；;。])\\s*(?:实付金额|实际付款|实际支付|实付款|用户付款金额|付款金额)\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*0(?:\\.0{1,2})?\\s*(?:元)?(?=[\\n，,；;。\\s]|$).*");}
 public static PaymentParser.Result apply(String raw,PaymentParser.Result r){if(r==null)return null;if(blocked(raw))return null;String reason=ambiguity(raw);if(!reason.isEmpty()){r.confirmed=false;r.reason=reason;}return r;}
 private static Set<Long> fields(String raw,String[] labels){Set<Long> out=new LinkedHashSet<>();for(String label:labels){Matcher m=Pattern.compile("(?m)(?:^|[\\n，,；;。])\\s*"+label+"\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*("+NUM+")\\s*(?:元)?(?=[\\n，,；;。\\s]|$)",Pattern.CASE_INSENSITIVE).matcher(raw);while(m.find()){long n=MoneyFlow.amount(m.group(1));if(n>0)out.add(n);}}return out;}
 public static PaymentParser.Result paid(String pkg,String raw){
  if(raw==null||!(pkg.equals("com.eg.android.AlipayGphone")||pkg.equals("com.tencent.mm")||pkg.equals("com.unionpay"))||blocked(raw)||!ambiguity(raw).isEmpty()||PaymentParser.rejected(raw))return null;
  if(!PaymentParser.has(raw,"支付成功","付款成功","已支付","已付款","成功支付","成功付款")||PaymentParser.has(raw,"退款","收入","入账","收款到账","收款成功","美元","港元","港币","USD","HKD","日元","JPY","欧元","EUR"))return null;
  String[] labels={"实付金额","实际付款","实际支付","实付款","用户付款金额","付款金额"};Set<Long> amounts=fields(raw,labels);if(amounts.size()!=1)return null;long value=amounts.iterator().next();
  // Strip only fully-labelled known auxiliary money fields, never unlabelled extras.
  String reduced=raw;String[] extras={"订单金额","商品金额","原价","优惠金额","优惠券抵扣","优惠合计","已优惠","商家实收金额","商户实收金额","实收金额","可开票金额","账户余额","可用余额","余额"};
  for(String label:extras)reduced=reduced.replaceAll("(?m)(?:^|[\\n，,；;])\\s*"+label+"\\s*[:：]?\\s*(?:人民币|RMB|CNY|[¥￥])?\\s*"+NUM+"\\s*(?:元)?(?=[\\n，,；;\\s]|$)","\n");
  PaymentParser.Result r=PaymentParser.parse(reduced+"\n支出人民币"+String.format(Locale.US,"%.2f",value/100.0)+"元");if(r==null)return null;if(r.cents!=value||r.reason.startsWith("通知包含")||!r.kind.equals("expense")){r.cents=value;r.confirmed=false;r.reason="实付字段与其他金额不一致或有未标注金额，请核对";}return r;
 }
}

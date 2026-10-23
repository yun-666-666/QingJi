package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Explicit paid fields / order-discount arithmetic, never smallest-money guessing. */
public final class AlipayPaid {
 static final String DISCOUNT="(?:碰一下立减|碰一碰立减|碰一碰优惠|支付优惠|优惠金额|本次优惠|已优惠|立减|随机立减|优惠合计|优惠总额|红包抵扣)";
 static final String ORDER="(?:订单金额|商品金额|原价|原金额)";
 static final String PAID="(?:实付金额|实际付款|实际支付|实际支付金额|实付款|实付)";
 static long amount(String s){s=s.trim().replaceFirst("^[−－-]","");return MoneyFlow.amount(s);}
 static Long field(List<String> t,String labels){Long result=null;Pattern p=Pattern.compile("^"+labels+"(?:\\s*[:：]\\s*|\\s+)?([−－-]?[¥￥]?\\s*[0-9]+(?:\\.[0-9]{1,2})?(?:元)?)?$");for(int i=0;i<t.size();i++){Matcher m=p.matcher(t.get(i).trim());if(!m.matches())continue;String value=m.group(1);if(value==null&&i+1<t.size())value=t.get(i+1);if(value==null)return -1L;long n=amount(value);if(n<=0)return -1L;if(result!=null&&result!=n)return -1L;result=n;}return result;}
 public static boolean apply(String pkg,List<String> t,PaymentParser.Result r){
  if(!AlipayPaymentCards.PKG.equals(pkg)||r==null||!r.kind.equals("expense"))return false;
  String all=String.join("\n",t);if(PaymentParser.has(all,"退款","美元","港币","港元","日元","欧元","USD","HKD","JPY","EUR"))return false;
  Long order=field(t,ORDER),discount=field(t,DISCOUNT),paid=field(t,PAID);long target=paid==null?0:paid;
  if(paid!=null&&paid<=0||order!=null&&order<=0||discount!=null&&discount<=0||order!=null&&discount!=null&&discount>=order){r.confirmed=false;r.reason="实付或优惠字段无效、为零或互相冲突，请核对";return true;}
  if(order!=null&&discount!=null&&order>0&&discount>0&&order>discount){long computed=order-discount;if(target>0&&target!=computed){r.confirmed=false;r.reason="实付金额与订单减优惠不一致，请核对";return true;}target=computed;}
  if(target<=0)return false;
  Long payment=field(t,"(?:付款金额|支付金额)");if(payment!=null&&payment!=target){r.confirmed=false;r.reason="多个付款字段互相冲突，请核对实际付款金额";return true;}
  // Only supported labelled extras or target itself may appear; signed discounts stay attached to their label.
  for(int i=0;i<t.size();i++){String s=t.get(i).trim();if(s.matches("^(?:"+ORDER+"|"+DISCOUNT+"|"+PAID+").*")){if(s.matches("(?:"+ORDER+"|"+DISCOUNT+"|"+PAID+")[:：]?"))i++;continue;}long n=amount(s);if(n>0&&n!=target){r.confirmed=false;r.reason="优惠实付页仍有未标注或冲突金额，请核对";return true;}}
  r.cents=target;r.confirmed=true;r.paidEvidence=true;r.reason="";return true;
 }
 static long billExpense(String text){String s=text.trim();if(s.matches("[−－-][¥￥]?\\s*[0-9]{1,9}(?:\\.[0-9]{1,2})?(?:元)?"))return amount(s);Matcher m=Pattern.compile("^支出\\s*[:：]?\\s*[¥￥]?\\s*([0-9]{1,9}(?:\\.[0-9]{1,2})?)\\s*元$").matcher(s);return m.matches()?MoneyFlow.amount(m.group(1)):0;}
 public static WechatReceiptParser.Receipt bill(String pkg,List<String> lines,long now){
  if(!AlipayPaymentCards.PKG.equals(pkg)||lines==null||lines.size()>240)return null;List<String> t=AlipayPaymentCards.clean(lines);
  if(!t.contains("账单详情")||!t.contains("支付时间")||!t.contains("付款方式"))return null;
  String all=String.join("\n",t);if(PaymentParser.has(all,"退款","交易关闭","支付失败","验证码","USD","美元","港币","港元","日元","欧元"))return null;
  int success=-1,states=0;for(int i=0;i<t.size();i++)if(t.get(i).matches("交易成功|自动扣款成功|免密支付成功|扣款成功|付款成功|支付成功")){if(++states>1)return null;if(i>=2&&billExpense(t.get(i-1))>0)success=i;}if(success<2)return null;String money=t.get(success-1);long cents=billExpense(money);if(cents<=0)return null;
  String merchant=t.get(success-2);if(cents<=0||merchant.isEmpty()||merchant.length()>100||merchant.matches("账单详情|全部账单|.*[¥￥].*"))return null;
  Long explicitPaid=field(t,PAID),payment=field(t,"(?:付款金额|支付金额)");if(explicitPaid!=null&&explicitPaid!=cents||payment!=null&&payment!=cents)return null;
  int ti=t.indexOf("支付时间");if(Collections.frequency(t,"支付时间")!=1||ti+1>=t.size())return null;long time=WechatAutoDebit.date(t.get(ti+1));if(time<=0||time>now+60000)return null;
  Long order=field(t,ORDER),discount=field(t,DISCOUNT);if(order!=null&&(order<0||discount!=null&&(discount<0||order-discount!=cents)||discount==null&&order!=cents))return null;
  WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.time=time;r.result=new PaymentParser.Result();r.result.cents=cents;r.result.merchant=merchant;r.result.kind="expense";r.result.category=ExpenseCategory.bill(t,merchant);for(String line:t)if(line.equals("账单分类")||line.startsWith("账单分类：")||line.startsWith("账单分类:"))r.result.categoryExplicit=true;r.result.paidEvidence=true;r.result.confirmed=false;r.result.reason="支付宝账单详情：以交易成功的实际扣款金额为准；历史详情请核对是否已记账";
  r.key="alipay-receipt:bill:"+time+"|"+cents+"|"+merchant;r.evidence.addAll(t);return r;
 }
}

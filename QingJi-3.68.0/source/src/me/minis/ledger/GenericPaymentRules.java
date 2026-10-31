package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** All-app structured payment result rules, independent of package identity. */
public final class GenericPaymentRules {
 public static PaymentParser.Result parse(String pkg,List<String> lines,boolean unsafe){
  if(unsafe||!PaymentApps.allowed(pkg)||lines==null||lines.size()>240)return null;List<String>t=new ArrayList<>();for(String line:lines)if(line!=null)for(String s:line.split("[\\r\\n]+"))if(!AmountText.spaces(s).isEmpty()){if(s.length()>300||t.size()>=240)return null;t.add(AmountText.spaces(s));}
  String all=String.join("\n",t);if(PaymentParser.has(all,PaymentPageRules.BLOCKED)||PaymentParser.rejected(all.replace("优惠券抵扣","抵扣").replace("优惠券优惠","优惠"))||NotificationSemantics.blocked(all)||!NotificationSemantics.ambiguity(all.replace("话费充值","通讯费").replace("流量充值","通讯费")).isEmpty())return null;
  int success=-1;boolean exit=false;for(int i=0;i<t.size();i++){String s=t.get(i);if(PaymentPageRules.has(PaymentPageRules.HISTORY,s)||PaymentPageRules.has(PaymentPageRules.NON_PAYMENT_TITLES,s))return null;if(PaymentPageRules.success(s)){if(success>=0)return null;success=i;}exit|=PaymentPageRules.has(PaymentPageRules.EXIT,s);}
  if(FlowRouting.personal(new MoneyFlow.Frame(pkg,"",t.toArray(new String[0]))))return null;PaymentPageFields.Values fields=PaymentPageFields.read(t);if(fields.invalid)return null;boolean structured=ReceiptEvidence.structured(t,fields);if(success<0&&!structured||success>=0&&!exit&&!structured)return null;Long paid=fields.paid;String rule=success<0?"structured-review":"explicit-paid";
  if(paid==null){rule="success-primary";int i=success+1;if(i<t.size()&&PaymentPageFields.shop(t.get(i)))i++;if(i<t.size()){String s=AmountText.canonical(t.get(i));int end=i;if(s.matches("(?i)¥|￥|人民币|RMB|CNY")&&i+1<t.size())s+=AmountText.canonical(t.get(++end));long n=PaymentPageFields.cash(s);if(n>0&&(s.matches("(?i)(?:[¥￥]|人民币|RMB|CNY).*|[0-9][0-9,]*(?:\\.[0-9]{1,2})?元")||s.matches("[0-9]{1,9}\\.[0-9]{2}")&&end+1<t.size()&&(PaymentPageRules.has(PaymentPageRules.EXIT,t.get(end+1))||PaymentPageFields.shop(t.get(end+1))||PaymentPageFields.value(t.get(end+1),PaymentPageRules.DISCOUNT)!=null||PaymentPageFields.value(t.get(end+1),PaymentPageRules.BALANCE)!=null||PaymentPageRules.has(PaymentPageRules.METHOD,t.get(end+1))))){paid=n;for(int j=i;j<=end;j++)fields.used[j]=true;}}}
  if(paid==null&&fields.order!=null&&fields.discount!=null){paid=fields.order-fields.discount;rule="order-minus-discount";}
  if(paid==null||paid<=0)return null;
  if(fields.order!=null){long expected=fields.order-(fields.discount==null?0:fields.discount);if(expected!=paid)return null;}
  for(int i=0;i<t.size();i++)if(!fields.used[i]){String s=AmountText.canonical(t.get(i));Matcher m=PaymentPageFields.CASH.matcher(s);while(m.find())if(PaymentPageFields.cash(m.group())!=paid)return null;if(s.matches("[+−－-]?[0-9][0-9.,]*[.,][0-9.,]*")&&PaymentPageFields.cash(s)!=paid)return null;}
  PaymentParser.Result r=new PaymentParser.Result();r.cents=paid;r.confirmed=success>=0;r.paidEvidence=r.confirmed;r.merchant=PaymentPageFields.merchant(ExpenseCategory.fields(all));r.category=ExpenseCategory.infer(r.merchant);ExpenseCategory.enrich(r,all);r.reason="通用交易证据 "+PaymentPageRules.VERSION+"/"+rule+(r.confirmed?"：成功状态及金额关系已校验":"：实付、商户及支付方式／交易标识相互支持；完成状态未知，请核对");return r;
 }
}

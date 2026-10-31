package me.minis.ledger;
import java.util.*;
public final class Audit359Test {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static PaymentParser.Result parse(String... a){return GenericPaymentRules.parse("org.any.receipt",Arrays.asList(a),false);}
 public static void main(String[] args){
  String[] amounts={"２８．５０元","￥２８．５０","rmb 28.50","cny\u00a028.50","人民币\u202f28.50","28.50\u00a0元","２８.５０","28.50"};
  for(String a:amounts){PaymentParser.Result r=parse("支付成功","实付金额："+a,"商户：星际补给站２号店","支付方式：零钱");ok(r!=null&&r.confirmed&&r.cents==2850,"paid "+a);ok(r.merchant.equals("星际补给站２号店"),"merchant untouched");r=parse("陌生收据","实付金额："+a,"商户：星际补给站","支付方式：零钱");ok(r!=null&&!r.confirmed&&r.cents==2850,"unknown stays review");ok(NotificationGate.hasMoneyUnit("实付金额："+a),"notification candidate "+a);}
  for(String a:new String[]{"１，２３４．５６元","rmb 1,234.56","￥１,２３４.５６"}){PaymentParser.Result r=parse("支付成功","实付金额："+a,"完成");ok(r!=null&&r.cents==123456,"grouped "+a);}
  for(String a:new String[]{"１２，３４．５６元","２８．５００元","２８..５０元","-２８．５０元","２８ ５０元","0.00元"})ok(parse("支付成功","实付金额："+a,"完成")==null,"invalid stays invalid "+a);
  for(String a:new String[]{"￥９９．００","rmb 99.00","99.00\u00a0元"})ok(parse("支付成功","实付金额：２８．５０元","完成",a)==null,"conflicting extra "+a);
  for(String a:new String[]{"￥２８．５０","rmb\u00a028.50","２８．５０"}){PaymentParser.Result r=parse("支付成功",a,"完成");ok(r!=null&&r.cents==2850,"primary "+a);}
  PaymentParser.Result r=parse("\u00a0支付成功\u00a0","实付金额：","cny","２８．５０","商户：星际补给站","支付方式：零钱");ok(r!=null&&r.confirmed&&r.cents==2850,"split unit");
  r=parse("支付成功","实付金额：２８．５０元","订单金额：３８．５０元","优惠：１０．００元","完成");ok(r!=null&&r.cents==2850,"arithmetic");
  ok(parse("支付成功","实付金额：２８．５０元","订单金额：４０．００元","优惠：１０．００元","完成")==null,"mismatch remains rejected");
  for(String status:new String[]{"待付款","支付失败","订单详情","支付处理中"})ok(parse(status,"实付金额：２８．５０元","商户：星际补给站","支付方式：零钱")==null,"status "+status);
  r=PaymentParser.parseNotification("org.any.receipt","交易回执","陌生收据\n实付金额：２８．５０\n商户：星际补给站\n支付方式：零钱");ok(r!=null&&r.cents==2850&&!r.confirmed,"full notification routing");
  for(int i=1;i<=300;i++){String a=String.format(java.util.Locale.ROOT,"%d.%02d",i,i%100);StringBuilder b=new StringBuilder();for(char c:a.toCharArray())b.append(c=='.'?'．':(char)('０'+c-'0'));r=parse("支付成功","实付金额：￥"+b,"完成");ok(r!=null&&r.cents==i*100+i%100,"format vector "+i);}
  System.out.println("PASS "+checks+" audit359 recognition assertions");
 }
}

package me.minis.ledger;
import java.util.*;
public final class Audit360Test {
 static int n;static void ok(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
 static PaymentParser.Result parse(String... a){return GenericPaymentRules.parse("org.any.pay",Arrays.asList(a),false);}
 public static void main(String[] args){
 for(String unit:new String[]{"人民币","RMB","rmb","CNY","cny"})for(String amount:new String[]{"28.50","２８．５０","\u00a028.50"}){
 String paid="实付金额"+unit+amount;PaymentParser.Result r=parse("支付成功",paid,"商户：晨光文具２号店","支付方式：零钱");ok(r!=null&&r.cents==2850&&r.confirmed,"inline "+paid);ok(r.merchant.equals("晨光文具２号店")&&r.category.equals("购物"),"merchant and category preserved");ok(NotificationGate.hasMoneyUnit(paid),"candidate");r=parse("未知收据",paid,"商户：晨光文具２号店","支付方式：零钱");ok(r!=null&&!r.confirmed,"unknown review");ok(parse("支付成功",paid,"订单金额"+unit+"40.00","优惠"+unit+"10.00","完成")==null,"arithmetic mismatch");r=parse("支付成功",paid,"订单金额"+unit+"38.50","优惠"+unit+"10.00","完成");ok(r!=null&&r.cents==2850,"valid arithmetic");}
 for(String s:new String[]{"实付金额RMBX28.50","实付金额CNY28..50","实付金额人民币28.500","实付金额rmb-28.50","实付金额RMB0.00"})ok(parse("支付成功",s,"完成")==null,"malformed "+s);
 ok(PaymentPageFields.value("商户RMB商店",PaymentPageRules.MERCHANT)==null,"no merchant label relaxation");
 System.out.println("PASS "+n+" audit360 recognition assertions");
 }
}

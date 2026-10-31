package me.minis.ledger;
import java.util.*;
public final class Evidence351Test {
 @org.junit.Test public void replay(){main(new String[0]);}
 static int checks;
 static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
 static PaymentParser.Result parse(String... text){return GenericPaymentRules.parse("org.unlisted.anyapp",Arrays.asList(text),false);}
 public static void main(String[] args){
  String[] receipt={"陌生的收据标题","实付金额：28.50元","商户：星际补给站","支付方式：零钱"};
  PaymentParser.Result r=parse(receipt);check(r!=null&&r.cents==2850&&!r.confirmed&&!r.paidEvidence,"unknown title structured receipt -> review");check(r.merchant.equals("星际补给站"),"merchant not in vocabulary retained");
  String[] paid=receipt.clone();paid[0]="支付成功";r=parse(paid);check(r!=null&&r.confirmed,"no exit label needed with structured evidence");
  r=parse("支付成功","实付金额：28.50元","完成");check(r!=null&&r.confirmed,"legacy success/amount/exit kept");
  check(parse("支付成功","实付金额：28.50元")==null,"weak two-field page remains insufficient");
  check(parse("未知","商品金额：28.50元","商户：星际补给站","支付方式：零钱")==null,"price is not actual payment");
  for(String status:new String[]{"待付款","支付失败","支付处理中","尚未付款","交易关闭","订单已取消","退款成功","收款成功","验证码","聊天记录","历史订单","订单详情"}){String[] a=receipt.clone();a[0]=status;check(parse(a)==null,"blocked "+status);}
  check(parse("实付金额：28.50元","商户：星际补给站")==null,"method or transaction id required");
  check(parse("实付金额：28.50元","支付方式：零钱")==null,"merchant required");
  check(parse("实付金额：28.50元","商户：星际补给站","支付方式：","付款方式")==null,"empty method is not evidence");
  check(parse("实付金额：28.50元","商户：星际补给站","交易号：ABC123456789012")!=null,"transaction id substitutes method");
  check(parse("实付金额：28.50元","商户：星际补给站","支付方式：零钱","¥99.00")==null,"unbound conflict rejected");
  check(parse("实付金额：28.50元","商户：星际补给站","支付方式：零钱","订单金额：40.00元","优惠：10.00元")==null,"bad arithmetic rejected");
  r=parse("实付金额：28.50元","商户：星际补给站","支付方式：零钱","订单金额：38.50元","优惠：10.00元");check(r!=null&&!r.confirmed,"valid arithmetic remains review without completion");
  check(GenericPaymentRules.parse("org.unknown",Arrays.asList(receipt),true)==null,"editable/unsafe blocked");
  for(int i=0;i<200;i++){r=GenericPaymentRules.parse("org.synthetic.app"+i,Arrays.asList(receipt),false);check(r!=null&&!r.confirmed,"arbitrary package "+i);}
  List<PaymentSnapshot.Node> nodes=new ArrayList<>();int id=0;for(String t:receipt)nodes.add(new PaymentSnapshot.Node(++id,0,1,0,id*60,300,id*60+40,"","TextView",t,"",false));
  r=ScreenProfile.SEMANTIC.parse(new PaymentSnapshot("org.new",1,1,0,0,400,800,nodes,false,false));check(r!=null&&!r.confirmed&&r.reason.contains("完成状态未知"),"snapshot review explanation");
  r=parse("陌生标题","实付金额：28.50","商户：星际补给站","支付方式：某某银行卡");check(r!=null&&!r.confirmed,"arbitrary bank method");
  String raw="陌生标题\n实付金额：28.50\n商户：星际补给站\n支付方式：某某银行卡";check(NotificationGate.hasMoneyUnit(raw),"unitless paid field reaches parser");r=PaymentParser.parseNotification("org.arbitrary.app","交易回执",raw);check(r!=null&&r.cents==2850&&!r.confirmed,"unitless structured notification review");check(PaymentParser.parseNotification("com.tencent.mm","普通聊天",raw)==null,"chat sender still excluded");
  check(!NotificationGate.hasMoneyUnit("优惠价 28.50"),"bare price not candidate");
  System.out.println("PASS "+checks+" evidence351 checks");
 }
}

package me.minis.ledger;

import java.util.*;

/** Synthetic snapshot replay: these fixtures make no claim about a particular live app version. */
public final class RecognitionTest {
 @org.junit.Test public void replay(){main(new String[0]);}
 private static int checks;
 private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 private static PaymentSnapshot.Node n(int id,int parent,String text,int x,int y){return new PaymentSnapshot.Node(id,parent,7,x,y,x+120,y+40,"","android.widget.TextView",text,"",false);}
 private static PaymentSnapshot snapshot(List<PaymentSnapshot.Node> nodes,boolean cut,boolean edit){return new PaymentSnapshot("com.example.payment",120,7,0,0,1080,2000,nodes,cut,edit);}
 private static List<PaymentSnapshot.Node> paid(){return new ArrayList<>(Arrays.asList(
  n(1,0,"支付成功",100,100),n(2,0,"完成",100,800),
  // Traversal returns the amount before its label, and another row in between.
  n(3,10,"28.50元",400,250),n(4,11,"优惠",100,350),n(5,11,"10.00元",400,350),n(6,10,"实付金额",100,250),
  n(7,12,"商户名称",100,450),n(8,12,"街角咖啡店",400,450)));
 }
 public static void main(String[] args){
  ScreenProfile rule=ScreenProfile.SEMANTIC;PaymentParser.Result result=rule.parse(snapshot(paid(),false,false));
  check(result!=null&&result.cents==2850&&result.confirmed,"bind reordered paid amount");
  check("街角咖啡店".equals(result.merchant),"bind merchant independently");
  List<PaymentSnapshot.Node> semantic=paid();semantic.set(5,new PaymentSnapshot.Node(6,10,7,100,250,220,290,"","android.view.View","","实付金额",false));
  check(rule.parse(snapshot(semantic,false,false)).cents==2850,"Compose content description");
  check(rule.parse(snapshot(paid(),true,false))==null,"truncated snapshot rejected");
  check(rule.parse(snapshot(paid(),false,true))==null,"input or password page rejected");
  List<PaymentSnapshot.Node> bad=paid();bad.add(n(9,0,"支付失败",100,600));check(rule.parse(snapshot(bad,false,false))==null,"failure overrides success");
  bad=paid();bad.add(n(9,0,"历史订单",100,600));check(rule.parse(snapshot(bad,false,false))==null,"historical page not instant payment");
  bad=paid();bad.add(n(9,0,"¥99.00",100,600));check(rule.parse(snapshot(bad,false,false))==null,"conflicting amount never guessed");
  bad=paid();bad.add(n(9,10,"40.00元",600,250));check(rule.parse(snapshot(bad,false,false))==null,"ambiguous label siblings rejected");
  bad=paid();bad.remove(1);check(rule.parse(snapshot(bad,false,false))==null,"second page anchor required");
  check(!new ScreenProfile("old","com.example.payment",1,119).matches(snapshot(paid(),false,false)),"app version range");
  check(new ScreenProfile("current","com.example.payment",120,125).matches(snapshot(paid(),false,false)),"matching app version");
  check(!new ScreenProfile("other","other.package",0,0).matches(snapshot(paid(),false,false)),"package boundary");
  check(PaymentParser.parseNotification("com.example.bank","银行","消费金额28.50元，余额500.00元").cents==2850,"balance is not payment");
  check(PaymentParser.parseNotification("com.example.shop","活动","满100减20，商品¥39.90")==null,"promotion negative");
  check(PaymentParser.parseNotification("com.example.bank","银行","退款申请 20.00元")==null,"pending refund negative");
  check(PaymentParser.parseNotification("com.tencent.mm","某位联系人","支付成功28.50元")==null,"chat is not trusted service");
  check(PaymentParser.parseNotification("com.example.bank","银行","退款到账20.00元").kind.equals("refund"),"refund direction");
  int[] pixels=new int[VisualPattern.SIZE];Arrays.fill(pixels,0xffffffff);check(VisualPattern.encode(pixels).isEmpty(),"blank image is not a template");
  for(int y=3;y<13;y++)for(int x=6;x<42;x++)if((x+y)%5<2)pixels[y*VisualPattern.WIDTH+x]=0xff111111;
  String feature=VisualPattern.encode(pixels);check(!feature.isEmpty()&&VisualPattern.matches(feature,feature),"learned pattern matches");
  check(!VisualPattern.matches(feature,feature.replace('1','0')),"blank cannot match learned title");
  check(!VisualPattern.matches(feature,feature.replace('1','x').replace('0','1').replace('x','0')),"theme mismatch requires relearning");
  ScanTiming timing=new ScanTiming();timing.started(100);check(timing.eventDelay(101,false)>0,"event burst coalescing");check(timing.request(100,80)&&!timing.request(101,90),"new events do not postpone a pending scan");
  System.out.println("PASS: "+checks+" recognition checks (synthetic snapshots, notifications, visual features)");
 }
}

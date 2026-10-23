package me.minis.ledger;
import java.util.*;
/** Trusted service navigation + isolated cards; never ordinary chat. */
final class WechatServiceCards {
 private static boolean exact(List<String> t,String s){return t.contains(s);}
 private static boolean usage(String s){return s.matches("使用.{1,45}支付");}
 private static boolean heading(String s){return WechatAutoDebit.marker(s);}
 private static long amount(List<String> t,int start,int end){Set<Long> values=new HashSet<>();for(int i=start;i<end;i++){String s=t.get(i);if(s.equals("汇总")||s.startsWith("今日第")||s.equals("交易状态")||s.equals("备注")||s.equals("收款方"))break;long n=MoneyFlow.amount(s);if(n>0)values.add(n);}return values.size()==1?values.iterator().next():0;}
 private static void finish(WechatReceiptParser.Receipt r,long now,boolean proof){r.result.confirmed=proof&&r.time>0&&now>=r.time&&now-r.time<=180000;r.result.reason=r.result.confirmed?"":!proof?"微信服务支付凭证未读到明确成功状态，请核对是否付款成功":"微信历史服务凭证，请核对交易日期";r.result.category=PaymentParser.category(r.result.merchant);r.key="wechat-receipt:"+r.time+"|"+r.result.kind+"|"+r.result.cents+"|"+r.result.merchant;}
 static List<WechatReceiptParser.Receipt> parse(List<String> t,long now){List<WechatReceiptParser.Receipt> out=new ArrayList<>();Set<String> keys=new HashSet<>();
  for(int i=0;i<t.size();i++){
   String s=t.get(i);boolean income=s.equals("收款到账通知");if(!income&&!usage(s))continue;
   if(!income){
    // Only the immediate header and its own immediately preceding clock belong to this card.
    int start=i;if(i>0&&(WechatPaymentCard.merchant(t.get(i-1))||t.get(i-1).equals("微信支付凭证")))start=i-1;
    if(start>0&&WechatReceiptParser.clock(t.get(start-1),now)>0)start--;
    int stop=t.size();for(int j=i+1;j<t.size();j++)if(heading(t.get(j))||WechatReceiptParser.clock(t.get(j),now)>0){stop=j;if(usage(t.get(j))&&j>i+1&&WechatPaymentCard.merchant(t.get(j-1)))stop=j-1;break;}
    WechatReceiptParser.Receipt payment=WechatPaymentCard.parse(t.subList(start,stop),now);if(payment!=null&&keys.add(payment.key))out.add(payment);continue;
   }
   int end=t.size();for(int j=i+1;j<t.size();j++)if(heading(t.get(j))||usage(t.get(j))||WechatReceiptParser.clock(t.get(j),now)>0){end=j;break;}
   List<String> card=t.subList(i,end);String joined=String.join("\n",card);if(PaymentParser.has(joined,"支付失败","付款失败","交易关闭","已退款","未到账","未存入零钱","收款失败"))continue;
   boolean proof=income?card.stream().anyMatch(v->v.matches("收款成功[，,]已存入零钱[。.]?(?:点击可查看详情)?"))||exact(card,"收款成功"):card.stream().anyMatch(v->v.matches("支付成功[，,]对方已收款[。.]?"))||exact(card,"支付成功");
   boolean detail=exact(card,"账单详情")||exact(card,"查看账单详情")||exact(card,"查看交易详情");if(!proof&&(!detail||income))continue;
   int primaryEnd=end;for(int j=i+1;j<end;j++)if(t.get(j).equals("账单详情")||t.get(j).equals("查看账单详情")||t.get(j).equals("查看交易详情")){primaryEnd=j;break;}
   long n=amount(t,i,primaryEnd);if(n<=0)continue;
   WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.result=new PaymentParser.Result();r.result.cents=n;r.result.kind=income?"income":"expense";r.result.merchant=income?"微信收款":"";
   int timeIndex=-1;for(int j=i-1;j>=Math.max(0,i-12);j--){long at=WechatReceiptParser.clock(t.get(j),now);if(at>0){r.time=at;timeIndex=j;break;}if(usage(t.get(j))||t.get(j).equals("收款到账通知"))break;}
   if(!income){for(int j=i+1;j+1<end;j++)if(t.get(j).equals("收款方")){String name=t.get(j+1);if(name.length()<=100&&MoneyFlow.amount(name)==0&&!name.matches(".*(?:详情|交易状态|成功|零钱|银行).*"))r.result.merchant=name;break;}
    if(r.result.merchant.isEmpty())for(int j=i-1;j>timeIndex&&j>=Math.max(0,i-5);j--){String name=t.get(j);if(name.matches(".*(?:详情|留言|订单|扣费服务|物流|凭证).*"))continue;if(MoneyFlow.amount(name)==0&&!name.equals("微信支付")&&name.length()<=100){r.result.merchant=name;break;}}
   }
   // Missing time is unsafe for stable multi-card identity: keep legacy fallback instead.
   if(r.time<=0)continue;r.evidence.addAll(card);finish(r,now,proof);if(keys.add(r.key))out.add(r);
  }return out;
 }
}

package me.minis.ledger;
import java.util.*;
/** One isolated payment card. Neither scan time nor neighbouring cards supply identity fields. */
final class WechatPaymentCard {
 static boolean usage(String s){return s!=null&&s.matches("使用.{1,45}支付");}
 static boolean detail(String s){return s.matches("账单详情|查看账单详情|查看交易详情");}
 static boolean merchant(String s){return s!=null&&!s.isEmpty()&&s.length()<=100&&MoneyFlow.amount(s)==0&&!s.matches("[¥￥]|[0-9:：\\s年月日昨天今上下午夜晚早晨凌]+|微信支付凭证|交易状态|当前状态|收款方|商户名称|收款金额|支付金额|实付金额|留言|备注|汇总|交易详情|收款小账本|收款成功.*|支付成功.*|付款成功.*|.*按钮")&&!detail(s)&&!usage(s)&&!WechatAutoDebit.marker(s)&&WechatServiceGate.label(s).isEmpty();}
 static boolean failed(List<String> t){return PaymentParser.has(String.join("\n",t),"支付失败","付款失败","交易关闭","已退款","退款中","未支付","未付款","待支付","待付款","待确认","未到账","收款失败","支付密码","验证码");}
 // WeChat exposes the same body as a compact parent description and split children.
 // Normalize only this exact body grammar, preserving conflicting amounts for rejection.
 private static List<String> body(List<String> input){
  java.util.regex.Pattern p=java.util.regex.Pattern.compile("^(使用[^¥￥\\r\\n]{1,45}支付)[¥￥]\\s*([0-9]{1,9}(?:\\.[0-9]{1,2})?)(账单详情|查看账单详情|查看交易详情)?$");
  int standalone=0;String method="";boolean summary=false;
  for(String s:input){if(usage(s))standalone++;java.util.regex.Matcher m=p.matcher(s);if(m.matches()){summary=true;if(!method.isEmpty()&&!method.equals(m.group(1)))return input;method=m.group(1);}}
  if(!summary||standalone>1)return input;
  for(String s:input)if(usage(s)&&!s.equals(method))return input;
  List<String> out=new ArrayList<>();boolean emitted=false;
  for(String s:input){java.util.regex.Matcher m=p.matcher(s);if(m.matches()){if(!emitted){out.add(method);emitted=true;}out.add("¥"+m.group(2));if(m.group(3)!=null)out.add(m.group(3));}else if(usage(s)){if(!emitted){out.add(s);emitted=true;}}else out.add(s);}
  return out;
 }
 static WechatReceiptParser.Receipt parse(List<String> t,long now){
  if(t==null||t.size()>220||failed(t))return null;t=body(t);
  int at=-1;for(int i=0;i<t.size();i++)if(usage(t.get(i))){if(at>=0)return null;at=i;}if(at<0)return null;
  boolean proof=false,footer=false;Set<Long> amounts=new HashSet<>();String explicit="";
  for(int i=at+1;i<t.size();i++){
   String s=t.get(i);if(WechatAutoDebit.marker(s))return null;
   proof|=s.matches("支付成功(?:[，,]对方已收款)?[。.]?");footer|=detail(s);
   long n=MoneyFlow.amount(s);if(n>0)amounts.add(n);
   if(s.equals("收款方")||s.equals("商户名称")){if(i+1>=t.size()||!merchant(t.get(i+1)))return null;String name=t.get(i+1);if(!explicit.isEmpty()&&!explicit.equals(name))return null;explicit=name;}
  }
  if(amounts.size()!=1||(!proof&&!footer))return null;
  String name="";long time=0;for(int i=0;i<at;i++){
   String s=t.get(i);long clock=WechatReceiptParser.clock(s,now);if(clock>0){if(time>0&&time!=clock)return null;time=clock;continue;}
   if(!WechatServiceGate.label(s).isEmpty()||s.equals("微信支付凭证"))continue;
   if(!merchant(s)||!name.isEmpty()&&!name.equals(s))return null;name=s;
  }
  if(!explicit.isEmpty()){if(!name.isEmpty()&&!name.equals(explicit))return null;name=explicit;}
  if(name.isEmpty())return null;
  WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.result=new PaymentParser.Result();r.result.cents=amounts.iterator().next();r.result.kind="expense";r.result.merchant=name;r.result.category=PaymentParser.category(name);r.time=time;
  r.result.confirmed=proof&&time>0&&now>=time&&now-time<=180000;
  r.result.reason=r.result.confirmed?"":time==0?"微信支付付款卡没有独立交易时间，记录时间仅为识别时间；请核对日期及是否已记账，同商户同金额无时间卡可能合并":!proof?"微信服务支付凭证未读到明确成功状态，请核对是否付款成功":"微信历史服务凭证，请核对交易日期";
  r.key="wechat-receipt:"+time+"|expense|"+r.result.cents+"|"+name;r.evidence.addAll(t);return r;
 }
}

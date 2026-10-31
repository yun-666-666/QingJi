package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Home recent-message preview: explicit successful debit only; relative clocks remain review. */
public final class AlipayRecentMessage {
 private static final Pattern BODY=Pattern.compile("^(?:(.{1,100}?)\\s+)?(?:自动扣款成功|免密支付成功|扣款成功|付款成功|支付成功)\\s*[¥￥]\\s*([0-9]{1,9}(?:\\.[0-9]{1,2})?)$");
 private static final Pattern AGE=Pattern.compile("([0-9]{1,3})(分钟|小时)前");
 public static boolean marker(String s){return s!=null&&(BODY.matcher(s.trim()).matches()||s.trim().matches("自动扣款成功|免密支付成功|扣款成功|付款成功|支付成功"));}
 public static WechatReceiptParser.Receipt parse(List<String> input,long now){
  if(input==null||input.size()>12)return null;List<String> t=AlipayPaymentCards.clean(input);String name="",age="",body="";int count=0;
  for(String s:t){if(s.equals("刚刚")||AGE.matcher(s).matches()){if(!age.isEmpty()&&!age.equals(s))return null;age=s;continue;}Matcher m=BODY.matcher(s);if(m.matches()){if(++count>1)return null;body=s;if(m.group(1)!=null){String embedded=m.group(1).trim();if(!name.isEmpty()&&!name.equals(embedded))return null;name=embedded;}continue;}
   if(s.matches("自动扣款成功|免密支付成功|扣款成功|付款成功|支付成功")||s.matches("[¥￥]\\s*[0-9]{1,9}(?:\\.[0-9]{1,2})?")){body+=(body.isEmpty()?"":" ")+s;continue;}
   if(s.isEmpty()||s.length()>100||s.matches(".*(?:最近消息|积分|能量|蚂蚁森林|统计|本月|活动|红包|查看|详情|失败|退款|支付消息|服务消息|扫一扫|收付款).*"))return null;
   if(!name.isEmpty()&&!name.equals(s))return null;name=s;
  }
  Matcher m=BODY.matcher(body);if(!m.matches()||name.isEmpty()||age.isEmpty()||PaymentParser.has(name,"成功","支付密码","验证码"))return null;
  long cents=MoneyFlow.amount(m.group(2));if(cents<=0)return null;long span=60000,offset=0;Matcher am=AGE.matcher(age);if(am.matches()){int n=Integer.parseInt(am.group(1));if(n<1||n>168)return null;span=am.group(2).equals("分钟")?60000:3600000;offset=n*span;}
  long estimated=(now-offset)/60000*60000;
  WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.time=estimated;r.result=new PaymentParser.Result();r.result.kind="expense";r.result.cents=cents;r.result.merchant=name;r.result.category=PaymentParser.category(name);r.result.confirmed=false;
  r.result.reason="支付宝首页最近消息：已显示扣款成功，但“"+age+"”仅为相对时间，所列时间为估算；请打开支付消息核对精确时间及是否已记账。短时间同商户同金额摘要可能合并，完整凭证仍独立核对";
  r.key="alipay-preview:"+(span==3600000?"h:":"m:")+digest(name+"|"+cents+"|"+estimated);r.evidence.addAll(t);return r;
 }
 private static String digest(String s){try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));return Hex.encode(b);}catch(Exception e){throw new IllegalStateException(e);}}
}

package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Only trusted WeChat payment service UI or a structured bill detail, never ordinary chat. */
public final class WechatReceiptParser {
 public static final class Receipt {public PaymentParser.Result result;public long time;public String id="",key="";public List<String> evidence=new ArrayList<>();}
 private static boolean exact(List<String> t,String key){for(String s:t)if(s.trim().equals(key))return true;return false;}
 public static boolean trustedService(String pkg,List<String> t){return "com.tencent.mm".equals(pkg)&&t!=null&&t.size()<=240&&WechatServiceGate.navigationTrusted(t)&&!WechatServiceGate.blocked(t);}
 public static List<Receipt> parseAll(String pkg,List<String> input,long now){if(input==null||input.size()>240)return Collections.emptyList();List<String> clean=WechatServiceGate.clean(input);if(trustedService(pkg,clean)){List<Receipt> cards=WechatServiceCards.parse(clean,now);cards.addAll(WechatAutoDebit.cards(clean,now));if(!cards.isEmpty())return cards;}Receipt legacy=parse(pkg,input,now);return legacy==null?Collections.emptyList():Collections.singletonList(legacy);}
 static long clock(String s,long now){
  String clean=s.replace(" ","");Matcher m=Pattern.compile("(?:(昨天|今天))?(凌晨|早上|上午|中午|下午|晚上)?([0-9]{1,2})[:：]([0-9]{2})").matcher(clean);
  if(!m.matches())return 0;try{int h=Integer.parseInt(m.group(3)),min=Integer.parseInt(m.group(4));String part=m.group(2);if(part==null&&m.group(1)==null)return 0;
   if(("下午".equals(part)||"晚上".equals(part)||"中午".equals(part))&&h<12)h+=12;if("凌晨".equals(part)&&h==12)h=0;
   if(h>23||min>59)return 0;Calendar c=Calendar.getInstance();c.setTimeInMillis(now);if("昨天".equals(m.group(1)))c.add(Calendar.DAY_OF_MONTH,-1);c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,min);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);long t=c.getTimeInMillis();return t<=now+60000?t:0;
  }catch(Exception e){return 0;}
 }
 public static Receipt parse(String pkg,List<String> input,long now){
  if(!"com.tencent.mm".equals(pkg)||input==null||input.size()>180)return null;List<String> t=new ArrayList<>();for(String s:input)if(s!=null&&!s.trim().isEmpty())t.add(s.trim().replace("￥ ","￥").replace("¥ ","¥"));
  String joined=String.join("\n",t);if(PaymentParser.has(joined,"支付密码","输入密码","验证码","按住说话","发送消息"))return null;
  boolean service=trustedService(pkg,t);
  boolean bill=(exact(t,"账单详情")||exact(t,"交易详情"))&&(exact(t,"交易单号")||exact(t,"商户单号"))&&(exact(t,"交易时间")||exact(t,"支付时间")||exact(t,"收款时间"));
  boolean receiveService=(exact(t,"微信收款助手")||exact(t,"收款助手"))&&(exact(t,"收款成功")||exact(t,"收款到账"));
  if(!service&&!bill&&!receiveService)return null;
  Receipt best=null;
  if(service)for(Receipt r:WechatServiceCards.parse(WechatServiceGate.clean(t),now))if(best==null||r.time>best.time)best=r;
  if(receiveService){Receipt r=new Receipt();r.result=new PaymentParser.Result();Set<Long> amounts=new HashSet<>();for(String s:t){long v=MoneyFlow.amount(s);if(v>0)amounts.add(v);}long labelled=MoneyFlow.labelAmount(t,"收款金额");if(labelled>0){amounts.clear();amounts.add(labelled);}if(amounts.size()==1){r.result.cents=amounts.iterator().next();r.result.kind="income";for(String s:t){long v=clock(s,now);if(v>r.time)r.time=v;}r.result.confirmed=r.time>0&&now>=r.time&&now-r.time<=180000;r.result.reason=r.result.confirmed?"":"微信收款服务凭证，未取得近期交易时间，请核对";r.result.merchant="微信收款";r.evidence.addAll(t);best=r;}}
  if(bill){
   Receipt r=new Receipt();r.result=new PaymentParser.Result();boolean expense=PaymentParser.has(joined,"支付成功","已支付"),income=PaymentParser.has(joined,"收款成功","收款到账","已收款");if(expense==income)return best;
   Set<Long> amounts=new HashSet<>();for(String s:t){String v=s.startsWith("-")||s.startsWith("+")?s.substring(1):s;long n=MoneyFlow.amount(v);if(n>0&&s.matches("[-+][¥￥]?[0-9]+\\.[0-9]{2}"))amounts.add(n);}
   String label=income?"收款金额":"实付金额";long labelled=MoneyFlow.labelAmount(t,label);if(labelled>0)amounts.add(labelled);if(amounts.size()!=1)return best;
   r.result.cents=amounts.iterator().next();r.result.kind=income?"income":"expense";r.result.confirmed=false;r.result.reason="打开微信账单详情，可能为历史交易，请核对日期";
   MoneyFlow.Frame f=new MoneyFlow.Frame(pkg,"WechatBillUI");f.text.addAll(t);r.id=MoneyFlow.transactionId(f);r.evidence.addAll(t);
   for(int i=0;i<t.size()-1;i++)if(t.get(i).matches("交易时间|支付时间|收款时间")){r.time=WechatAutoDebit.date(t.get(i+1));}
   if(best==null)best=r;
  }
  if(best!=null){best.result.category=PaymentParser.category(best.result.merchant);best.key="wechat-receipt:"+(best.id.isEmpty()?best.time+"|"+best.result.kind+"|"+best.result.cents+"|"+best.result.merchant:best.id);}
  return best;
 }
}

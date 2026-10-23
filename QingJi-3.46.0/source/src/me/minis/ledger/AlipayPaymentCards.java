package me.minis.ledger;
import java.util.*;
import java.util.regex.*;
/** Alipay payment-message receipts, isolated before parsing; never parse a whole chat as a payment. */
public final class AlipayPaymentCards {
 public static final String PKG="com.eg.android.AlipayGphone";
 private static final Pattern CLOCK=Pattern.compile("(?:(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日|今天|昨天)?\\s*(凌晨|早上|上午|中午|下午|晚上)?\\s*(\\d{1,2})[:：](\\d{2})(?::(\\d{2}))?");
 public static boolean trusted(String pkg,List<String> nav){return PKG.equals(pkg)&&nav.contains("支付消息")&&!nav.contains("发送")&&!nav.contains("发送消息")&&!nav.contains("按住说话");}
 static boolean success(String s){return s.matches("自动扣款成功|免密支付成功|扣款成功|付款成功|支付成功");}
 static boolean timestamp(String s){return CLOCK.matcher(s.trim()).matches()||s.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(?::\\d{2})?");}
 static long clock(String s,long now){
  if(s.matches("\\d{4}-.*"))return WechatAutoDebit.date(s.length()==16?s+":00":s);
  Matcher m=CLOCK.matcher(s.trim());if(!m.matches())return 0;
  try{Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.setLenient(false);int h=Integer.parseInt(m.group(5)),min=Integer.parseInt(m.group(6));String part=m.group(4);
   if(part!=null){if(h<1||h>12)return 0;if((part.equals("下午")||part.equals("晚上")||part.equals("中午"))&&h<12)h+=12;if((part.equals("凌晨")||part.equals("上午"))&&h==12)h=0;}
   if(h>23||min>59)return 0;if(m.group(2)!=null){int year=m.group(1)==null?c.get(Calendar.YEAR):Integer.parseInt(m.group(1));c.set(Calendar.YEAR,year);c.set(Calendar.MONTH,Integer.parseInt(m.group(2))-1);c.set(Calendar.DAY_OF_MONTH,Integer.parseInt(m.group(3)));}else if(s.startsWith("昨天"))c.add(Calendar.DAY_OF_MONTH,-1);
   c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,min);c.set(Calendar.SECOND,m.group(7)==null?0:Integer.parseInt(m.group(7)));c.set(Calendar.MILLISECOND,0);long t=c.getTimeInMillis();
   if(t>now+60000&&m.group(2)!=null&&m.group(1)==null){c.add(Calendar.YEAR,-1);t=c.getTimeInMillis();}return t<=now+60000?t:0;
  }catch(Exception e){return 0;}
 }
 static List<String> clean(List<String> input){List<String> out=new ArrayList<>();for(String raw:input){if(raw==null)continue;for(String line:raw.split("[\\r\\n]+")){String s=line.trim();if(!s.isEmpty()&&(out.isEmpty()||!out.get(out.size()-1).equals(s)))out.add(s);}}return out;}
 private static String field(List<String> t,String label){String value="";for(int i=0;i<t.size();i++){String s=t.get(i),v="";if(s.equals(label)&&i+1<t.size())v=t.get(i+1);else if(s.startsWith(label+"：")||s.startsWith(label+":")||s.startsWith(label+" "))v=s.substring(label.length()+1).trim();if(!v.isEmpty()){if(!value.isEmpty()&&!value.equals(v))return "";value=v;}}return value;}
 static boolean merchant(String s){return !s.isEmpty()&&s.length()<=100&&!timestamp(s)&&MoneyFlow.amount(s)==0&&!s.matches("[¥￥]|支付消息|服务消息|自动扣款|查看详情|付款方式|扣款服务|返回|.*按钮|.*统计.*|本月.*|.*积分.*")&&!success(s);}
 public static WechatReceiptParser.Receipt parseCard(List<String> input,long now){
  if(input==null||input.size()>180)return null;List<String> t=clean(input);String all=String.join("\n",t);
  if(PaymentParser.has(all,"扣款失败","支付失败","待扣款","未支付","退款","交易关闭","验证码","支付密码","美元","港币","港元","日元","欧元","USD","HKD","JPY","EUR"))return null;
  int title=-1,footer=-1;for(int i=0;i<t.size();i++){if(success(t.get(i))){if(title>=0)return null;title=i;}if(title>=0&&footer<0&&t.get(i).equals("查看详情"))footer=i;}
  if(title<0||footer<=title)return null;
  String service=field(t.subList(footer+1,t.size()),"扣款服务"),method=field(t.subList(footer+1,t.size()),"付款方式");if(service.isEmpty()&&!t.get(title).matches("付款成功|支付成功")||method.isEmpty()||service.length()>150||method.length()>80||service.equals("付款方式")||method.equals("扣款服务"))return null;
  Set<Long> amounts=new HashSet<>();for(int i=title+1;i<footer;i++){String s=t.get(i);if(s.matches("[¥￥]"))continue;long n=MoneyFlow.amount(s);if(n<=0||!s.matches("[¥￥]?\\s*[0-9]{1,9}(?:\\.[0-9]{1,2})?(?:元)?"))return null;amounts.add(n);}if(amounts.size()!=1)return null;
  // Header belongs to this card. Stop at page-level controls; never borrow a previous card's clock.
  String name="";long time=0;boolean sawTime=false;
  for(int i=title-1;i>=Math.max(0,title-3);i--){String s=t.get(i);if(timestamp(s)){long at=clock(s,now);if(at==0||sawTime&&time!=at)return null;time=at;sawTime=true;continue;}if(merchant(s)&&name.isEmpty()){name=s;continue;}break;}
  if(name.isEmpty())return null;
  WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.time=time;r.result=new PaymentParser.Result();r.result.cents=amounts.iterator().next();r.result.kind="expense";r.result.merchant=name;r.result.category=PaymentParser.category(name);ExpenseCategory.enrich(r.result,all);
  r.result.confirmed=time>0&&now>=time&&now-time<=180000;r.result.reason=r.result.confirmed?"":time==0?"支付宝扣款成功卡无独立交易时间，记录时间仅为识别时间；请核对日期，同商户同金额无时间卡可能合并":"支付宝历史扣款成功消息，请核对交易日期及是否已记账";
  r.key="alipay-receipt:"+digest(time+"|expense|"+r.result.cents+"|"+name+"|"+service+"|"+method);r.evidence.addAll(t);return r;
 }
 private static String digest(String s){try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));return Hex.encode(b);}catch(Exception e){throw new IllegalStateException(e);}}
}

package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Completed deferred debit only. Order dates never become payment dates. */
public final class WechatAutoDebit {
 public static boolean completed(String s){return s!=null&&s.replaceAll("[\\s，,。]","").contains("先用后付订单已完成已自动支付");}
 public static boolean marker(String s){return completed(s)||s.equals("你有一笔收款入账")||s.equals("收款到账通知")||s.equals("微信支付凭证")||s.matches("使用.{1,45}支付");}
 private static boolean failed(String s){return PaymentParser.has(s,"未支付","未付款","尚未","即将","预计","待扣款","扣款失败","支付失败","自动确认收货并付款","待确认收货","申请售后","取消订单","已退款");}
 public static PaymentParser.Result notification(String source,String title,String raw){if(!"com.tencent.mm".equals(source)||title==null||!title.trim().matches("微信支付(?:[:：\\s（(].*)?"))return null;if(raw==null)return null;
  // Exact compact service notification only, not a conversation-list row or arbitrary chat.
  String compact=raw.replaceFirst("^微信支付[：:\\s]*", "").trim();
  if(compact.matches("已扣费\\s*[¥￥]\\s*[0-9]{1,9}(?:\\.[0-9]{1,2})?[。.]?")){long cents=MoneyFlow.amount(compact.replaceFirst("^已扣费\\s*", "").replaceFirst("[。.]$", ""));if(cents>0){PaymentParser.Result shortResult=new PaymentParser.Result();shortResult.cents=cents;shortResult.confirmed=true;shortResult.merchant="微信自动扣费";return shortResult;}}
  if(!completed(raw)||failed(raw))return null;PaymentParser.Result r=PaymentParser.parse(raw);if(r==null)return null;
  if(PaymentParser.has(raw,"美元","港元","港币","日元","欧元","USD","HKD","JPY","EUR"))return r;
  if(PaymentParser.has(raw,"收款","入账","收入","退款")){r.confirmed=false;r.reason="自动扣款通知混有其他收支线索，请核对";return r;}
  // Existing parser retains multi-amount ambiguity; never overwrite it with a guessed debit.
  if(r.reason.equals("未识别到明确的交易完成/收支字段")||r.confirmed){r.kind="expense";r.confirmed=true;r.reason="";}if(raw.contains("拼多多"))r.merchant="拼多多";r.category=PaymentParser.category(r.merchant);return r;
 }
 private static String digest(String s){try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));return Hex.encode(b);}catch(Exception e){throw new IllegalStateException(e);}}
 public static List<WechatReceiptParser.Receipt> cards(List<String> t,long now){List<WechatReceiptParser.Receipt> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(int i=0;i<t.size();i++){
  if(!completed(t.get(i)))continue;int end=t.size();for(int j=i+1;j<t.size();j++)if(marker(t.get(j))||WechatReceiptParser.clock(t.get(j),now)>0){end=j;break;}
  List<String> card=t.subList(i,end);if(failed(String.join("\n",card)))continue;
  int debit=-1;for(int j=i+1;j<end;j++)if(t.get(j).matches("通过.{1,40}扣款")){debit=j;break;}if(debit<0)continue;
  Set<Long> amounts=new HashSet<>();for(int j=debit+1;j<end;j++){String s=t.get(j);if(s.matches(".*(?:下单时间|支付时间|交易详情|按时支付|管理扣费|查看商家订单).*"))break;long n=MoneyFlow.amount(s);if(n>0)amounts.add(n);}if(amounts.size()!=1)continue;
  String merchant="微信先用后付";if(i>0){String m=t.get(i-1);if(m.length()<=80&&!m.matches(".*(?:时间|订单|详情|服务|微信支付|我的账单|支付服务).*" )&&MoneyFlow.amount(m)==0&&WechatReceiptParser.clock(m,now)==0)merchant=m;}if(merchant.startsWith("拼多多"))merchant="拼多多";
  String order="";long paid=0;for(int j=i+1;j+1<end;j++){if(t.get(j).equals("下单时间"))order=t.get(j+1);if(t.get(j).equals("支付时间")||t.get(j).equals("扣款时间"))paid=date(t.get(j+1));}
  // Only a nearby message timestamp preceding this card, never an order timestamp below it.
  if(paid==0)for(int j=i-1;j>=Math.max(0,i-3);j--){long at=WechatReceiptParser.clock(t.get(j),now);if(at>0){paid=at;break;}if(marker(t.get(j))||t.get(j).equals("下单时间"))break;}
  WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.result=new PaymentParser.Result();r.result.cents=amounts.iterator().next();r.result.kind="expense";r.result.merchant=merchant;r.result.category=PaymentParser.category(merchant);r.time=paid;r.result.confirmed=paid>0&&now>=paid&&now-paid<=180000;
  r.result.reason=r.result.confirmed?"":paid>0?"先用后付扣款成功的历史消息，请核对支付日期":"先用后付已自动扣款；卡片只有下单时间或没有支付时间，当前记录时间仅为识别时间，请核对真实扣款日期";
  if(!orderTime(order)||merchant.equals("微信先用后付")||!WechatServiceGate.label(merchant).isEmpty())continue; // require one stable card identity; never switch to paid-time fallback
  r.key="wechat-receipt:auto:"+digest(merchant+"|"+r.result.cents+"|"+(order.isEmpty()?"paid:"+paid:"order:"+order));r.evidence.addAll(card);if(seen.add(r.key))out.add(r);
 }return out;}
 public static boolean orderTime(String s){if(s==null)return false;Matcher m=Pattern.compile("([0-9]{1,2})月([0-9]{1,2})日\\s+([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?").matcher(s);if(!m.matches())return false;try{Calendar c=Calendar.getInstance();c.clear();c.setLenient(false);c.set(2000,Integer.parseInt(m.group(1))-1,Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),m.group(5)==null?0:Integer.parseInt(m.group(5)));c.getTimeInMillis();return true;}catch(Exception e){return false;}}
 public static long date(String s){for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy年MM月dd日 HH:mm:ss"})try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat(pattern,Locale.CHINA);f.setLenient(false);java.text.ParsePosition p=new java.text.ParsePosition(0);java.util.Date d=f.parse(s,p);if(d!=null&&p.getIndex()==s.length())return d.getTime();}catch(Exception ignored){}return 0;}
}

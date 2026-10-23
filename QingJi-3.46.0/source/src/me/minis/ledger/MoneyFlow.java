package me.minis.ledger;
import java.math.BigDecimal;import java.util.*;import java.util.regex.*;
/** Pure-Java state machine. Input is sanitized by the accessibility adapter, never chat text. */
public final class MoneyFlow {
 public static final long DETAIL_TTL=600000;
 public static final long TTL=180000; // elapsedRealtime, not wall clock
 public static final class Frame {
  public String pkg="",activity="",click="";public boolean chat=false,truncated=false;public List<String> text=new ArrayList<>(),amountInputs=new ArrayList<>();
  public Frame(String p,String a,String...lines){pkg=p;activity=a;text.addAll(Arrays.asList(lines));}
 }
 public static final class Receipt {public final PaymentParser.Result result;public final String token,transactionId,type;private String emittedSig="",identity="";private Seen seen;public Receipt(PaymentParser.Result r,String k,String id,String t){result=r;token=k;transactionId=id;type=t;}}
 private static final Pattern MONEY=Pattern.compile("(?:[¥￥]\\s*|人民币\\s*)?([0-9]{1,9}(?:\\.[0-9]{1,2})?)\\s*(?:元)?");
 private static final class Seen {final long at;Seen(long a){at=a;}}
 private final LinkedHashMap<String,Seen> recentDetails=new LinkedHashMap<>();
 private static String detailIdentity(Frame f,String t,String kind,long n,String id){
  if(!id.isEmpty())return f.pkg+"|"+t+"|"+kind+"|id|"+id;
  if(kind.equals("income")?OwnReceiptEvidence.detailType(f).isEmpty():!(t.equals("红包")&&OwnReceiptEvidence.sentPacketAmount(f)>0||t.equals("转账")&&exact(f.text,"转账时间")))return "";
  String title="",transferTime="",receiveTime="";
  for(int i=0;i<f.text.size();i++){String s=f.text.get(i).trim();if(t.equals("红包")&&s.matches("[^，,。:：\\n]{1,80}的红包"))title=s;
   if(s.equals("转账时间")&&i+1<f.text.size())transferTime=f.text.get(i+1).trim();
   if(s.equals("收款时间")&&i+1<f.text.size())receiveTime=f.text.get(i+1).trim();
  }
  if(t.equals("红包")&&title.isEmpty()||t.equals("转账")&&(transferTime.isEmpty()||kind.equals("income")&&receiveTime.isEmpty()))return "";
  return f.pkg+"|"+t+"|"+kind+"|"+n+"|"+title+"|"+transferTime+"|"+receiveTime;
 }
 private void pruneDetails(long now){Iterator<Seen> it=recentDetails.values().iterator();while(it.hasNext()){long age=now-it.next().at;if(age<0||age>DETAIL_TTL)it.remove();}}
 private static String opaqueIdentity(String s){if(s.isEmpty())return s;try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));return Hex.encode(b);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void rememberDetail(String identity,long now){if(identity.isEmpty())return;recentDetails.remove(identity);if(recentDetails.size()>=64)recentDetails.remove(recentDetails.keySet().iterator().next());recentDetails.put(identity,new Seen(now));}
 private String pkg="",type="",token="",direction="expense";private long cents=0,at=0,armedAt=0;private boolean submitted=false,receiving=false;private String pendingSig="",doneSig="";private long stableAt=0;public String status="等待转账或红包流程";
 public void reset(){pkg="";type="";token="";cents=at=armedAt=0;submitted=receiving=false;pendingSig=doneSig="";status="等待转账或红包流程";}
 public static long amount(String text){if(text==null)return 0;String s=text.trim().replace(",","");Matcher m=MONEY.matcher(s);if(!m.matches())return 0;try{long v=new BigDecimal(m.group(1)).movePointRight(2).longValueExact();return v>0&&v<=99999999999L?v:0;}catch(Exception e){return 0;}}
 private static boolean exact(List<String> t,String...keys){for(String s:t)for(String k:keys)if(s.trim().equals(k))return true;return false;}
 private static boolean contains(List<String> t,String key){for(String s:t)if(s.contains(key))return true;return false;}
 private static boolean ownStored(Frame f){return OwnReceiptEvidence.storedCue(f.text)||OwnReceiptEvidence.transferCue(f.text);}
 public static boolean moneyActivity(String a){String s=a.toLowerCase(Locale.ROOT);return s.contains("remittance")||s.contains("luckymoney")||s.contains("redpacket")||s.contains("redpackage")||s.contains("transfer")||s.contains("hongbao");}
 private static String type(Frame f){String detail=FlowRouting.explicitType(f);if(detail.equals("conflict"))return "";if(!detail.isEmpty())return detail;String a=f.activity.toLowerCase(Locale.ROOT);if(a.contains("luckymoney")||a.contains("redpacket")||a.contains("redpackage")||a.contains("hongbao")||exact(f.text,"发红包","拼手气红包","普通红包","红包详情","微信红包","领取红包","收到红包","红包已领取","红包发送成功"))return "红包";if(a.contains("remittance")||a.contains("transfer")||exact(f.text,"转账","转账详情","微信转账","转账成功","确认收款","收款成功"))return "转账";return "";}
 public static boolean outgoingPage(Frame f){if(f.chat||exact(f.text,"转账成功","发送成功","支付成功","付款成功","已收款","收款成功","已存入零钱","红包发送成功","红包详情","转账详情"))return false;return (exact(f.text,"塞钱进红包","确认转账","立即转账")||(!f.amountInputs.isEmpty()&&exact(f.text,"转账","发红包")))&&(moneyActivity(f.activity)||exact(f.text,"转账金额","总金额","单个金额","塞钱进红包"));}
 public static boolean submit(String s){return s!=null&&s.trim().matches("(?:转账|确认转账|塞钱进红包|发红包|发送红包|确认发送|立即转账)");}
 public static boolean receiveClick(String s){return s!=null&&s.trim().matches("(?:确认收款|收款|领取红包|开红包|开|拆红包)");}
 private static boolean cancel(Frame f){return f.click.matches("(?:取消|取消支付|放弃支付|暂不支付)")||exact(f.text,"支付失败","付款失败","转账失败","红包发送失败","支付已取消","交易关闭","转账已退还");}
 private static long unique(List<String> values){long v=0;for(String s:values){long n=amount(s);if(n==0)continue;if(v>0&&v!=n)return 0;v=n;}return v;}
 // Total packet amount is never confused with per-person amount.
 public static long outgoingAmount(Frame f){
  String label=contains(f.text,"总金额")?"总金额":contains(f.text,"单个金额")?"单个金额":"转账金额";long n=labelAmount(f.text,label);if(n==0)n=labelAmount(f.text,"金额");if(n==0)n=unique(f.amountInputs);
  if(label.equals("单个金额")&&contains(f.text,"红包个数")){long count=0;for(int i=0;i<f.text.size()-1;i++)if(f.text.get(i).trim().equals("红包个数")){try{count=Long.parseLong(f.text.get(i+1).trim().replace("个",""));}catch(Exception ignored){}}if(count<=0||count>1000)return 0;try{n=Math.multiplyExact(n,count);}catch(Exception e){return 0;}}
  return n<=99999999999L?n:0;
 }
 public static long labelAmount(List<String> text,String label){Set<Long> values=new HashSet<>();for(int i=0;i<text.size();i++){String s=text.get(i).trim();if(s.equals(label)||s.equals(label+"（元）")||s.equals(label+"(元)")){for(int j=i+1;j<Math.min(text.size(),i+4);j++){String t=text.get(j).trim();if(t.equals("¥")||t.equals("￥")||t.equals("元"))continue;long n=amount(t);if(n>0)values.add(n);break;}}else if(s.startsWith(label+"：")||s.startsWith(label+":")){long n=amount(s.substring(label.length()+1));if(n>0)values.add(n);}}return values.size()==1?values.iterator().next():0;}
 public static String transactionId(Frame f){for(String label:new String[]{"转账单号","交易单号","订单号","红包单号"})for(int i=0;i<f.text.size();i++){String s=f.text.get(i).trim();String id=s.equals(label)&&i+1<f.text.size()?f.text.get(i+1).trim():s.startsWith(label+"：")?s.substring(label.length()+1).trim():"";if(id.matches("[A-Za-z0-9_-]{12,80}"))return id;}return "";}
 private void begin(Frame f,String t,long n,long now,boolean receive){pkg=f.pkg;type=t;token=UUID.randomUUID().toString();direction=receive?"income":"expense";cents=n;at=now;armedAt=0;submitted=false;receiving=receive;pendingSig=doneSig="";}
 static long receiptAmount(Frame f,String kind,String t){
  // Read self's receipt only: a group total, participant list or remaining amount is not an income.
  String[] labels=kind.equals("income")?(t.equals("红包")?new String[]{"领取金额","你领取的金额","你领取了","收到金额"}:new String[]{"收款金额","到账金额","转账金额"}):new String[]{"转账金额","支付金额","实付金额","红包金额","总金额"};
  for(String label:labels){long n=labelAmount(f.text,label);if(n>0)return n;}
  if(t.equals("红包")&&kind.equals("income")&&!ownStored(f)&&!exact(f.text,"领取成功","红包已领取"))return 0;
  // Only primary numeric nodes before the participants/metadata section are eligible.
  List<String> primary=new ArrayList<>();for(String s:f.text){if(s.matches(".*(?:已领取.*个|红包总额|领取记录|领取详情|手气最佳|剩余金额|总共|红包个数|转账时间|收款时间|交易单号|转账单号|订单号).*")||s.equals("领取情况"))break;if(amount(s)>0)primary.add(s);}
  return unique(primary);
 }
 public Receipt observe(Frame f,long now){return observe(f,now,false);}
 /** Complete single-window snapshot; every existing semantic/ownership gate still applies. */
 public Receipt observeComplete(Frame f,long now){return observe(f,now,true);}
 private Receipt observe(Frame f,long now,boolean complete){
  if(!ScreenParser.supported(f.pkg)){reset();return null;}if(!pkg.isEmpty()&&(!pkg.equals(f.pkg)||now-at>TTL))reset();if(f.truncated){status="页面节点过多，未用不完整页面确认交易";return null;}if(cancel(f)){reset();status="交易取消或失败，候选已丢弃";return null;}
  if(FlowRouting.explicitType(f).equals("conflict")){pendingSig="";status="收支页面类型冲突，等待新页面";return null;}
  if(FlowRouting.paid(f)&&FlowRouting.merchant(f)&&!FlowRouting.personal(f))return null;
  String t=type(f);if(!f.chat&&outgoingPage(f)&&!t.isEmpty()){
   long n=outgoingAmount(f);if(!doneSig.isEmpty()&&submitted&&!submit(f.click)&&type.equals(t)&&n==cents){pendingSig="";return null;}if(n>0){if(token.isEmpty()||receiving||!doneSig.isEmpty()||!type.equals(t)){begin(f,t,n,now,false);}else {cents=n;at=now;}status="已捕获"+t+"金额，等待发送成功";}else if(!token.isEmpty()&&!receiving&&!submitted){cents=0;status="输入金额不完整，不入账";}
  }
  // A receive control establishes the page role, not proof of completed payment.
  if(!f.chat&&!t.isEmpty()&&moneyActivity(f.activity)&&exact(f.text,"确认收款","领取红包","开红包")&&!receiveClick(f.click)&&(token.isEmpty()||!receiving||!type.equals(t))){begin(f,t,0,now,true);status="发现领取/收款页面，等待到账证据";}
  if(!f.chat&&receiveClick(f.click)&&!t.isEmpty()){begin(f,t,0,now,true);submitted=true;armedAt=now;status="已观察领取/收款动作，等待到账结果";}
  else if(submit(f.click)&&!token.isEmpty()&&!receiving&&cents>0){submitted=true;armedAt=now;status="已观察发送动作，尚未确认交易完成";}
  if(f.chat){if(doneSig.equals(token+"|review")||doneSig.startsWith(f.pkg+"|")){pendingSig="";return null;}pendingSig=submitted&&!receiving?"chat_wait":"";if(submitted&&!receiving&&cents>0&&now-armedAt>=800){PaymentParser.Result r=result(cents,"expense",type,false,"只观察到提交后返回聊天，缺少成功结果，请核对是否已发送");String sig=token+"|review";if(sig.equals(doneSig))return null;doneSig=sig;status="未取得成功证据，已提交待确认";return emitted(new Receipt(r,token,"",type),"");}return null;}
  boolean generic=ownsGenericSuccess(f,now);
  if(generic)t=type;else if(t.isEmpty())t=type;if(t.isEmpty())return null;
  long sentPacket=t.equals("红包")?OwnReceiptEvidence.sentPacketAmount(f):0;
  boolean sent=sentPacket>0||exact(f.text,"转账成功","转账已发送","红包发送成功","红包已发送","发送成功")||(submitted&&!receiving&&exact(f.text,"等待对方收款","待对方收款"));boolean paid=exact(f.text,"支付成功","付款成功");boolean received=t.equals("红包")?ownStored(f)||exact(f.text,"领取成功")||(exact(f.text,"红包已领取")&&(receiving||exact(f.text,"领取金额","你领取的金额","收到金额"))):ownStored(f)||exact(f.text,"收款成功","收款到账","你已收款")||(exact(f.text,"已收款")&&(receiving||exact(f.text,"收款金额","到账金额")));
  String kind=received?"income":sent||paid?"expense":"";if(kind.isEmpty()){status=token.isEmpty()?"未发现可确认的转账/红包结果":status;return null;}
  if(paid&&(!submitted||receiving)&&!sent){return null;} // generic payment success belongs to old purchase parser
  if(!generic&&!moneyActivity(f.activity)&&!FlowRouting.personal(f)&&OwnReceiptEvidence.detailType(f).isEmpty()&&!exact(f.text,"转账详情","红包详情","转账成功","红包发送成功","红包已领取","领取成功"))return null;
  if((sent||paid)&&received){status="页面同时包含收发线索，暂不入账";return null;}
  long n=kind.equals("expense")&&sentPacket>0?sentPacket:receiptAmount(f,kind,t);boolean linked=submitted&&!pkg.isEmpty()&&pkg.equals(f.pkg)&&direction.equals(kind)&&type.equals(t)&&now-armedAt<=TTL;
  if(n==0&&linked&&kind.equals("expense"))n=cents;if(n<=0){status=kind.equals("income")?"成功页未读到自己的实际到账金额，未猜测红包总额":"成功页金额不可读，未入账";return null;}
  String id=transactionId(f),identity=opaqueIdentity(detailIdentity(f,t,kind,n,id));pruneDetails(now);
  if((!linked||!id.isEmpty())&&!identity.isEmpty()&&recentDetails.containsKey(identity)){pendingSig="";status="重复打开近期同一详情，未新增记录";return null;}
  String sig=f.pkg+"|"+t+"|"+kind+"|"+n+"|"+id+"|"+(linked?token:"");if(sig.equals(doneSig))return null;if(!sig.equals(pendingSig)){pendingSig=sig;stableAt=now;status="发现结果，等待页面稳定";if(!complete)return null;}if(!complete&&now-stableAt<600)return null;
  boolean confirmed=linked&&(cents==0||n==cents);String reason=confirmed?"":!linked?"未观察到本次发送/领取过程，可能是旧详情，请确认交易时间与收支":"成功金额与输入金额不同，请核对";
  if(!linked){pkg=f.pkg;type=t;direction=kind;token=UUID.randomUUID().toString();at=now;submitted=false;receiving=kind.equals("income");cents=n;armedAt=0;}PaymentParser.Result r=result(n,kind,t,confirmed,reason);doneSig=sig;rememberDetail(identity,now);status=confirmed?"已确认"+t+"成功":"详情已送待确认，未当作新交易";return emitted(new Receipt(r,token,id,t),identity);
 }
 private Receipt emitted(Receipt r,String identity){r.emittedSig=doneSig;r.identity=identity;r.seen=recentDetails.get(identity);return r;}
 public void persistenceFailed(Receipt r){if(r==null)return;if(token.equals(r.token)&&doneSig.equals(r.emittedSig)){doneSig="";pendingSig="";}if(!r.identity.isEmpty()&&r.seen!=null&&recentDetails.get(r.identity)==r.seen)recentDetails.remove(r.identity);}
 private static PaymentParser.Result result(long n,String kind,String type,boolean confirmed,String reason){PaymentParser.Result r=new PaymentParser.Result();r.cents=n;r.kind=kind;r.merchant=type;r.confirmed=confirmed;r.reason=reason;return r;}
 public boolean ownsGenericSuccess(Frame f,long now){return submitted&&!receiving&&pkg.equals(f.pkg)&&now>=armedAt&&now-armedAt<=TTL&&FlowRouting.genericLinked(f,cents);}
 public boolean ownsPage(Frame f,long now){if(FlowRouting.merchant(f)&&!FlowRouting.personal(f))return false;return FlowRouting.personal(f)||moneyActivity(f.activity)||ownsGenericSuccess(f,now);}
 public boolean needsSettle(){return !pendingSig.isEmpty()&&!pendingSig.equals(doneSig);}
}

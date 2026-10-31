package me.minis.ledger;
import java.util.*;
import java.util.regex.*;
/** Reads visible UI text, not pixels. Never treats a chat quotation as a receipt. */
public final class ScreenParser {
 private static boolean safeExtras(List<String> lines,long paid){
  Pattern amount=Pattern.compile("[¥￥]\\s*[0-9,]+(?:\\.[0-9]{1,2})?|[0-9,]+(?:\\.[0-9]{1,2})?\\s*元");
  for(int i=0;i<lines.size();i++){String s=lines.get(i),previous=i>0?lines.get(i-1):"";long bare=MoneyFlow.amount(s);if(bare>0&&bare!=paid&&!extraLabel(s)&&!extraLabel(previous))return false;Matcher m=amount.matcher(s);while(m.find()){long v=MoneyFlow.amount(m.group());if(v==0)return false;if(v!=paid&&!extraLabel(s)&&!extraLabel(previous))return false;}}
  return true;
 }
 private static boolean extraLabel(String s){return s.matches("^(?:优惠(?:金额)?|优惠券抵扣|优惠券优惠|本次优惠|已优惠|优惠了|已省|已节省|共省|共优惠|立减|随机立减|碰一碰优惠|支付优惠|优惠合计|优惠总额|减免|原价|原金额|订单金额|商品金额|账户余额|可用余额|余额|积分抵扣|红包抵扣)(?:[：:\\s].*|[0-9¥￥].*|)$");}
 private static final String MERCHANT_LABEL="(?:收款方全称|收款商户全称|商户全称|商家全称|收款方名称|收款商户名称|商户名称|商家名称|收款商户|收款方|商户|商家)";
 private static final Pattern MERCHANT_FIELD=Pattern.compile("^"+MERCHANT_LABEL+"(?:[\\s\\u3000]*[:：][\\s\\u3000]*(.*)|[\\s\\u3000]+(.+))?$");
 private static boolean merchantEnd(String s){return s.matches("完成|完成支付|返回商家|返回商户")||!extraLabel(s)&&merchantPromotion(s);}
 private static boolean merchantPromotion(String s){return PaymentParser.has(s,"推荐","广告","优惠","领券","领取","关注","公众号","小程序","进入商家","进入商户","进入店铺","查看商家","查看商户","商家服务","商户服务","更多服务","支付有礼","支付后有礼","再来一单");}
 private static boolean merchantName(String s){
  if(s.isEmpty()||s.length()>100||MoneyFlow.amount(s)>0||!s.matches(".*[\\p{L}].*")||s.matches(".*[：:；;|\\r\\n¥￥<>].*"))return false;
  if(s.matches("(?i)(?:人民币|RMB|CNY)?\\s*[-+0-9., ]+\\s*(?:元|人民币|RMB|CNY)?"))return false;
  if(s.matches("(?:去|前往|打开|进入|浏览|逛逛|联系|投诉|搜索).*"))return false;
  if(merchantEnd(s)||merchantPromotion(s)||MERCHANT_FIELD.matcher(s).matches()||extraLabel(s))return false;
  if(s.matches("微信支付|微信|支付宝|财付通|人民币|RMB|CNY|元|返回|关闭|更多|帮助|客服|设置|零钱|零钱通|余额|支付|付款|收款|碰一碰|知道了"))return false;
  return !PaymentParser.has(s,"支付成功","付款成功","支付方式","付款方式","支付金额","付款金额","实付","订单","单号","交易时间","付款时间","支付时间","交易详情","账单","银行卡","信用卡","储蓄卡","尾号","查看","点击","返回","完成","http://","https://");
 }
 private static boolean bareMerchantName(String s){
  // No node roles/bounds survive this API. Accept only shop-like names in a tiny
  // receipt template, not an arbitrary neighbouring string or a navigation title.
  return merchantName(s)&&!s.matches(MERCHANT_LABEL+".*")&&s.matches(".*(?:公司|商店|商行|超市|便利店|餐厅|餐饮|饭店|咖啡|奶茶|药店|书店|酒店|店)(?:[（(][^（）()]{1,30}[）)])?");
 }
 private static long merchantPrimaryAmount(String s){
  long v=MoneyFlow.amount(s);if(v>0)return v;
  for(String label:new String[]{"实付金额","付款金额","支付金额","实付款","实付","实际付款"}){v=MoneyFlow.labelAmount(Collections.singletonList(s),label);if(v>0)return v;}
  return 0;
 }
 private static String screenMerchant(String pkg,List<String> raw,PaymentParser.Result r){
  if(!"expense".equals(r.kind))return "";
  List<String> lines=new ArrayList<>();for(String s:raw){s=s.trim();if(!s.isEmpty())lines.add(s);}
  int start=-1;for(int i=0;i<lines.size();i++)if(lines.get(i).matches("支付成功|付款成功")){if(start>=0)return "";start=i;}
  if(start<0)return "";
  int end=lines.size();for(int i=start+1;i<lines.size();i++)if(merchantEnd(lines.get(i))){end=i;break;}
  Set<String> names=new LinkedHashSet<>();boolean labelled=false,invalid=false;
  for(int i=start+1;i<end;i++){
   Matcher field=MERCHANT_FIELD.matcher(lines.get(i));if(!field.matches())continue;
   labelled=true;String name=field.group(1)!=null?field.group(1):field.group(2);
   if(name==null||name.trim().isEmpty())name=i+1<end?lines.get(i+1):"";
   name=name.trim();if(merchantName(name))names.add(name);else invalid=true;
  }
  if(labelled)return !invalid&&names.size()==1?names.iterator().next():"";
  if(!"com.tencent.mm".equals(pkg)||!r.confirmed)return "";
  // Only S/name/amount or S/amount/name followed by a real exit or one payment
  // method pair. Never search past promotion/navigation or infer from page prose.
  if(start+3>=lines.size()||end<start+3)return "";
  String a=lines.get(start+1),b=lines.get(start+2),name="";
  if(bareMerchantName(a)&&merchantPrimaryAmount(b)==r.cents)name=a;
  if(bareMerchantName(b)&&merchantPrimaryAmount(a)==r.cents)name=b;
  if(name.isEmpty())return "";
  int next=start+3;
  if(next<lines.size()&&lines.get(next).matches("支付方式|付款方式")){
   if(next+1>=lines.size()||!lines.get(next+1).matches("零钱|零钱通|余额|(?:.*银行.*|银行卡|信用卡|储蓄卡)(?:[（(].*[）)])?"))return "";
   next+=2;
  }
  return next<lines.size()&&lines.get(next).matches("完成|完成支付|返回商家|返回商户")?name:"";
 }
 public static boolean supported(String pkg){return "com.tencent.mm".equals(pkg)||"com.eg.android.AlipayGphone".equals(pkg);}
 public static PaymentParser.Result parse(String pkg,List<String> lines,boolean editable){
  if(!supported(pkg)||editable||lines==null)return null;
  boolean success=false,exit=false;StringBuilder all=new StringBuilder();
  for(String line:lines){String s=line.trim();if(s.equals("支付成功")||s.equals("付款成功"))success=true;
   if(s.equals("完成")||s.equals("返回商家")||s.equals("返回商户")||s.equals("完成支付"))exit=true;
   if(s.equals("账单详情")||s.equals("交易详情")||s.equals("聊天信息")||s.equals("转账详情")||s.equals("支付失败")||s.equals("付款失败"))return null;
   all.append(s).append('\n');
  }
  if(!success||!exit)return null;
  String text=all.toString();
  // Accessibility often exposes currency and digits as adjacent nodes.
  text=text.replaceAll("([¥￥])\\s*\\n\\s*", "$1");
  text=text.replaceAll("(实付金额|付款金额|支付金额|实付款|实付|支付|付款)\\s*[:：]?\\s*\\n\\s*([0-9]+(?:\\.[0-9]{1,2})?)(?=\\s*\\n)","$1：$2元");
  // Only the first few nodes after success are eligible for a bare primary amount.
  String[] parts=text.split("\\n");
  for(int i=0;i<parts.length;i++)if(parts[i].equals("支付成功")||parts[i].equals("付款成功")){
   for(int j=i+1;j<Math.min(parts.length,i+4);j++)if(parts[j].matches("[0-9]{1,9}\\.[0-9]{2}")){parts[j]+="元";break;}
  }
  text=String.join("\n",parts);
  List<String> core=new ArrayList<>();for(int i=0;i<parts.length;i++){if(extraLabel(parts[i])){if(i+1<parts.length&&MoneyFlow.amount(parts[i+1])>0)i++;continue;}core.add(parts[i]);}PaymentParser.Result r=PaymentParser.parse(String.join("\n",core));if(r==null)return null;
  if(!r.kind.equals("expense")){r.confirmed=false;r.reason="成功页含其他收支线索，请核对";}
  // Success pages are structured receipts, not arbitrary notifications. Only an explicit
  // actual-paid field may override multiple amounts; every extra must be labelled.
  String normalized=text;List<String> nl=Arrays.asList(normalized.split("\\n"));Set<Long> paid=new HashSet<>();
  for(String label:new String[]{"实付金额","付款金额","支付金额","实付款","实付","实际付款"}){long v=MoneyFlow.labelAmount(nl,label);if(v>0)paid.add(v);}
  if(paid.isEmpty()){for(int i=0;i<nl.size();i++)if(nl.get(i).equals("支付成功")||nl.get(i).equals("付款成功")){for(int j=i+1;j<Math.min(nl.size(),i+7);j++){String s=nl.get(j);if(extraLabel(s))break;long v=MoneyFlow.amount(s);if(v>0){paid.add(v);break;}if(!s.equals("¥")&&!s.equals("￥")&&!s.isEmpty()&&!s.matches("碰一碰|付款金额|实付|实付金额|实际付款|人民币|支付金额"))break;}}}
  if(paid.isEmpty()){Set<Long> primary=new HashSet<>();for(String s:core){long v=MoneyFlow.amount(s);if(v>0)primary.add(v);}if(primary.size()==1)paid.addAll(primary);}
  boolean unsafeCurrency=PaymentParser.has(text,"美元","港元","港币","日元","欧元","澳元","新加坡元","USD","HKD","JPY","EUR");
  if(paid.size()==1&&r.kind.equals("expense")&&!PaymentParser.has(text,"退款","到账","已收款")&&!unsafeCurrency){if(safeExtras(nl,paid.iterator().next())){r.cents=paid.iterator().next();r.confirmed=true;r.reason="";}else{r.confirmed=false;r.reason="支付成功页存在未标注或矛盾金额，请核对实际付款金额";}}
  if(!r.confirmed&&r.reason.startsWith("通知包含"))r.reason="支付成功页包含多个金额，未能唯一确定实际付款金额，请核对";
  AlipayPaid.apply(pkg,lines,r);
  // Do not inherit notification-style guesses (e.g. an advertisement saying 在某店支付).
  // A missing/ambiguous merchant is preferable to attaching another shop to this receipt.
  r.merchant=screenMerchant(pkg,nl,r);
  r.category=PaymentParser.category(r.merchant);
  if(!r.confirmed&&r.reason.isEmpty())r.reason="页面金额不明确，请核对";
  return r;
 }
}

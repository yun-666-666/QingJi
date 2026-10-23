package me.minis.ledger;
import java.util.*;
/** Input must be one isolated trusted service card, never a page-wide mixture. */
public final class WechatIncomingCard {
 public static List<WechatReceiptParser.Receipt> parse(List<String> t,long now){List<WechatReceiptParser.Receipt> out=new ArrayList<>();int at=t.indexOf("你有一笔收款入账");if(at<0)return out;String all=String.join("\n",t);if(PaymentParser.has(all,"预计扣费","支付失败","收款失败","未到账","待收款","待领取","已退款","支付密码","验证码"))return out;int label=t.indexOf("收款账户"),memo=t.indexOf("转账备注");if(label<at||label+1>=t.size()||!t.get(label+1).equals("零钱")||memo<at||memo+1>=t.size())return out;long cents=MoneyFlow.labelAmount(t,"收款金额");if(cents<=0)return out;Set<Long> values=new HashSet<>();for(int i=at+1;i<label;i++){long n=MoneyFlow.amount(t.get(i));if(n>0)values.add(n);}if(values.size()!=1||!values.contains(cents))return out;for(String s:t)if(s.startsWith("收款金额")&&!s.equals("收款金额")){long described=MoneyFlow.amount(s.substring(4).replaceFirst("^[：:]", ""));if(described>0&&described!=cents)return out;}
  String merchant="微信收款";for(int i=at-1;i>=0;i--){String s=t.get(i);if(WechatReceiptParser.clock(s,now)>0)break;if(!s.matches("微信支付|我的账单|支付服务|优惠")&&s.length()<=80&&MoneyFlow.amount(s)==0){merchant=s;break;}}
  long time=0;for(int i=at-1;i>=0;i--){long v=WechatReceiptParser.clock(t.get(i),now);if(v>0){time=v;break;}}
  WechatReceiptParser.Receipt r=new WechatReceiptParser.Receipt();r.result=new PaymentParser.Result();r.result.cents=cents;r.result.kind="income";r.result.merchant=merchant;r.result.category=PaymentParser.category(merchant);r.result.confirmed=false;r.time=time;r.result.reason=time>0?"微信收款入账卡，请核对日期及是否已记账":"微信收款入账卡没有独立收款时间，记录时间仅为识别时间；请核对日期及是否已记账，同内容无时间卡可能合并";r.evidence.addAll(Arrays.asList(merchant,"你有一笔收款入账","收款金额",String.format(Locale.US,"¥%.2f",cents/100.0),"收款账户","零钱","转账备注",t.get(memo+1)));
  try{byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest((time+"|"+String.join("|",r.evidence)).getBytes("UTF-8"));r.key="wechat-receipt:income-card:"+Hex.encode(b);}catch(Exception e){throw new IllegalStateException(e);}out.add(r);return out;
 }
}

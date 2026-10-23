package me.minis.ledger;
import java.util.*;
/** Bounded visible service-node fields; never retains diagnostic raw text. */
public final class ServiceCardText {
 public static final class Stats {public int text,desc,different,descCards,selectedDesc,longFields;public boolean textDone,descDone,partDone,debit,money;public String report(){return "正文 text="+text+" desc="+desc+" 双字段="+different+" 描述卡="+descCards+" 采用描述="+selectedDesc+" 超长="+longFields+" text成功="+textDone+" desc成功="+descDone+" 拆分成功="+partDone+" 扣款字段="+debit+" 金额节点="+money;}}
 private static String norm(String s){return s==null?"":s.replaceAll("[\\s\\u200b\\ufeff，,。]","");}
 public static boolean full(String s){return s!=null&&s.length()<2048&&WechatAutoDebit.completed(s)&&s.contains("扣款")&&(s.contains("下单时间")||s.contains("支付时间"))&&(s.contains("¥")||s.contains("￥"));}
 public static String choose(String text,String desc,Stats st){String t=text==null?"":text.trim(),d=desc==null?"":desc.trim();if(!t.isEmpty())st.text++;if(!d.isEmpty())st.desc++;if(!t.isEmpty()&&!d.isEmpty()&&!t.equals(d))st.different++;st.textDone|=WechatAutoDebit.completed(t);st.descDone|=WechatAutoDebit.completed(d);st.partDone|=norm(t).equals("已自动支付")||norm(d).equals("已自动支付");st.debit|=t.contains("扣款")||d.contains("扣款");st.money|=MoneyFlow.amount(t)>0||MoneyFlow.amount(d)>0;if(t.length()>=2048||d.length()>=2048)st.longFields++;boolean dc=full(d);if(dc)st.descCards++;
  // A generic non-evidence label may mask a self-contained card description.
  // Do not overwrite contradictory evidence from the text field.
  if(dc&&!full(t)&&MoneyFlow.amount(t)==0&&!PaymentParser.has(t,"支付","扣款","收款","金额","退款","失败","取消","下单","时间")){st.selectedDesc++;return d;}
  return t.isEmpty()?d:t;
 }
}

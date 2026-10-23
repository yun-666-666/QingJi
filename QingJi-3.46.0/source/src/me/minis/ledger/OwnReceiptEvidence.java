package me.minis.ledger;
import java.util.*;
/** Matches own-receipt cues only; not a parser for chat cards or arbitrary sentences. */
public final class OwnReceiptEvidence {
 private static String normalized(String s){return s==null?"":s.trim().replaceAll("[\\s\\u00a0\\u3000\\u200b\\ufeff]+","").replaceAll("[>›〉》»❯❱→]+$","");}
 public static boolean transferCue(List<String> lines){boolean self=false,funds=false;for(String s:lines){String v=normalized(s);if(v.matches("(?:你|您)已收款[，,。]?资金已存入零钱[。]?"))return true;if(v.matches("(?:你|您)已收款[，,。]?"))self=true;if(v.matches("资金已存入零钱[。]?"))funds=true;}return self&&funds;}
 private static boolean stored(String s){return normalized(s).matches("已存入(?:零钱|余额)(?:[，,。]?(?:可直接消费|可直接用于消费|可用于消费|可直接提现|可用于发红包|可直接发红包))?[，,。]?");}
 public static boolean storedCue(List<String> lines){for(int i=0;i<lines.size();i++){if(stored(lines.get(i)))return true;String s=normalized(lines.get(i));if(!s.matches("已存入|已存入零钱[，,。]?|已存入余额[，,。]?"))continue;for(int j=i+1;j<Math.min(lines.size(),i+3);j++){s+=normalized(lines.get(j));if(stored(s))return true;}}return false;}
 /** Only an explicit sender-side waiting sentence, not a packet title/total alone. */
 public static long sentPacketAmount(MoneyFlow.Frame f){if(f.chat||f.truncated||!packetTitle(f.text)||!MoneyFlow.moneyActivity(f.activity))return 0;long value=0;for(int i=0;i<f.text.size();i++){String s=normalized(f.text.get(i));if(!s.startsWith("红包金额"))continue;for(int j=i;j<Math.min(f.text.size(),i+4);j++){if(j>i)s+=normalized(f.text.get(j));java.util.regex.Matcher m=java.util.regex.Pattern.compile("红包金额([0-9]{1,9}(?:\\.[0-9]{1,2})?)元[，,。]?等待对方领取[。]?").matcher(s);if(m.matches()){long n=MoneyFlow.amount(m.group(1));if(value>0&&value!=n)return 0;value=n;break;}}}return value;}
 private static boolean label(List<String> lines,String key){for(String s:lines){String v=normalized(s);if(v.equals(key)||v.startsWith(key+"：")||v.startsWith(key+":"))return true;}return false;}
 public static boolean packetTitle(List<String> lines){for(String s:lines){String v=normalized(s);if(v.matches("[^，,。:：\\n]{1,80}的红包"))return true;}return false;}
 public static String detailType(MoneyFlow.Frame f){if(f.chat||f.truncated)return "";if(transferCue(f.text)&&label(f.text,"转账时间")&&label(f.text,"收款时间"))return "转账";if(storedCue(f.text)&&packetTitle(f.text))return "红包";return "";}
 public static int numericNodes(List<String> lines){int count=0;for(String s:lines)if(MoneyFlow.amount(s)>0)count++;return count;}
 public static String flags(MoneyFlow.Frame f){return "类型="+detailType(f)+" 本人收款="+transferCue(f.text)+" 存入提示="+storedCue(f.text)+" 红包标题="+packetTitle(f.text)+" 数字节点="+numericNodes(f.text)+" 聊天="+f.chat+" 截断="+f.truncated;}
}

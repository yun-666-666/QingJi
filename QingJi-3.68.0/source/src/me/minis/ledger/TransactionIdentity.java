package me.minis.ledger;
import java.util.*;import java.util.regex.*;
public final class TransactionIdentity {
 public static String providerId(String raw){if(raw==null)return "";String[] lines=raw.split("[\\r\\n]+");Pattern p=Pattern.compile("^(?:交易号|交易单号|支付单号|付款单号|支付宝交易号|微信支付单号)\\s*[:：]?\\s*([A-Za-z0-9_-]{12,64})?$");String found="";for(int i=0;i<lines.length;i++){Matcher m=p.matcher(lines[i].trim());if(!m.matches())continue;String id=m.group(1);if(id==null&&i+1<lines.length)id=lines[++i].trim();if(id==null||!id.matches("[A-Za-z0-9_-]{12,64}"))continue;if(!found.isEmpty()&&!found.equals(id))return "";found=id;}return found;}
 public static final class Info {public String key="";public long time;public boolean settlement=false;}
 public static Info identify(String source,String raw,long posted,PaymentParser.Result r){Info x=new Info();x.time=posted;if(raw==null||r==null)return x;
 String provider=source.endsWith(":screen")?source.substring(0,source.length()-7):source;
 long explicit=r.transactionTime>0?r.transactionTime:TransactionTime.labelled(raw,posted);if(explicit>0){x.time=explicit;r.transactionTime=explicit;}
 if(!source.equals("manual")){String id=r.transactionId.isEmpty()?providerId(raw):r.transactionId;if(!id.isEmpty()){x.key="provider|"+provider+"|"+r.kind+"|"+id;return x;}}
 String bank="";if(raw.contains("建设银行")||source.equals("com.chinamworld.main"))bank="ccb";else if(raw.contains("招商银行")||source.equals("cmb.pb"))bank="cmb";else if(raw.contains("工商银行"))bank="icbc";else if(raw.contains("农业银行"))bank="abc";else if(raw.contains("中国银行"))bank="boc";if(bank.isEmpty())return x;
 Matcher account=Pattern.compile("(?:尾号|账户|账号|卡号)(?:为)?\\s*[*＊尾号]*([0-9]{4})(?![0-9])").matcher(raw);if(!account.find())return x;String tail=account.group(1);
 String effective=raw;Matcher auth=Pattern.compile("(?:交易)?预授权时间\\s*[:：]?\\s*([^）)\\n]{5,45})").matcher(raw);if(auth.find()&&PaymentParser.has(raw,"完成扣款","扣款人民币","扣款成功")){effective=auth.group(1);x.settlement=true;}
 Matcher m=Pattern.compile("(?:(20[0-9]{2})年)?([0-9]{1,2})月([0-9]{1,2})日\\s*([0-9]{1,2})(?:时|:|：)([0-9]{1,2})分?").matcher(effective);if(!m.find())return x;
 try{Calendar p=Calendar.getInstance();p.setTimeInMillis(posted);int year=m.group(1)==null?p.get(Calendar.YEAR):Integer.parseInt(m.group(1));Calendar c=Calendar.getInstance();c.clear();c.setLenient(false);c.set(year,Integer.parseInt(m.group(2))-1,Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)));long t=c.getTimeInMillis();if(t>posted+86400000L&&m.group(1)==null){c.add(Calendar.YEAR,-1);t=c.getTimeInMillis();}if(t>posted+300000L||posted-t>370L*86400000L)return x;x.time=t;x.key=bank+"|"+tail+"|"+t+"|"+r.kind+"|"+r.cents;return x;}catch(Exception e){return x;}
 }
}

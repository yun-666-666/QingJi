package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Independent node-field binding; no notification regex applied to a whole screen. */
final class PaymentPageFields {
 static final Pattern CASH=Pattern.compile("[+−－-]?(?:人民币|RMB|CNY|[¥￥])\\s*[+−－-]?\\s*[0-9][0-9.,]*|[+−－-]?[0-9][0-9.,]*\\s*元");
 static String value(String s,String[] labels){for(String label:labels)if(s.startsWith(label)){String v=s.substring(label.length());if(v.isEmpty())return "";if(v.matches("^[（(：:\\s¥￥0-9−－-].*")){v=v.replaceFirst("^[：:\\s]+","").replaceFirst("^[（(]元[）)]\\s*","").replaceFirst("^[：:\\s]+","");return v;}}return null;}
 static String[] moneyLabels(int type){return type==0?PaymentPageRules.PAID:type==1?PaymentPageRules.ORDER:type==2?PaymentPageRules.DISCOUNT:PaymentPageRules.BALANCE;}
 static class Values{Long paid,order,discount,balance;boolean invalid;boolean[] used;Values(int n){used=new boolean[n];}}
 static long cash(String s){String v=s.replaceFirst("^(?:人民币|RMB|CNY)\\s*","").trim();if(!v.matches("[¥￥]?\\s*(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,9})(?:\\.[0-9]{1,2})?\\s*(?:元)?"))return -1;long n=MoneyFlow.amount(v);return n>0?n:v.matches("[¥￥]?\\s*0(?:\\.0{1,2})?\\s*(?:元)?")?0:-1;}
 static Values read(List<String> t){Values a=new Values(t.size());Long[] v=new Long[4];for(int i=0;i<t.size();i++)for(int type=0;type<4;type++){String s=value(t.get(i),moneyLabels(type));if(s==null)continue;int end=i;if(s.isEmpty()){if(i+1>=t.size()){a.invalid=true;break;}s=t.get(++end);}if(s.matches("¥|￥|人民币|RMB|CNY")&&end+1<t.size())s+=t.get(++end);long n=cash(type==2?s.replaceFirst("^[−－-]\\s*",""):s);if(n<0||v[type]!=null&&v[type]!=n){a.invalid=true;break;}v[type]=n;for(int j=i;j<=end;j++)a.used[j]=true;break;}a.paid=v[0];a.order=v[1];a.discount=v[2];a.balance=v[3];return a;}
 static boolean name(String v){return v.length()>=2&&v.length()<=100&&v.matches(".*[\\p{L}].*")&&!v.matches(".*[\\r\\n：:¥￥<>].*")&&!PaymentPageRules.success(v)&&!PaymentPageRules.has(PaymentPageRules.EXIT,v)&&!PaymentParser.has(v,"推荐","广告","领取","优惠","订单","银行卡","支付金额","付款金额","付款方式","支付方式","返回","http://","https://");}
 static boolean shop(String v){return name(v)&&v.matches(".*(?:公司|商店|商行|超市|便利店|餐厅|餐饮|饭店|咖啡|奶茶|药店|书店|酒店|店|饼屋|粥铺|食堂)(?:[（(][^（）()]{1,30}[）)])?");}
 static String merchant(List<String> t){Set<String>names=new HashSet<>();boolean bad=false,labelled=false;for(int i=0;i<t.size();i++){String v=value(t.get(i),PaymentPageRules.MERCHANT);if(v==null)continue;labelled=true;if(v.isEmpty())v=i+1<t.size()?t.get(i+1):"";if(!name(v)){bad=true;continue;}names.add(v);}if(labelled)return !bad&&names.size()==1?names.iterator().next():"";
  for(int i=0;i+3<t.size();i++)if(PaymentPageRules.success(t.get(i))){String a=t.get(i+1),b=t.get(i+2);if(!PaymentPageRules.has(PaymentPageRules.EXIT,t.get(i+3)))return "";if(shop(a)&&cash(b)>0)return a;if(shop(b)&&cash(a)>0)return b;}return "";}
}

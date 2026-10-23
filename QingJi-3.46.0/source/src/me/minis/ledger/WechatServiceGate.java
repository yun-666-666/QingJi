package me.minis.ledger;
import java.util.*;
/** Navigation-only normalization. Does not interpret amounts or conversation summaries. */
public final class WechatServiceGate {
 public static String label(String raw){if(raw==null||raw.length()>120)return "";String s=raw.trim().replaceAll("[\\s\\u200b\\ufeff]","").replaceFirst("[,，]?按钮$","");return s.matches("微信支付|我的账单|支付服务|优惠")?s:"";}
 public static void navigation(String raw,List<String> out){if(raw==null||raw.length()>240)return;String direct=label(raw);if(!direct.isEmpty()){if(!out.contains(direct))out.add(direct);return;}String[] parts=raw.split("[\\n\\r]+|[,，](?=我的账单|支付服务|优惠)");if(parts.length<2)return;List<String> labels=new ArrayList<>();for(String p:parts){String l=label(p);if(l.isEmpty())return;labels.add(l);}for(String l:labels)if(!out.contains(l))out.add(l);}
 public static boolean navigationTrusted(List<String> lines){List<String> t=new ArrayList<>();for(String s:lines)navigation(s,t);return t.contains("微信支付")&&(t.contains("我的账单")||t.contains("支付服务"))||t.contains("我的账单")&&t.contains("支付服务")&&t.contains("优惠");}
 public static boolean blocked(List<String> t){String joined=String.join("\n",t);return PaymentParser.has(joined,"支付密码","输入密码","验证码","按住说话","按住 说话","发送消息")||t.contains("通讯录")&&t.contains("发现")&&t.contains("我");}
 public static List<String> clean(List<String> input){List<String> out=new ArrayList<>();if(input==null)return out;for(String raw:input){if(raw==null)continue;for(String s:raw.split("[\\n\\r]+")){s=s.trim().replace("￥ ","￥").replace("¥ ","¥");if(s.isEmpty())continue;if(out.size()>=240)return Collections.emptyList();String l=label(s);out.add(l.isEmpty()?s:l);}}return joinHeading(out);}
 private static List<String> joinHeading(List<String> in){List<String> out=new ArrayList<>();for(int i=0;i<in.size();i++){String normalized=in.get(i).replaceAll("[\\s\\u200b\\ufeff，,。]","");if(normalized.equals("先用后付订单已完成")&&i+1<in.size()&&in.get(i+1).replaceAll("[\\s\\u200b\\ufeff，,。]","").equals("已自动支付")){out.add("先用后付订单已完成，已自动支付");i++;}else out.add(in.get(i));}return out;}
 public static String flags(List<String> t){List<String> nav=new ArrayList<>();for(String s:t)navigation(s,nav);return "标题="+nav.contains("微信支付")+" 账单菜单="+nav.contains("我的账单")+" 服务菜单="+nav.contains("支付服务")+" 优惠菜单="+nav.contains("优惠")+" 扣款标记="+t.stream().anyMatch(WechatAutoDebit::completed)+" 安全阻止="+blocked(t);}
}

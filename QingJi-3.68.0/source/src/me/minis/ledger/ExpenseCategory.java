package me.minis.ledger;
import java.util.*;
/** Classification only: never supplies payment evidence, amount, identity or status. */
public final class ExpenseCategory {
 static final String[] CATS={"餐饮","交通","购物","生活","医疗","娱乐","其他"};
 static final java.util.regex.Pattern BRANCH=java.util.regex.Pattern.compile("[（(][^（）()]*[）)]");
 static final String[][] KEYS=words();
 private static String[][] words(){String[] keys={
   "餐饮|餐厅|饭店|饭馆|食堂|快餐|小吃|面馆|面店|面庄|拉面|烩面|米线|米粉|螺蛳粉|麻辣烫|麻辣香锅|火锅|烧烤|烤肉|烤鱼|炸鸡|汉堡|披萨|饺子馆|饺子店|包子铺|包子店|早餐|早点|粥店|粥铺|粥屋|粥道|饼屋|饼店|烧饼|煎饼|肉夹馍|凉皮|卤味|熟食|烘焙|面包店|蛋糕|甜品|咖啡|奶茶|茶饮|冰淇淋|麦当劳|肯德基|必胜客|华莱士|德克士|蜜雪冰城|瑞幸|星巴克|古茗|茶百道|喜茶|沪上阿姨|霸王茶姬|海底捞|老乡鸡|乡村基|真功夫|沙县小吃|兰州拉面|饿了么|外卖",
   "地铁|公交|滴滴出行|滴滴打车|高德打车|出租车|网约车|铁路|火车票|车票|停车|加油站|加油费|充电桩|高速通行|通行费|客运站|汽车票|机票|航空公司|哈啰骑行|共享单车|青桔单车",
   "超市|便利店|便利超市|盒马|商场|淘宝|京东商城|拼多多|购物|百货|服装|鞋店|书店|文具|菜市场|生鲜|果蔬|水果店|零食店|零食很忙|赵一鸣|良品铺子|名创优品|屈臣氏|万宁|沃尔玛|永辉|大润发|胖东来|苏宁易购",
   "慧生活|校园热水|洗衣服务|洗衣机|洗衣店|干洗店|电费|水费|燃气|话费|宽带|房租|物业|理发|美发|美容|酒店|宾馆|旅馆|公寓住宿|快递费|邮寄费|中国移动|中国联通|中国电信",
   "医院|药店|大药房|药房|医疗|挂号|诊所|卫生院|口腔门诊|体检|医保缴费",
   "电影院|影城|电影票|游戏充值|网吧|网咖|游乐园|景区门票|ktv|健身房|健身馆|游泳馆|音乐会员|视频会员|爱奇艺|腾讯视频|优酷|哔哩哔哩大会员|网易云音乐|qq音乐"
  };
  String[][] out=new String[keys.length][];for(int i=0;i<keys.length;i++)out[i]=keys[i].split("\\|");return out;
 }
 // Labeled transaction fields only: never infer from an entire recommendation-filled screen.
 static final String[] PRODUCT={"商品名称","商品说明","商品信息","商品描述","订单商品","消费项目","交易用途","付款用途","服务名称","扣款服务","订单说明"};
 static final String[] BILL={"账单分类","交易分类"};
 static final java.util.regex.Pattern PROMO=java.util.regex.Pattern.compile("^(?:为你推荐|为您推荐|相关推荐|猜你喜欢|广告|推荐|领取优惠).*");
 static final java.util.regex.Pattern PLATFORM=java.util.regex.Pattern.compile("^(?:淘宝|天猫|京东|拼多多|抖音商城|美团|支付宝|微信).*");
 static final String[] MORE={"萃春疯|茉莉奶白|陈香贵|马记永|南城香|和府捞面","哈啰顺风车|花小猪打车|货拉拉|etc通行费|特来电|星星充电","奥乐齐|钱大妈|百果园|鸣鸣很忙","丰巢寄件|中通快递费|校园吹风机|校园烘干费","叮当快药|美团买药","迪士尼门票|环球影城门票"};
 static final String[] EXTRA={
  "塔斯汀|萨莉亚|达美乐|棒约翰|袁记云饺|杨国福|张亮麻辣烫|正新鸡排|绝味鸭脖|周黑鸭|茶颜悦色|奈雪|一点点|益禾堂|库迪|幸运咖|罗森咖啡|堂食|餐费|饭费|工作餐|早餐套餐|午餐|晚餐|饮品|寿司|牛肉面|炸酱面|黄焖鸡|煲仔饭|酸菜鱼|冒菜|串串香|牛肉粉|豆浆|豆花|馄饨|馍店",
  "12306|中国铁路|铁路客票|滴滴快车|滴滴专车|嘀嗒出行|曹操出行|t3出行|首汽约车|美团打车|哈啰单车|美团单车|公交乘车|乘车码|乘车费用|地铁票|停车缴费|路桥费|高速费|中国石化|中国石油|南方航空|东方航空|中国国航|海南航空|春秋航空",
  "京东|天猫|唯品会|得物|小米商城|华为商城|抖音商城|七鲜|朴朴|叮咚买菜|美团买菜|小象超市|山姆|costco|开市客|罗森|全家便利|7-eleven|美宜佳|天福便利|晨光文具|新华书店|服饰|鞋帽|数码配件|日用品|卫生纸|洗发水|充电器|数据线|耳机|牛仔裤|运动鞋",
  "自助饮水|饮水机|热水服务|生活缴费|电力缴费|国家电网|南方电网|自来水|燃气费|通讯费|话费充值|流量充值|校园洗浴|热水费|洗澡|洗浴|洗烘|洗衣|水控|校园一卡通充值|宿舍电费|住宿费|顺丰速运|菜鸟裹裹|邮政寄递|寄件费|学费|培训费|考试报名费|教育缴费|打印费|复印费",
  "医疗缴费|门诊缴费|住院费|医药费|药品费|处方药|同仁堂|益丰大药房|老百姓大药房|大参林|一心堂|健之佳|海王星辰|牙科|口腔医院|疫苗接种",
  "电影购票|猫眼电影|淘票票|电玩城|桌游|密室逃脱|剧本杀|演唱会门票|游乐场|腾讯视频会员|芒果tv|哔哩哔哩|bilibili大会员|酷狗音乐|酷我音乐|游戏点券|游戏月卡|steam游戏"
 };
 static {for(int i=0;i<KEYS.length;i++){String[] add=(EXTRA[i]+"|"+MORE[i]).split("\\|");String[] merged=Arrays.copyOf(KEYS[i],KEYS[i].length+add.length);System.arraycopy(add,0,merged,KEYS[i].length,add.length);KEYS[i]=merged;}}
 static List<String> fields(String raw){List<String> out=new ArrayList<>();if(raw==null)return out;for(String s:raw.split("[\\r\\n；;]+")){s=s.trim();if(s.length()>300)continue;if(PROMO.matcher(s).matches())break;out.add(s);}return out;}
 static String value(List<String> lines,int i,String label){String s=lines.get(i).trim();if(s.equals(label))return i+1<lines.size()?lines.get(i+1):"";if(s.startsWith(label+" ")&&!s.substring(label.length()).trim().startsWith("：")&&!s.substring(label.length()).trim().startsWith(":"))return s.substring(label.length()).trim();if(s.startsWith(label)){String v=s.substring(label.length()).trim();if(v.startsWith("：")||v.startsWith(":")){v=v.substring(1).trim();return !v.isEmpty()?v:i+1<lines.size()?lines.get(i+1):"";}}return null;}
 public static void enrich(PaymentParser.Result r,String raw){
  if(r.categoryExplicit)return;List<String> lines=fields(raw);for(int i=0;i<lines.size();i++)for(String label:BILL)if(value(lines,i,label)!=null){r.category=bill(lines,r.merchant);r.categoryExplicit=true;return;}
  String merchant=infer(r.merchant),product=null;boolean conflict=false,hasProduct=false;
  for(int i=0;i<lines.size();i++)for(String label:PRODUCT){String v=value(lines,i,label);if(v==null)continue;hasProduct=true;int hit=hit(v);if(hit==-2){conflict=true;continue;}if(hit<0)continue;String c=CATS[hit];if(product!=null&&!product.equals(c))conflict=true;product=c;}
  if(!hasProduct&&r.categoryMerchant!=null&&Objects.equals(r.categoryMerchant,r.merchant))return;r.categoryMerchant=r.merchant;
  boolean platform=r.merchant!=null&&PLATFORM.matcher(r.merchant.trim()).matches();
  if(!merchant.equals("其他")&&!platform){r.category=merchant;return;}
  r.category=conflict?"其他":product!=null?product:merchant;
 }
 private static int hit(String text){
  if(text==null)return -1;String s=text.trim().toLowerCase(Locale.ROOT);int hit=-1;
  for(int i=0;i<KEYS.length;i++)for(String k:KEYS[i])if(s.contains(k)){if(hit>=0)return -2;hit=i;break;}
  return hit;
 }
 public static String infer(String text){int i=hit(text==null?null:BRANCH.matcher(text).replaceAll(""));return i<0?"其他":CATS[i];}
 public static String explicit(String value){
  if(value==null)return "其他";String s=value.trim();
  String[][] map={{"餐饮","餐饮美食","美食","餐饮食品"},{"交通","交通出行","交通运输"},{"购物","日用百货","服饰装扮","数码电器","母婴亲子","家居家装","文化休闲用品"},{"生活","生活服务","充值缴费","住房物业","酒店旅游"},{"医疗","医疗健康","医疗保健"},{"娱乐","文化休闲","运动户外","休闲娱乐","运动健身"}};
  String found=null;for(String part:s.split("[/／>＞·]+"))for(String[] a:map)for(String x:a)if(part.trim().equals(x)){if(found!=null&&!found.equals(a[0]))return "其他";found=a[0];}return found==null?"其他":found;
 }
 public static String bill(List<String> lines,String merchant){
  String found=null;for(int i=0;i<lines.size();i++)for(String label:BILL){String v=value(lines,i,label);if(v!=null){String c=explicit(v);if(found!=null&&!found.equals(c))return "其他";found=c;}}
  return found==null?infer(merchant):found;
 }
 public static String key(String merchant,String kind){
  if(!"expense".equals(kind)&&!"income".equals(kind)&&!"refund".equals(kind))return "";
  String s=merchant==null?"":merchant.trim();if(s.length()<2||s.length()>100||s.matches("支付|付款|收款|转账|红包|其他|未知商户|未识别商户|未识别|微信|支付宝|云闪付|手动|购物订单"))return "";
  // Exact full merchant, retaining branch and punctuation; never merge arbitrary payees.
  return kind+"|"+s;
 }
}

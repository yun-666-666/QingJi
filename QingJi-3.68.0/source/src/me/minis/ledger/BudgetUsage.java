package me.minis.ledger;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;import java.util.*;
/** Current natural-month total follows the home formula; category budgets keep their own rules. */
public final class BudgetUsage {
 public final long limit,used,month;public final String style;public final boolean income;
 public BudgetUsage(long limit,long used,long month,String style){this(limit,used,month,style,false);}
 public BudgetUsage(long limit,long used,long month,String style,boolean income){this.limit=Math.max(0,limit);this.used=used;this.month=month;this.style=validStyle(style);this.income=income;}
 public static boolean homeIncome(Context c){return LedgerDb.prefs(c).getBoolean("home_income",false);}
 static long used(SQLiteDatabase db,long start,long end,String category,boolean income){return category.isEmpty()?LedgerStats.amount(LedgerStats.totals(db,start,end),income,!income):Math.max(0,FinanceQueries.budget(db,start,end,category));}
 public String formula(){return income?"净花销（支出−收入，不扣退款）":"净支出（支出−退款，不扣收入）";}
 public String explanation(){return "跟随首页口径，按本自然月"+formula()+"计算；不含借还本金、结算、待确认、已排除和未来日期流水。分类预算仍按支出−退款及案件排除设置独立计算。";}
 public static String validStyle(String s){return "B".equals(s)?"B":"D".equals(s)?"D":"A";}
 public static String style(Context c){return validStyle(LedgerDb.prefs(c).getString("budget_style","A"));}
 static void saveStyle(Context c,String s){if(!Arrays.asList("A","B","D").contains(s))throw new IllegalArgumentException("预算样式不合法");if(!LedgerDb.prefs(c).edit().putString("budget_style",s).commit())throw new IllegalStateException("样式保存失败，请重试");LedgerEvents.changed(c);}
 public boolean visible(){return limit>0;}public boolean over(){return visible()&&used>limit;}
 public long remaining(){return over()?used-limit:Math.max(0,limit-used);}
 public double ratio(){return visible()?used*100.0/limit:0;}public float fill(){return (float)Math.max(0,Math.min(1,ratio()/100));}
 public String status(){return over()?"over":visible()&&used==limit?"full":ratio()>=80?"near":"normal";}
 public String word(){return over()?"超":"余";}
 public String percent(){double r=ratio();if(r>999)return "999%+";long n=Math.round(r);if(r<100)n=Math.min(99,n);else if(r>100)n=Math.max(101,n);return n+"%";}
 public static String money(long n){return String.format(Locale.ROOT,"%.2f",n/100.0);}
 public static String compact(long n){return n>=10000000000L?String.format(Locale.ROOT,"%.2f亿",n/10000000000.0):n>=1000000?String.format(Locale.ROOT,"%.2f万",n/1000000.0):money(n);}
 public String description(){return "本月总预算 "+money(limit)+" 元，已用 "+money(used)+" 元，"+(over()?"超支":"剩余")+" "+money(remaining())+" 元，使用率 "+String.format(Locale.ROOT,"%.1f",ratio())+"%；按本自然月"+formula()+"计算";}
 public JSONObject json()throws JSONException{return new JSONObject().put("cents",limit).put("used",used).put("month",month).put("style",style).put("income",income);}
 static BudgetUsage read(Context c,LedgerDb h,long now){return read(c,h,now,homeIncome(c));}
 static BudgetUsage read(Context c,LedgerDb h,long now,boolean income){try(StatsSnapshot snapshot=new StatsSnapshot(h.getReadableDatabase())){return read(c,now,income,snapshot);}}
 static BudgetUsage read(Context c,long now,boolean income,StatsSnapshot snapshot){long[] range=LedgerPlans.month(now);long limit=0;try(Cursor r=snapshot.db.rawQuery("SELECT cents FROM budgets WHERE category=''",null)){if(r.moveToFirst())limit=r.getLong(0);}long used=limit>0?LedgerStats.amount(snapshot.totals(range[0],Math.min(range[1],WidgetPeriods.throughToday(now))),income,!income):0;return new BudgetUsage(limit,used,range[0],style(c),income);}
}

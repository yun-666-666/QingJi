package me.minis.ledger;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;import java.util.*;
/** One natural-month total, independent of selected book/widget/notice mode. No category fallback. */
public final class BudgetUsage {
 public final long limit,used,month;public final String style;
 public BudgetUsage(long limit,long used,long month,String style){this.limit=Math.max(0,limit);this.used=Math.max(0,used);this.month=month;this.style=validStyle(style);}
 public static String validStyle(String s){return "B".equals(s)?"B":"D".equals(s)?"D":"A";}
 public static String style(Context c){return validStyle(LedgerDb.prefs(c).getString("budget_style","A"));}
 static void saveStyle(Context c,String s){if(!Arrays.asList("A","B","D").contains(s))throw new IllegalArgumentException("预算样式不合法");if(!LedgerDb.prefs(c).edit().putString("budget_style",s).commit())throw new IllegalStateException("样式保存失败，请重试");LedgerEvents.changed(c);}
 public boolean visible(){return limit>0;}public boolean over(){return visible()&&used>limit;}
 public long remaining(){return over()?used-limit:Math.max(0,limit-used);}
 public double ratio(){return visible()?used*100.0/limit:0;}public float fill(){return (float)Math.min(1,ratio()/100);}
 public String status(){return over()?"over":visible()&&used==limit?"full":ratio()>=80?"near":"normal";}
 public String word(){return over()?"超":"余";}
 public String percent(){double r=ratio();if(r>999)return "999%+";long n=Math.round(r);if(r<100)n=Math.min(99,n);else if(r>100)n=Math.max(101,n);return n+"%";}
 public static String money(long n){return String.format(Locale.ROOT,"%.2f",n/100.0);}
 public static String compact(long n){return n>=10000000000L?String.format(Locale.ROOT,"%.2f亿",n/10000000000.0):n>=1000000?String.format(Locale.ROOT,"%.2f万",n/1000000.0):money(n);}
 public String description(){return "本月总预算 "+money(limit)+" 元，已用 "+money(used)+" 元，"+(over()?"超支":"剩余")+" "+money(remaining())+" 元，使用率 "+String.format(Locale.ROOT,"%.1f",ratio())+"%；按本自然月净支出计算";}
 public JSONObject json()throws JSONException{return new JSONObject().put("cents",limit).put("used",used).put("month",month).put("style",style);}
 static BudgetUsage read(Context c,LedgerDb h,long now){SQLiteDatabase db=h.getReadableDatabase();long[] range=LedgerPlans.month(now);db.beginTransactionNonExclusive();try{long limit=0;try(Cursor r=db.rawQuery("SELECT cents FROM budgets WHERE category=''",null)){if(r.moveToFirst())limit=r.getLong(0);}long used=limit>0?FinanceQueries.budget(db,range[0],Math.min(range[1],now+1),""):0;BudgetUsage b=new BudgetUsage(limit,used,range[0],style(c));db.setTransactionSuccessful();return b;}finally{db.endTransaction();}}
}

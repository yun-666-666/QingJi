package me.minis.ledger;
import java.util.*;import java.text.SimpleDateFormat;
/** Chronological local-calendar periods; current bucket ends tomorrow, matching home totals. */
public final class WidgetPeriods {
 public static int valid(int p){return p>=0&&p<=2?p:0;}
 public static long[] bounds(long now,int period){period=valid(period);Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);if(period==1)c.add(Calendar.DAY_OF_MONTH,-((c.get(Calendar.DAY_OF_WEEK)+5)%7));if(period==2)c.set(Calendar.DAY_OF_MONTH,1);int field=period==2?Calendar.MONTH:Calendar.DAY_OF_MONTH,step=period==1?7:1;c.add(field,-2*step);long[] b=new long[4];for(int i=0;i<4;i++){b[i]=c.getTimeInMillis();c.add(field,step);}return b;}
 public static long throughToday(long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DAY_OF_MONTH,1);return c.getTimeInMillis();}
 public static String label(long start,int period){return new SimpleDateFormat(period==2?"yyyy/MM":"MM/dd",Locale.CHINA).format(new Date(start))+(period==1?"周":"");}
 public static String money(long cents){return java.math.BigDecimal.valueOf(cents,2).toPlainString();}
 public static final String SQL="SELECT COALESCE(SUM(CASE WHEN kind='expense' THEN cents WHEN kind='income' AND ?='1' THEN -cents ELSE 0 END),0) FROM entries WHERE status='confirmed' AND time>=? AND time<?";
}

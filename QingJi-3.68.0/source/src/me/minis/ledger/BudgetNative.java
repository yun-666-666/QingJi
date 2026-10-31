package me.minis.ledger;
import android.content.Context;import android.view.View;import android.widget.RemoteViews;
/** Native TextView + determinate ProgressBar only. No bitmap, Canvas, ImageView or launch intent. */
public final class BudgetNative {
 private static int id(Context c,String name){return c.getResources().getIdentifier(name,"id",c.getPackageName());}
 public static RemoteViews view(Context c,BudgetUsage b,boolean dark){return view(c,b,dark,false);}
 public static void bindNotice(Context c,RemoteViews target,String host,BudgetUsage b){int slot=id(c,host);target.removeAllViews(slot);if(b.visible())target.addView(slot,view(c,b,false,true));}
 private static RemoteViews view(Context c,BudgetUsage b,boolean dark,boolean notice){String status=b.over()?"over":b.ratio()>=80?"near":"normal",key=(dark?"dark_":"light_")+status;RemoteViews v=new RemoteViews(c.getPackageName(),c.getResources().getIdentifier(notice?"budget_notice_content":"budget_native_"+key,"layout",c.getPackageName()));boolean ring=b.style.equals("B");v.setViewVisibility(id(c,"budget_linear"),ring?View.GONE:View.VISIBLE);v.setViewVisibility(id(c,"budget_circular"),ring?View.VISIBLE:View.GONE);if(!ring){v.setViewVisibility(id(c,"budget_bar"),b.style.equals("A")?View.VISIBLE:View.GONE);v.setViewVisibility(id(c,"budget_segments"),b.style.equals("D")?View.VISIBLE:View.GONE);}
  String prefix=ring?"budget_ring_":"budget_";v.setTextViewText(id(c,prefix+"label"),"月预算");v.setTextViewText(id(c,prefix+"percent"),b.percent());v.setTextViewText(id(c,prefix+"value"),b.word()+" ¥"+BudgetUsage.compact(b.remaining()));
  // Android owns notification text colors; the widget keeps its existing palette.
  if(!notice){int accent=b.over()?(dark?0xffffaabc:0xffb73956):b.ratio()>=80?(dark?0xfff2cd94:0xff95601b):AppAppearance.accent(dark);v.setTextColor(id(c,prefix+"label"),AppAppearance.muted(dark));v.setTextColor(id(c,prefix+"percent"),accent);v.setTextColor(id(c,prefix+"value"),accent);}
  int progress=Math.round(b.fill()*10000);if(ring)v.setProgressBar(id(c,"budget_ring"),10000,progress,false);else if(b.style.equals("D")){for(int i=0;i<10;i++)v.setProgressBar(id(c,"budget_seg_"+i),10000,Math.max(0,Math.min(10000,(progress-i*1000)*10)),false);}else v.setProgressBar(id(c,"budget_bar"),10000,progress,false);v.setContentDescription(id(c,"budget_native"),b.description());return v;
 }

 public static void bind(Context c,RemoteViews target,String host,BudgetUsage b,boolean dark){int slot=id(c,host);target.removeAllViews(slot);if(b.visible())target.addView(slot,view(c,b,dark));}
}

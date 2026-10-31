package me.minis.ledger;
/** Widget-only layout decisions. No ledger or permission changes. */
final class WidgetSizing {
 static boolean compact(int width){return width>0&&width<220;}
 static boolean shortCard(int height){return height>0&&height<190;}
 static boolean chart(int width,int height){return true;}
 static String layout(int width,int height){return "ledger_widget_native";}
 static float amountPx(int width,float density,float sampleWidth,float sampleHeight,float basePx){float available=Math.max(1,((width-20)*.43f-10)*density);return Math.max(1,basePx*Math.min(1,Math.min(available/Math.max(1,sampleWidth),42*density/Math.max(1,sampleHeight))));}
 static int chartWidth(int width){return Math.round((width-20)*.57f);}
 static int chartHeight(int width,int height){return Math.max(96,height-94);}
}

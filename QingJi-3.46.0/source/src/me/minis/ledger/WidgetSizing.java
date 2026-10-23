package me.minis.ledger;
/** Widget-only layout decisions. No ledger or permission changes. */
final class WidgetSizing {
 static boolean compact(int width){return width>0&&width<220;}
 static boolean shortCard(int height){return height>0&&height<190;}
 static boolean chart(int width,int height){return true;}
 static String layout(int width,int height){return "ledger_widget_native";}
 static int chartWidth(int width){return Math.round((width-20)*.57f);}
 static int chartHeight(int width,int height){return Math.max(96,height-94);}
}

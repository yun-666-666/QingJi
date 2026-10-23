package me.minis.ledger;
/** Palette and compositing are pure Java, matching theme.css rather than system force-dark. */
public final class AppAppearance {
 public static int background(boolean dark){return dark?0xff141722:0xfff7f8fc;}
 public static int surface(boolean dark){return dark?0xff202434:0xfff8f9fd;}
 public static int text(boolean dark){return dark?0xffe6e8f3:0xff252a3a;}
 public static int muted(boolean dark){return dark?0xffa7b0c8:0xff81889b;}
 public static int soft(boolean dark){return dark?0xff2a2f45:0xffeef0fa;}
 public static int accent(boolean dark){return dark?0xffadb5ff:0xff626ddd;}
 public static int blend(int base,int overlay,double alpha){int out=0xff000000;for(int shift:new int[]{16,8,0}){int a=(base>>shift)&255,b=(overlay>>shift)&255;out|=((int)Math.round(a*(1-alpha)+b*alpha))<<shift;}return out;}
 public static int shade(int base,boolean dark,int depth){for(int i=0;i<Math.min(3,Math.max(0,depth));i++)base=blend(base,dark?0xff000000:0xff1c2446,dark?.56:101.0/255);return base;}
 public static boolean darkIcons(int color){double l=.299*((color>>16)&255)+.587*((color>>8)&255)+.114*(color&255);return l>=145;}
}

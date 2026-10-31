package me.minis.ledger;
/** Formatting compatibility only; never guesses digits or repairs malformed amounts. */
public final class AmountText {
 private AmountText(){}
 public static String spaces(String s){return s==null?"":s.replace('\u00a0',' ').replace('\u202f',' ').replace('\u3000',' ').trim();}
 public static String canonical(String s){s=spaces(s);StringBuilder b=null;for(int i=0;i<s.length();i++){char c=s.charAt(i),n=c;if(c>='０'&&c<='９')n=(char)('0'+c-'０');else if(c=='．')n='.';else if(c=='，')n=',';if(n!=c&&b==null)b=new StringBuilder(s);if(b!=null)b.setCharAt(i,n);}return b==null?s:b.toString();}
}

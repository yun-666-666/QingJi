package me.minis.ledger;
/** Exact yuan input only; invalid input remains an ordinary text search. */
public final class SearchAmount {
 public static long parse(String text){if(text==null)return -1;String s=text.trim().replaceFirst("^[¥￥]\\s*","").replaceFirst("\\s*元$","").trim();if(!s.matches("[0-9]{1,9}(?:\\.[0-9]{1,2})?"))return -1;int dot=s.indexOf('.');String whole=dot<0?s:s.substring(0,dot),part=dot<0?"":s.substring(dot+1);return Long.parseLong(whole)*100+(part.isEmpty()?0:Long.parseLong(part)*(part.length()==1?10:1));}
}

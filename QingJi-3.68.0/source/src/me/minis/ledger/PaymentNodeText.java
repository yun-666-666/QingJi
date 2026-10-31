package me.minis.ledger;
import java.util.*;
/** Read both accessibility fields; preserve conflicting amounts so rules reject ambiguity. */
public final class PaymentNodeText {
 private static final java.util.regex.Pattern BREAKS=java.util.regex.Pattern.compile("[\\r\\n]+");
 public static List<String> lines(CharSequence text,CharSequence desc){List<String> a=split(text),b=split(desc);if(a==null||b==null)return null;if(a.isEmpty())return b;if(b.isEmpty()||Collections.indexOfSubList(a,b)>=0)return a;if(Collections.indexOfSubList(b,a)>=0)return b;if(a.size()+b.size()>64)return null;a.addAll(b);return a;}
 private static List<String> split(CharSequence field){List<String> out=new ArrayList<>();if(field==null)return out;if(field.length()>2048)return null;for(String line:BREAKS.split(field)){String s=line.trim();if(s.isEmpty())continue;if(s.length()>300||out.size()>=64)return null;out.add(s);}return out;}
}

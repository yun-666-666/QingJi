package me.minis.ledger;
/** A nearby amount without a transaction id is only a relation, never an automatic merge. */
public final class CrossSourceEvidence {
 public static boolean related(String source,String other,long received,long otherReceived,String merchant,String otherMerchant,String txkey,String otherKey){
  if(source==null||other==null||source.equals(other)||source.equals("manual")||other.equals("manual"))return false;
  String base=source.endsWith(":screen")?source.substring(0,source.length()-7):source;
  String obase=other.endsWith(":screen")?other.substring(0,other.length()-7):other;
  if(!base.equals(obase)||source.endsWith(":screen")==other.endsWith(":screen")||received<=0||otherReceived<=0||Math.abs(received-otherReceived)>180000)return false;
  if(!txkey.isEmpty()&&!otherKey.isEmpty()&&!txkey.equals(otherKey))return false;
  return merchant.isEmpty()||otherMerchant.isEmpty()||merchant.equals(otherMerchant);
 }
 public static String reason(long id){return "疑似重复：同一应用的通知/页面已有同金额记录 #"+id+"，仅作关联，未自动合并；若是同一笔请忽略本条，另一笔才确认";}
}

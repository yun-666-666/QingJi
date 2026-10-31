package me.minis.ledger;
import java.util.UUID;
/** One stable success page is one observation; a recognised departure starts a new page session. */
public final class PurchaseSession {
 private String candidate="",saved="",session=UUID.randomUUID().toString();
 private long at;
 public String observe(String signature,long now){
  if(!signature.equals(candidate)){candidate=signature;at=now;return "";}
  if(now<at||now-at<800||saved.equals(signature))return "";
  saved=signature;return "screen:"+session+":"+signature;
 }
 /** Caller has already validated one complete, non-editable success-page snapshot. */
 public String observeComplete(String signature,long now){
  if(signature==null||signature.isEmpty()||saved.equals(signature))return "";
  candidate=signature;at=now;saved=signature;return "screen:"+session+":"+signature;
 }
 public boolean waiting(String signature){return !signature.equals(saved);}
 public void failed(String key){if(key.equals("screen:"+session+":"+saved))saved="";}
 public void leave(){if(candidate.isEmpty()&&saved.isEmpty())return;candidate=saved="";at=0;session=UUID.randomUUID().toString();}
}

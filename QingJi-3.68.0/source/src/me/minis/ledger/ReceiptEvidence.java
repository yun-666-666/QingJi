package me.minis.ledger;
import java.util.*;
/** Independent receipt fields, not an application/title allowlist. Missing status stays in review. */
final class ReceiptEvidence {
 static boolean structured(List<String> lines,PaymentPageFields.Values fields){
  if(fields.invalid||fields.paid==null||fields.paid<=0)return false;
  String merchant=PaymentPageFields.merchant(ExpenseCategory.fields(String.join("\n",lines)));
  if(merchant.isEmpty())return false;
  boolean method=false;
  for(int i=0;i<lines.size();i++){
   String value=PaymentPageFields.value(lines.get(i),PaymentPageRules.METHOD);
   if(value==null)continue;
   if(value.isEmpty()&&i+1<lines.size())value=lines.get(i+1);
   if(value.length()>=2&&value.length()<=60&&value.matches(".*[\\p{L}].*")&&!value.matches(".*[：:\\r\\n¥￥<>].*")&&!PaymentPageRules.has(PaymentPageRules.METHOD,value)&&!PaymentPageRules.has(PaymentPageRules.MERCHANT,value)&&!PaymentPageRules.has(PaymentPageRules.PAID,value)&&!PaymentPageRules.has(PaymentPageRules.EXIT,value)&&!PaymentPageRules.success(value))method=true;
  }
  return method||!TransactionIdentity.providerId(String.join("\n",lines)).isEmpty();
 }
}

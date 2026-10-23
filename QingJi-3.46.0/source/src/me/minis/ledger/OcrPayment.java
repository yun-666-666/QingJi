package me.minis.ledger;

import java.util.*;

/** OCR fills missing screen evidence; the existing payment rules still decide the transaction. */
final class OcrPayment {
 static final class Area {
  final int left,top,right,bottom;
  Area(int left,int top,int right,int bottom){this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
 }
 static Area area(PaymentSnapshot s,int learnedTitleTop){
  if(s.truncated||s.editable||s.width<=0||s.height<=0)return null;
  PaymentSnapshot.Node title=s.success();int top=title==null?learnedTitleTop:title.top;
  if(top<s.top||top>=s.top+s.height)return null;
  int bottom=s.top+s.height;
  for(PaymentSnapshot.Node n:s.nodes)for(String line:n.lines()){
   if(PaymentPageRules.has(PaymentPageRules.HISTORY,line)||PaymentPageRules.has(PaymentPageRules.BLOCKED,line)||PaymentPageRules.has(PaymentPageRules.NON_PAYMENT_TITLES,line))return null;
   if(n.top>top&&PaymentPageRules.has(PaymentPageRules.EXIT,line))bottom=Math.min(bottom,n.bottom+Math.max(4,s.height/100));
  }
  return new Area(s.left,Math.max(s.top,top-Math.max(4,s.height/100)),s.left+s.width,Math.min(s.top+s.height,bottom));
 }
 static final class Parsed {
  final PaymentParser.Result result;final List<String> lines;
  Parsed(PaymentParser.Result result,List<String> lines){this.result=result;this.lines=lines;}
 }
 static Parsed parse(PaymentSnapshot original,List<PaymentSnapshot.Node> recognized,ScreenProfile profile){
  if(original.truncated||original.editable||recognized.isEmpty())return null;
  List<PaymentSnapshot.Node> nodes=new ArrayList<>(original.nodes);
  for(PaymentSnapshot.Node pixel:recognized){
   boolean duplicate=false;for(PaymentSnapshot.Node node:original.nodes){
    if(node.lines().isEmpty())continue;
    boolean overlaps=Math.min(node.right,pixel.right)>Math.max(node.left,pixel.left)&&Math.min(node.bottom,pixel.bottom)>Math.max(node.top,pixel.top);
    // Equal text at the same position is one observation, not two success titles/amounts.
    if(overlaps&&compact(node.lines()).equals(compact(pixel.lines()))){duplicate=true;break;}
    // OCR may place a label and its missing value on one line. Replace only that label.
    if(overlaps&&node.lines().size()==1&&pixel.lines().size()==1&&fieldLabel(node.lines().get(0))&&PaymentPageFields.value(pixel.lines().get(0),new String[]{node.lines().get(0)})!=null)nodes.remove(node);
   }
   if(!duplicate)nodes.add(pixel);
  }
  nodes.sort(Comparator.comparingInt((PaymentSnapshot.Node n)->n.top).thenComparingInt(n->n.left));
  PaymentSnapshot combined=new PaymentSnapshot(original.pkg,original.version,original.window,original.left,original.top,original.width,original.height,nodes,false,false);
  List<String> lines=profile.bind(combined);PaymentParser.Result result=GenericPaymentRules.parse(original.pkg,lines,false);
  if(result==null)return null;
  result.transactionId=TransactionIdentity.providerId(String.join("\n",lines));
  result.reason="内置离线 OCR 补充页面文字；成功状态、实付金额及原有付款规则已校验";
  return new Parsed(result,lines);
 }
 private static String compact(List<String> lines){return String.join("",lines).replaceAll("\\s+","");}
 private static boolean fieldLabel(String text){if(PaymentPageRules.has(PaymentPageRules.MERCHANT,text))return true;for(int i=0;i<4;i++)if(PaymentPageRules.has(PaymentPageFields.moneyLabels(i),text))return true;return false;}
 static String frame(PaymentSnapshot s){
  StringBuilder out=new StringBuilder(s.pkg).append('|').append(s.version).append('|').append(s.window).append('|').append(s.width).append('x').append(s.height);
  for(PaymentSnapshot.Node n:s.nodes)if(!n.lines().isEmpty())out.append('\n').append(n.left).append(',').append(n.top).append(':').append(n.text).append('|').append(n.description);
  return out.toString();
 }
}

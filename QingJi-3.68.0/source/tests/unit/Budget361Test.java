package me.minis.ledger;
public final class Budget361Test {
 static int checks;static void ok(boolean x,String m){checks++;if(!x)throw new AssertionError(m);}
 public static void main(String[] args){
 long[] totals={130664,38545,10000,3};
 for(int pass=0;pass<6;pass++){boolean income=pass%2==1;long used=LedgerStats.amount(totals,income,!income);BudgetUsage b=new BudgetUsage(100000,used,0,"D",income);ok(used==(income?92119:120664),"formula switches");ok(b.over()==!income,"over state");ok(b.remaining()==(income?7881:20664),"remaining switches");ok(b.percent().equals(income?"92%":"121%"),"percent switches");ok(b.formula().startsWith(income?"净花销":"净支出"),"formula label");}
 for(long expense:new long[]{0,10000,100000})for(long revenue:new long[]{0,5000,200000})for(long refund:new long[]{0,3000,150000})for(boolean income:new boolean[]{false,true}){
 long used=LedgerStats.amount(new long[]{expense,revenue,refund,3},income,!income);BudgetUsage b=new BudgetUsage(100000,used,0,"B",income);ok(used==expense-(income?revenue:refund),"exact selected formula");ok(b.used==used,"signed used retained");ok(b.remaining()==Math.abs(100000-used),"remaining/over amount");ok(b.fill()>=0&&b.fill()<=1,"bounded progress");}
 BudgetUsage a=new BudgetUsage(100000,LedgerStats.amount(totals,false,true),0,"A",false),b=new BudgetUsage(100000,LedgerStats.amount(totals,true,false),0,"A",true);ok(a.used==120664&&b.used==92119,"two component modes coexist");
 System.out.println("PASS "+checks+" budget361 formula and budget assertions; no Android launcher test");
 }
}

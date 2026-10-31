package me.minis.ledger;
public final class Budget358Test {
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
 for(String style:new String[]{"A","B","D"})for(boolean income:new boolean[]{false,true}){
  BudgetUsage b=new BudgetUsage(100000,income?92119:120664,0,style,income);
  check(b.used==(income?92119:120664));check(b.remaining()==(income?7881:20664));check(b.over()!=income);check(b.percent().equals(income?"92%":"121%"));check(b.formula().contains(income?"不扣退款":"不扣收入"));check(b.description().contains(b.formula()));
  b=new BudgetUsage(100000,-20000,0,style,income);check(b.used==-20000);check(b.remaining()==120000);check(b.fill()==0);check(b.percent().equals("-20%"));check(b.status().equals("normal"));
  b=new BudgetUsage(100000,100000,0,style,income);check(b.status().equals("full"));check(b.remaining()==0);check(b.percent().equals("100%"));
  b=new BudgetUsage(100000,100001,0,style,income);check(b.fill()==1);check(b.percent().equals("101%"));check(b.remaining()==1);
  b=new BudgetUsage(0,0,0,style,income);check(!b.visible());check(b.fill()==0);
 }
 check(LedgerStats.amount(new long[]{130664,38545,10000,3},true,false)==92119);check(LedgerStats.amount(new long[]{130664,38545,10000,3},false,true)==120664);
 System.out.println("PASS "+checks+" pure Java budget formula, signed value and visual state assertions");
 }
}

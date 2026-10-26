package me.minis.ledger;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

final class FinanceService {
 static boolean readOnly(String action){return Arrays.asList("layoutRead","cases","day","state","detail","calendar","project","duplicates","rulePreview","goalDetail","list","importPreview","importSelect","importCancel").contains(action);}
 static String label(String action){switch(action){case "split":return "消费拆分";case "refund":return "退款关联";case "case":return "报销或借款";case "settle":return "回款分配";case "merge":return "合并重复账单";case "distinct":return "保留不同消费";case "rule":return "保存个人规则";case "ruleApply":return "规则处理历史";case "taglink":return "添加项目标签";case "allocation":return "目标分配";case "importCommit":return "导入对账";default:return "编辑";}}
 static Object read(Context c,LedgerDb h,String op,JSONObject p)throws Exception{
  if(op.startsWith("import"))return BillImport.request(c,h,op,p);
  return FinanceQueries.read(h.getReadableDatabase(),op,p);
 }
 static Object write(Context c,LedgerDb h,String op,JSONObject p)throws Exception{
  SQLiteDatabase db=h.getWritableDatabase();
  if(op.equals("rule"))return FinanceRules.save(db,p);
  if(op.equals("ruleApply"))return FinanceRules.preview(db,p,true);
  if(op.equals("merge")||op.equals("distinct"))return FinanceRules.duplicate(db,p,op.equals("merge"));
  if(op.equals("importCommit"))return BillImport.request(c,h,op,p);
  return FinanceData.mutate(db,op,p);
 }
}

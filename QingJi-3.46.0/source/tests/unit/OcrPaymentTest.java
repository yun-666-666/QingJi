package me.minis.ledger;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public final class OcrPaymentTest {
 private PaymentSnapshot.Node node(int id,String text,int x,int y,int width){return new PaymentSnapshot.Node(id,-1,7,x,y,x+width,y+40,"","text",text,"",false);}
 private PaymentSnapshot snapshot(PaymentSnapshot.Node... nodes){return new PaymentSnapshot("com.example.payment",100,7,0,0,1080,2000,Arrays.asList(nodes),false,false);}
 private PaymentSnapshot missing(){return snapshot(node(1,"支付成功",100,100,300),node(2,"实付金额",100,260,200),node(3,"完成",100,800,200));}
 private OcrPayment.Parsed parse(PaymentSnapshot s,PaymentSnapshot.Node... nodes){return OcrPayment.parse(s,Arrays.asList(nodes),ScreenProfile.SEMANTIC);}
 @Test public void existingRecognitionIsUnchanged(){RecognitionTest.main(new String[0]);}
 @Test public void missingAmountBecomesOriginalConfirmedPayment(){OcrPayment.Parsed p=parse(missing(),node(10000,"28.50元",450,260,200));assertNotNull(p);assertEquals(2850,p.result.cents);assertTrue(p.result.confirmed);}
 @Test public void ocrCombinedLabelReplacesOnlyTheMissingLabel(){OcrPayment.Parsed p=parse(missing(),node(10000,"实付金额 28.50元",100,260,600));assertNotNull(p);assertEquals(2850,p.result.cents);}
 @Test public void duplicateSuccessTitleIsOneAnchor(){assertNotNull(parse(missing(),node(10000,"支付成功",102,102,300),node(10001,"28.50元",450,260,200)));}
 @Test public void conflictingReadableAmountIsNotOverwritten(){PaymentSnapshot s=snapshot(node(1,"支付成功",100,100,300),node(2,"实付金额",100,260,200),node(3,"完成",100,800,200),node(4,"99.00元",450,260,200));assertNull(parse(s,node(10000,"实付金额 28.50元",100,260,600)));}
 @Test public void opaqueResultPageUsesActualOcrAnchors(){assertNotNull(parse(snapshot(),node(10000,"支付成功",100,100,300),node(10001,"实付金额 28.50元",100,260,600),node(10002,"完成",100,800,200)));}
 @Test public void visualMatchDoesNotInventSuccess(){assertNull(parse(snapshot(),node(10000,"实付金额 28.50元",100,260,600),node(10001,"完成",100,800,200)));}
 @Test public void historyAndFailureKeepTheirExistingExclusions(){assertNull(parse(missing(),node(10000,"支付失败",100,500,300),node(10001,"28.50元",450,260,200)));assertNull(parse(missing(),node(10000,"账单详情",100,500,300),node(10001,"28.50元",450,260,200)));}
 @Test public void cropEndsAtExitAndKeepsDisplayCoordinates(){OcrPayment.Area area=OcrPayment.area(missing(),0);assertEquals(80,area.top);assertEquals(860,area.bottom);assertEquals(1080,area.right);}
 @Test public void noPaymentAnchorNeedsRecentCheckoutOrLearnedRegion(){assertNull(OcrPayment.area(snapshot(),-1));assertNotNull(OcrPayment.area(snapshot(),0));}
 @Test public void snapshotTextParsingIsReused(){PaymentSnapshot.Node n=node(1,"支付成功",0,0,200);assertSame(n.lines(),n.lines());}
 @Test public void recognitionFrameChangesWhenAmountOrWindowChanges(){assertNotEquals(OcrPayment.frame(missing()),OcrPayment.frame(snapshot(node(1,"实付金额28.50元",100,260,600))));}
}

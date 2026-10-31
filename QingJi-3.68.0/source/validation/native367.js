const {JSDOM}=require('jsdom'),fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const base=process.argv[2]||'/var/minis/shared/qingji-3.50/work/assets-v2';let checks=0,errors=[],calls=[];const ok=(x,m)=>{checks++;assert(x,m)};
const dom=new JSDOM(fs.readFileSync(path.join(base,'index.html'),'utf8'),{runScripts:'outside-only',pretendToBeVisual:true,url:'https://ledger.local/'}),w=dom.window,d=w.document;
w.matchMedia=()=>({matches:true,addEventListener(){}});w.scrollTo=()=>{};w.HTMLElement.prototype.scrollIntoView=()=>{};w.addEventListener('error',e=>errors.push(e.error?.stack||e.message));
const now=Date.now(),entries=[{_id:1,rev:1,kind:'expense',cents:1234,merchant:'合成餐厅',category:'餐饮',source:'manual',time:now,status:'confirmed',excluded:0},{_id:2,rev:1,kind:'income',cents:5000,merchant:'合成回款',category:'其他',source:'manual',time:now,status:'confirmed',excluded:0},{_id:3,rev:1,kind:'refund',cents:234,merchant:'合成退款',category:'餐饮',source:'manual',time:now,status:'confirmed',excluded:0}];
const stats={expense:1234,income:5000,refund:234,count:3,start:0,end:now+86400000};let revision=1,failOp='',savedLayout=null;
function response(op,a){
 if(op===failOp)throw new Error('合成读取失败');
 if(op==='home')return {day:stats,week:stats,selected:stats,pending:0};
 if(op==='changes')return {rows:[],revision,reset:false};
 if(op==='select')return {count:a.mode==='all'?3:0};
 if(op==='apply')return 3;
 if(op==='page')return {rows:[],order:[],total:0,more:false};
 if(op==='overview')return {rows:[],cents:0,count:0,revision};
 if(op==='plans')return {budgets:[],reminders:[],backup:{}};
 if(op==='detail')return entries.find(e=>e._id===Number(a.id))||entries[0];
 if(op==='finance.layoutRead')return savedLayout;
 if(op==='finance.state')return {rules:[],tags:[],goals:[],layout:savedLayout};
 if(op==='finance.layout'){savedLayout={rev:1,data:JSON.stringify(a)};return savedLayout;}
 if(op==='finance.cases')return {rows:[],total:0,more:false};
 if(op==='finance.list')return {rows:entries.filter(e=>!a.kind||e.kind===a.kind),total:entries.filter(e=>!a.kind||e.kind===a.kind).length};
 if(op==='finance.detail')return {entry:entries.find(e=>e._id===a.entry)||entries[0],objects:[],refunded:0,paid:1234,personal:1234,receivable:0,explanation:'合成识别依据'};
 if(op==='finance.duplicates')return [];
 if(op==='finance.calendar')return {rows:[{day:new Date().getDate(),expense:1234,refund:234,count:3}]};
 if(op==='finance.day')return {...stats,rows:entries,total:3,more:false};
 if(op==='finance.rulePreview')return {matched:0,protected:0,sample:[],conflicts:[],revision};
 if(op==='finance.importPreview')return {summary:{platform:'manual',start:now,end:now},groups:[],rows:[],allRows:0,total:0,selected:0};
 if(['finance.case','finance.split','finance.refund','finance.rule','finance.importCancel'].includes(op))return {};
 throw new Error('UNHANDLED '+op);
}
const bridge={getTheme:()=> 'dark',getMotion:()=> 'a',getHomePeriod:()=> 'month',theme(){},chrome(){},settingsVisible(){},homePeriod(){},refresh(){},importStatement(){calls.push(['importStatement',{}])},request(text){const q=JSON.parse(text);calls.push([q.op,q.args]);w.setTimeout(()=>{try{w.ledgerResult({id:q.id,ok:true,value:response(q.op,q.args)});}catch(e){w.ledgerResult({id:q.id,ok:false,error:e.message});}},0);}};w.Ledger=bridge;
for(const s of d.querySelectorAll('script[src]'))try{vm.runInContext(fs.readFileSync(path.join(base,s.getAttribute('src')),'utf8'),dom.getInternalVMContext());}catch(e){errors.push(e.stack)}
const tick=()=>new Promise(r=>setTimeout(r,35));const click=async(root,text)=>{const b=[...root.querySelectorAll('button')].find(b=>b.textContent===text);ok(b,'button exists '+text);b.click();await tick();};const fin=()=>d.getElementById('finance');
async function home(){if(w.currentAppDialog?.())w.closeAppDialog();if(!d.getElementById('dateModal').hidden)d.getElementById('dateClose').click();if(!d.getElementById('modal').hidden)d.getElementById('close').click();d.querySelector('[data-tab=book]').click();await tick();}
async function detail(id){await home();w.openLedgerEntry(id);await tick();d.getElementById('entryFinance').click();await tick();ok(fin().textContent.includes('账单关系与识别说明'),'entry finance opens '+id);}
(async()=>{await tick();w.receive({paged:true,entriesRevision:1,capture:false,budgetUsage:{}});await tick();
 ok(calls.some(c=>c[0]==='home')&&calls.some(c=>c[0]==='page'),'native paging actually active');
 ok(!/Cannot set properties|UNHANDLED/.test(d.getElementById('toast').textContent),'startup no null-control error: '+d.getElementById('toast').textContent);
 for(const label of ['报销','借还']){await home();await click(d.querySelector('.book-management'),label);ok(w.ledgerPage()==='finance'&&!fin().hidden&&fin().textContent.includes(label==='报销'?'报销与代垫':'借入与借出'),'route renders '+label);await click(fin(),'从实际流水新建');await click(fin(),'选择这笔');ok(fin().textContent.includes('到期日'),'case form renders');ok(!fin().querySelector('select,input[type=date]'),'no native selection');fin().querySelector('.app-select').click();await tick();ok(!!w.currentAppDialog(),'case type application popup');w.goBack();await tick();ok(!w.currentAppDialog()&&!fin().hidden,'back closes popup not route');await click(fin(),'保存案件');ok(calls.some(c=>c[0]==='finance.case'),'case save reaches native request');}
 await home();await click(d.querySelector('#book .monthbar'),'定制');ok(fin().textContent.includes('编辑首页')&&!/项目|目标/.test(fin().textContent),'layout excludes removed modules');await click(fin(),'完成');ok(w.ledgerPage()==='book','layout save returns book');
 for(const tab of ['review','settings']){d.querySelector('[data-tab='+tab+']').click();await tick();const root=tab==='review'?d.querySelector('.review-management'):d.getElementById('dataPanel');await click(root,tab==='review'?'导入账单查漏':'账单导入与对账');await click(fin(),'选择账单文件');ok(calls.some(c=>c[0]==='importStatement'),'import picker bridge');await click(fin(),'继续查看已解析预览');ok(fin().textContent.includes('对账预览'),'import preview renders');w.goBack();await tick();}
 await home();d.querySelector('[data-tab=review]').click();await tick();await click(d.querySelector('.review-management'),'重复账单核对');await click(fin(),'选择这笔');ok(fin().textContent.includes('暂无候选'),'duplicate query rendered');
 await home();d.querySelector('[data-tab=settings]').click();await tick();await click(d.getElementById('capturePanel'),'个人分类规则');await click(fin(),'新建规则');ok(fin().querySelectorAll('.app-select').length===4,'rule dropdowns custom');await click(fin(),'预览匹配与冲突');ok(fin().textContent.includes('匹配 0 笔'),'rule preview works');
 await detail(1);await click(fin(),'拆分分类');ok(fin().querySelectorAll('.app-select').length===2,'split category selectors');await click(fin(),'平均分配总额');await click(fin(),'保存拆分');ok(calls.some(c=>c[0]==='finance.split'),'split save request');
 await detail(3);await click(fin(),'关联原消费');await click(fin(),'选择这笔');ok(fin().textContent.includes('退款分配'),'refund allocation route');await click(fin(),'确认关联');ok(calls.some(c=>c[0]==='finance.refund'),'refund association request');
 await home();d.getElementById('bookDateButton').click();await tick();ok(d.getElementById('dateMonthTotal').textContent==='本月净支出 ¥10.00','calendar via native request');d.getElementById('dateApply').click();await tick();ok(d.getElementById('dateModal').hidden&&w.ledgerPage()==='book','calendar retained bottom route');
 await home();d.getElementById('selectVisible').click();await tick();ok(!d.getElementById('selectionBar').hidden&&!d.getElementById('batchCategory').disabled,'batch select without removed control');d.getElementById('selectCancel').click();await tick();ok(d.getElementById('selectionBar').hidden,'batch cancel works');
 failOp='finance.cases';await click(d.querySelector('.book-management'),'报销');ok(fin().textContent.includes('合成读取失败')&&!fin().hidden,'backend error visible not blank page');failOp='';await home();await click(d.querySelector('.book-management'),'报销');ok(fin().textContent.includes('暂无案件'),'reopen after error recovers');
 ok(errors.length===0,errors.join('\n'));ok(!/Cannot set properties|UNHANDLED/.test(d.getElementById('toast').textContent),'no unhandled bridge operations');console.log('PASS '+checks+' native-mode UI assertions; mocked native transport, real paged.js and entry routes; not Android database integration');dom.window.close();
})().catch(e=>{console.error(e.stack);console.error('window errors:',errors);dom.window.close();process.exitCode=1});

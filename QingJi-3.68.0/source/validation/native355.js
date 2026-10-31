const {JSDOM}=require('jsdom'),fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const base=process.argv[2]||'/var/minis/shared/qingji-3.50/work/assets-v2';let checks=0,errors=[],calls=[],phase=0,revision=1,delayGroup=false,delayed=null;
const ok=(x,m)=>{checks++;assert(x,m)},dom=new JSDOM(fs.readFileSync(path.join(base,'index.html'),'utf8'),{runScripts:'outside-only',pretendToBeVisual:true,url:'https://ledger.local/'}),w=dom.window,d=w.document;
w.matchMedia=()=>({matches:true,addEventListener(){}});w.scrollTo=()=>{};w.HTMLElement.prototype.scrollIntoView=()=>{};w.addEventListener('error',e=>errors.push(e.error?.stack||e.message));
const now=Date.now(),key='expense:合成商户',entry=id=>({_id:id,rev:revision,kind:'expense',cents:300,merchant:'合成商户',category:'生活',source:'com.tencent.mm:screen',label:'微信 · 页面',time:now,status:'confirmed',excluded:0,raw:'合成付款凭证 3.00元',reason:''});
function response(op,a){
 if(op==='home')return {day:{expense:phase?300:600,income:0,refund:0,count:phase?1:2,start:0,end:now+86400000},pending:0};
 if(op==='changes')return {rows:[],revision,reset:true};
 if(op==='select')return {count:a.mode==='clear'?0:1};
 if(op==='page'){
  if(a.status==='review')return {rows:[],order:[],total:0,more:false};
  if(a.group)return {rows:phase?[entry(2)]:[entry(1),entry(2)],order:phase?['2']:['1','2'],total:phase?1:2,more:false};
  const g={...entry(phase===2?3:2),key,name:'合成商户',singleId:phase?(phase===2?3:2):0,count:phase?1:2,cents:phase?300:600,selected:0,categoryCount:1};
  return {rows:[g],order:[key],total:g.count,more:false};
 }
 if(op==='detail')return entry(a.id);
 if(op==='finance.layoutRead')return null;
 if(op==='finance.cases')return {rows:[],total:0,more:false};
 if(op==='plans')return {budgets:[],reminders:[],backup:{}};
 if(op==='overview')return {rows:[],cents:0,count:0,revision};
 throw new Error('UNHANDLED '+op);
}
function reply(q){try{w.ledgerResult({id:q.id,ok:true,value:response(q.op,q.args)});}catch(e){w.ledgerResult({id:q.id,ok:false,error:e.message});}}
w.Ledger={getTheme:()=> 'dark',getMotion:()=> 'a',getHomePeriod:()=> 'day',theme(){},chrome(){},settingsVisible(){},homePeriod(){},refresh(){},request(text){const q=JSON.parse(text);calls.push(q);if(delayGroup&&q.op==='page'&&q.args.group){delayed=q;return;}w.setTimeout(()=>reply(q),0);}};
for(const s of d.querySelectorAll('script[src]'))try{vm.runInContext(fs.readFileSync(path.join(base,s.getAttribute('src')),'utf8'),dom.getInternalVMContext());}catch(e){errors.push(e.stack)}
const tick=()=>new Promise(r=>setTimeout(r,50)),book=()=>d.getElementById('entries');
(async()=>{await tick();w.receive({paged:true,entriesRevision:revision,capture:false,budgetUsage:{}});await tick();
 ok(book().querySelectorAll('details').length===1,'multiple entries aggregate once');ok(book().textContent.includes('支出合计'),'multiple retains aggregate');
 book().querySelector('details').open=true;await tick();ok(book().querySelectorAll('.transaction').length===2,'expanded details load');
 phase=1;revision++;w.receive({paged:true,entriesRevision:revision,capture:false,budgetUsage:{}});await tick();await tick();
 ok(book().querySelectorAll('details').length===0,'aggregate removed after two become one');ok(book().querySelectorAll('.transaction').length===1,'exactly one surviving bill');ok(!book().textContent.includes('合计'),'single has no aggregate label');ok(book().querySelector('.transaction').dataset.id==='2','surviving id');ok(book().textContent.includes('微信 · 页面'),'single source visible');
 book().querySelector('.transaction').click();await tick();ok(!d.getElementById('modal').hidden,'single opens original editor');ok(calls.some(q=>q.op==='detail'&&q.args.id===2),'single requests correct original');
 const raw=d.getElementById('rawPanel');ok(raw.tagName==='DETAILS'&&!raw.open&&!raw.hidden,'raw evidence collapsed not removed');raw.open=true;ok(raw.textContent.includes('合成付款凭证'),'raw evidence remains');d.getElementById('close').click();book().querySelector('.transaction').click();await tick();ok(!raw.open,'reopen resets collapsed state');
 const other=d.querySelector('#categoryChoices [data-value="其他"]');other.click();ok(d.getElementById('editCategory').value==='其他'&&other.classList.contains('selected'),'other category still selectable');ok(d.querySelectorAll('#categoryChoices button').length===7,'all seven categories retained');d.getElementById('close').click();
 phase=2;revision++;w.receive({paged:true,entriesRevision:revision,capture:false,budgetUsage:{}});await tick();await tick();book().querySelector('.transaction').click();await tick();ok(calls.some(q=>q.op==='detail'&&q.args.id===3),'same-value replacement opens new id');d.getElementById('close').click();
 for(const target of ['h2','.finance-head','.finance-back']){[...d.querySelectorAll('.book-management button')].find(b=>b.textContent==='借还').click();await tick();ok(!d.getElementById('finance').hidden,'route opens '+target);d.querySelector('#finance '+target).click();await tick();ok(w.ledgerPage()==='book','whole navigation header returns '+target);}
 phase=0;revision++;w.receive({paged:true,entriesRevision:revision,capture:false,budgetUsage:{}});await tick();await tick();delayGroup=true;book().querySelector('details').open=true;await tick();ok(!!delayed,'group request is in flight');phase=1;revision++;w.receive({paged:true,entriesRevision:revision,capture:false,budgetUsage:{}});await tick();await tick();reply(delayed);await tick();ok(book().querySelectorAll('.transaction').length===1&&!book().querySelector('details'),'late group response cannot resurrect old details');
 ok(errors.length===0,errors.join('\n'));ok(!/UNHANDLED|Cannot set|缓存失效/.test(d.getElementById('toast').textContent),'no runtime errors');console.log('PASS '+checks+' screenshot-regression assertions (real paged.js, mocked native transport; not Android integration)');dom.window.close();
})().catch(e=>{console.error(e.stack);console.error(errors);dom.window.close();process.exitCode=1});

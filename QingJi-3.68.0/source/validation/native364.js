const {JSDOM}=require('jsdom'),fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const base=process.argv[2]||'/var/minis/shared/qingji-3.50/work/assets-v2',scenario=process.argv[3]||'reminder';let checks=0,errors=[],calls=[],held=[],hold=new Set(),revision=1;
const ok=(x,m)=>{checks++;assert(x,m)},dom=new JSDOM(fs.readFileSync(path.join(base,'index.html'),'utf8'),{runScripts:'outside-only',pretendToBeVisual:true,url:'https://ledger.test/'}),w=dom.window,d=w.document,$=id=>d.getElementById(id);
w.matchMedia=()=>({matches:true,addEventListener(){}});w.scrollTo=()=>{};w.HTMLElement.prototype.scrollIntoView=()=>{};w.addEventListener('error',e=>errors.push(e.error?.stack||e.message));
const stats={expense:12000,income:5000,refund:2000,count:3},plan=()=>({budgets:[],reminders:[],backup:{enabled:true,keep:5,directory:true,ack:true}});
function value(q){switch(q.op){case 'select':return {count:0};case 'home':return {day:stats,week:stats,selected:stats,pending:41};case 'page':return {rows:[],order:[],total:41,more:false};case 'changes':return {rows:[],revision,reset:false};case 'overview':return {rows:[],cents:0,count:0,revision};case 'finance.layoutRead':return null;case 'plans':return plan();case 'history':return [];case 'reminder':return q.id||'12345678-1234-1234-1234-123456789abc';case 'budget':case 'backupSettings':case 'ackReminder':return true;default:throw Error('UNHANDLED '+q.op)}}
const reply=(q,v,err)=>w.ledgerResult(err?{id:q.rid,ok:false,error:err}:{id:q.rid,ok:true,value:v===undefined?value(q):v});
w.Ledger={getTheme:()=> 'dark',getMotion:()=> 'a',getHomePeriod:()=> 'month',theme(){},chrome(){},settingsVisible(){},homePeriod(){},refresh(){},request(text){const raw=JSON.parse(text),q={...raw,...raw.args,rid:raw.id};calls.push(q);if(hold.has(q.op)){held.push(q);return}w.setTimeout(()=>{try{reply(q)}catch(e){reply(q,null,e.message)}},0)}};
for(const s of d.querySelectorAll('script[src]'))vm.runInContext(fs.readFileSync(path.join(base,s.getAttribute('src')),'utf8'),dom.getInternalVMContext());
const tick=()=>new Promise(r=>setTimeout(r,55)),tab=t=>d.querySelector('[data-tab='+t+']').click(),take=op=>{const i=held.findIndex(q=>q.op===op);assert(i>=0,'held '+op);return held.splice(i,1)[0]},submit=()=> $('reminderForm').dispatchEvent(new w.Event('submit',{cancelable:true,bubbles:true}));
function receive(){w.receive({paged:true,entriesRevision:revision,homeIncome:false,capture:false,budgetUsage:{}})}
async function settings(){tab('settings');$('plansPanel').open=true;await tick();}
(async()=>{await tick();receive();await tick();
if(scenario==='reminder'){
 await settings();$('reminderNew').click();$('reminderTitle').value='合成订阅';hold.add('reminder');submit();submit();await tick();ok(calls.filter(q=>q.op==='reminder').length===1,'double submit creates only one reminder');ok($('reminderForm').querySelector('[type=submit]').disabled,'save disabled while pending');reply(take('reminder'));await tick();ok($('reminderForm').hidden,'successful unchanged form closes');ok(!$('reminderForm').querySelector('[type=submit]').disabled,'save re-enabled');
 $('reminderNew').click();$('reminderTitle').value='第一份';submit();await tick();const old=take('reminder');$('reminderCancel').click();$('reminderNew').click();$('reminderTitle').value='新草稿';reply(old);await tick();ok(!$('reminderForm').hidden&&$('reminderTitle').value==='新草稿','late save preserves newly opened draft');ok($('reminderId').value==='','new draft must not inherit old saved ID');
 submit();await tick();reply(take('reminder'),null,'合成保存失败');await tick();ok(!$('reminderForm').hidden,'failed save keeps draft');ok(!$('reminderForm').querySelector('[type=submit]').disabled,'failed save unlocks');
 hold.clear();$('reminderTitle').value='保存中编辑';hold.add('reminder');submit();await tick();const edited=take('reminder');$('reminderTitle').value='编辑未提交';$('reminderTitle').dispatchEvent(new w.Event('input',{bubbles:true}));reply(edited);await tick();ok(!$('reminderForm').hidden&&$('reminderTitle').value==='编辑未提交','editing pending draft is not discarded');ok($('reminderId').value==='12345678-1234-1234-1234-123456789abc','same draft receives saved ID');submit();await tick();const update=take('reminder');ok(update.id==='12345678-1234-1234-1234-123456789abc','next save updates instead of creating duplicate');reply(update);await tick();
}
if(scenario==='draft'){
 await settings();$('reminderNew').click();$('reminderTitle').value='旧草稿';hold.add('reminder');submit();await tick();const old=take('reminder');$('reminderCancel').click();$('reminderNew').click();$('reminderTitle').value='新草稿';reply(old);await tick();ok(!$('reminderForm').hidden,'late save cannot close new reminder');
}
if(scenario==='backupDraft'){
 await settings();$('backupPanel').open=true;await tick();$('backupKeep').value='7';$('backupKeep').dispatchEvent(new w.Event('input'));hold.add('backupSettings');$('backupSave').click();await tick();const old=take('backupSettings');$('backupKeep').value='9';$('backupKeep').dispatchEvent(new w.Event('input'));reply(old);await tick();await tick();ok($('backupKeep').value==='9','pending backup save must retain new draft');
}
if(scenario==='history'){
 await settings();hold.add('history');$('historyPanel').open=true;await tick();const old=take('history');$('historyRefresh').click();await tick();reply(take('history'),[]);await tick();reply(old,null,'过期历史错误');await tick();ok(!$('historyRows').textContent.includes('过期历史错误'),'late history error ignored');
}
if(scenario==='group'){
 hold.add('page');$('search').value='分组';$('search').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter'}));await tick();const list=take('page'),key='expense:合成商家';reply(list,{rows:[{key,name:'合成商家',kind:'expense',count:2,cents:600,time:Date.now(),category:'生活',categoryCount:1}],order:[key],total:2,more:false});await tick();$('entries').querySelector('details').open=true;await tick();const group=take('page');ok(!!group.group,'group read held');$('search').value='新筛选';$('search').dispatchEvent(new w.KeyboardEvent('keydown',{key:'Enter'}));await tick();reply(take('page'),{rows:[],order:[],total:0,more:false});await tick();reply(group,null,'过期商家错误');await tick();ok(!$('toast').textContent.includes('过期商家错误'),'obsolete group error suppressed');
}
if(scenario==='cycle'){
 await settings();$('reminderNew').click();$('reminderTitle').value='选择周期';const before=calls.filter(q=>q.op==='reminder').length;$('reminderCycleChoices').querySelector('[data-value=year]').click();await tick();ok($('reminderCycle').value==='year','cycle updated');ok(calls.filter(q=>q.op==='reminder').length===before,'cycle button must not submit reminder');ok(!$('reminderForm').hidden,'cycle selection leaves form open');
}
if(scenario==='backup'){
 await settings();$('backupPanel').open=true;await tick();$('backupKeep').value='7';$('backupKeep').dispatchEvent(new w.Event('input'));hold.add('backupSettings');$('backupSave').click();$('backupSave').click();await tick();ok(calls.filter(q=>q.op==='backupSettings').length===1,'backup save deduplicated');const q=take('backupSettings');ok(q.keep===7,'submitted exact snapshot');$('backupKeep').value='9';$('backupKeep').dispatchEvent(new w.Event('input'));reply(q);await tick();await tick();ok($('backupKeep').value==='9','late save preserves newer backup edits');ok(!$('backupSave').disabled,'backup save unlocks');
}
if(scenario==='plans'){
 await settings();hold.add('plans');receive();await tick();const first=take('plans');for(let i=0;i<12;i++)receive();await tick();ok(!held.some(q=>q.op==='plans'),'plan refreshes coalesce while one read pending');reply(first);await tick();ok(held.filter(q=>q.op==='plans').length===1,'coalesced burst schedules one follow-up');const second=take('plans');reply(second);await tick();ok($('plansError').textContent==='','latest plan read paints successfully');
 hold.clear();hold.add('history');$('historyPanel').open=true;await tick();const stale=take('history');$('historyRefresh').click();await tick();const fresh=take('history');reply(fresh,[]);await tick();reply(stale,null,'过期历史错误');await tick();ok(!$('historyRows').textContent.includes('过期历史错误'),'stale history error cannot overwrite fresh result');
}
if(scenario==='review'){
 hold.add('page');tab('review');await tick();const first=take('page');ok(first.page===0,'initial review loads page zero');reply(first,null,'首次失败');await tick();ok(!$('loadReview').hidden,'review initial failure exposes retry');$('loadReview').click();await tick();const retry=take('page');ok(retry.page===0,'review retry does not skip first 40 rows');reply(retry);await tick();ok(!$('loadReview').disabled,'retry clears busy state');
}
ok(errors.length===0,errors.join('\n'));console.log('PASS '+checks+' audit364 '+scenario+' assertions; mocked transport, production UI');dom.window.close()})().catch(e=>{console.error(e.stack,errors);dom.window.close();process.exitCode=1});

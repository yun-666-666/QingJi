'use strict';
(async()=>{
 const host=document.getElementById('finance'),checks=[],errors=[],wait=ms=>new Promise(r=>setTimeout(r,ms));
 const request=(op,args={})=>toolsRequest('finance.'+op,args),marker='功能验收-'+Date.now();
 let baseline=0,failure='',epoch=0;
 const check=(value,name)=>{if(!value)throw new Error(name);checks.push(name);};
 async function until(fn,name){for(let i=0;i<100;i++){if(await fn())return;await wait(80);}throw new Error(name);}
 const error=e=>errors.push(e.message||'JS error');window.addEventListener('error',error);
 const field=label=>[...host.querySelectorAll('.finance-field')].find(n=>n.firstChild?.textContent===label)?.querySelector('input,select,textarea');
 const click=text=>{const b=[...host.querySelectorAll('button')].find(n=>n.textContent===text);if(!b)throw new Error('Missing button: '+text);b.click();};
 const title=()=>host.querySelector('h2')?.textContent;
 async function tool(number){
  document.querySelector('[data-tab="finance"]').click();
  for(let i=0;i<20&&!host.querySelector('.finance-grid');i++){host.querySelector('.finance-head button').click();await wait(100);}
  await until(()=>host.querySelector('.finance-grid'),'Tools hub');
  const b=[...host.querySelectorAll('.finance-grid button')].find(n=>n.textContent.startsWith(number));if(!b)throw new Error('Missing feature '+number);b.click();await wait(200);
 }
 async function searchPick(kind){await until(()=>field('商户关键词或账单编号'),'Picker ready');field('商户关键词或账单编号').value=marker;click('查询');await until(()=>[...host.querySelectorAll('.finance-card')].some(n=>n.textContent.includes(marker)),'Picker search');const row=[...host.querySelectorAll('.finance-card')].find(n=>n.textContent.includes(marker)&&n.textContent.includes(kind));if(!row)throw new Error('Fixture picker row');[...row.querySelectorAll('button')].find(n=>n.textContent==='选择这笔').click();await wait(200);}
 async function seed(kind,cents){await toolsRequest('save',{time:Date.now()-10000,source:'manual',kind,cents,merchant:marker,category:'其他'});const page=await toolsRequest('page',{status:'confirmed',all:true,kind,q:marker,groups:false,epoch:++epoch});return page.rows[0]._id;}
 try{
  await until(()=>typeof renderFinance==='function','Finance script ready');
  baseline=(await toolsRequest('history',{}))[0]?.id||0;
  const expense=await seed('expense',10000),refund=await seed('refund',2000);
  document.querySelector('[data-tab="finance"]').click();await until(()=>host.querySelectorAll('.finance-grid>button').length===12,'Twelve tools');check(true,'All twelve feature entrances visible');
  await tool('01');check(title()==='账单导入对账'&&host.textContent.includes('CSV'),'Statement import entry');
  await tool('04');await searchPick('支出');await until(()=>title()==='账单关系与识别说明'&&host.textContent.includes('此记录未保留识别依据'),'Evidence detail');check(true,'Manual record shows honest missing evidence');
  click('拆分分类');await until(()=>title()==='一笔消费拆分'&&field('金额 / 元'),'Split form');const amounts=[...host.querySelectorAll('.finance-field')].filter(n=>n.firstChild?.textContent==='金额 / 元').map(n=>n.querySelector('input'));amounts[0].value='60';amounts[1].value='40';click('保存拆分');await until(()=>title()==='账单关系与识别说明','Split saved');check((await request('detail',{entry:expense})).objects.filter(o=>o.type==='split').length===2,'Split form persists two items');
  await tool('02');await searchPick('退款');await searchPick('支出');await until(()=>title()==='退款分配'&&field('本次分配金额 / 元'),'Refund form');field('本次分配金额 / 元').value='20';click('确认关联');await until(()=>title()==='账单关系与识别说明','Refund saved');check((await request('detail',{entry:expense})).paid===8000,'Refund UI links to split expense');
  await tool('03');await searchPick('支出');await until(()=>title()==='重复账单并排核对','Duplicates page');check(host.textContent.includes('候选依据'),'Duplicate screen explains recommendation');
  await tool('05');click('新建规则');await until(()=>field('规则名称'),'Rule form');field('规则名称').value=marker;field('商户（可空）').value=marker;click('预览匹配与冲突');await until(()=>host.textContent.includes('匹配 2 笔'),'Rule preview');check(host.textContent.includes('手工保护 2 笔'),'Rule UI previews protected matches');
  await tool('07');await until(()=>[...host.querySelectorAll('button')].some(b=>b.textContent==='从实际流水新建'),'Reimbursement ready');click('从实际流水新建');await searchPick('支出');await until(()=>field('对方称呼'),'Case form');field('对方称呼').value=marker;field('应收／本金 / 元').value='60';click('保存案件');await until(()=>title()==='报销与代垫'&&host.textContent.includes(marker),'Case saved');check((await request('cases',{kind:'reimbursement'})).rows.some(o=>o.entry===expense&&o.cents===6000),'Reimbursement UI creates partial receivable');
  const receipt=await seed('income',3000);await tool('07');await until(()=>host.textContent.includes(marker),'Case reload');click('关联真实回款');await searchPick('收入');await until(()=>field('本次本金／回款金额 / 元'),'Settlement form');field('本次本金／回款金额 / 元').value='30';click('确认分配');await until(()=>title()==='报销与代垫'&&host.textContent.includes('部分收回'),'Settlement saved');check((await request('detail',{entry:expense})).receivable===3000,'Reimbursement UI settles actual receipt once');
  await tool('08');await until(()=>field('新标签名称'),'Tags loaded');field('新标签名称').value=marker;click('创建标签');await until(()=>[...host.querySelectorAll('h3')].some(n=>n.textContent===marker),'Tag saved');check((await request('state')).tags.some(o=>o.label===marker),'Tag creation UI persists');
  const loan=await seed('income',50000);await tool('09');await until(()=>[...host.querySelectorAll('button')].some(b=>b.textContent==='从实际流水新建'),'Loans ready');click('从实际流水新建');await searchPick('收入');await until(()=>field('对方称呼'),'Loan form');field('对方称呼').value=marker;field('应收／本金 / 元').value='500';click('保存案件');await until(()=>title()==='借入与借出'&&host.textContent.includes('借入'),'Loan saved');check((await request('cases',{kind:'loans'})).rows.some(o=>o.entry===loan&&o.cents===50000),'Loan UI creates actual principal case');
  await tool('12');await until(()=>host.querySelectorAll('.finance-calendar button').length>27,'Calendar');check([...host.querySelectorAll('.finance-calendar button')].every(b=>b.getAttribute('aria-label').includes('元')),'Calendar has daily amounts and spoken labels');host.querySelector('.finance-calendar button.today').click();await until(()=>title()?.includes('当日流水')&&host.textContent.includes('与日历一致'),'Calendar day detail');check(true,'Calendar opens matching daily ledger');
  await tool('14');await until(()=>host.querySelectorAll('.layout-item').length===8,'Layout editor');const row=host.querySelector('.layout-item'),input=row.querySelector('input');input.click();click('取消');check(!(await request('layoutRead')),'Cancel layout does not persist');await tool('14');await until(()=>host.querySelectorAll('.layout-item').length===8,'Layout edit ready');host.querySelector('.layout-item[data-module=quick] input').click();click('完成');await until(async()=>JSON.parse((await request('layoutRead')).data).hidden.includes('quick'),'Layout saved');check(getComputedStyle(document.querySelector('[data-home-module=quick]')).display==='none','Home layout save hides selected module');
  await tool('15');click('创建目标');await until(()=>field('名称'),'Goal form');field('名称').value=marker;field('目标金额 / 元').value='4000';click('保存目标');await until(()=>title()==='储蓄目标'&&host.textContent.includes(marker),'Goal saved');click('增加分配');await until(()=>field('分配金额 / 元'),'Allocation form');field('分配金额 / 元').value='1000';click('确认分配');await until(()=>title()==='储蓄目标'&&host.textContent.includes('1000.00'),'Allocation saved');const g=(await request('state')).goals.find(o=>o.label===marker);check(g.cents===400000&&g.allocated===100000,'Goal UI saves 25 percent without a virtual payment');
  check(document.documentElement.scrollWidth<=innerWidth+1,'Finance screens fit phone width');check(!errors.length,'No finance UI runtime errors');
 }catch(e){failure=e.message;}
 finally{
  try{const history=await toolsRequest('history',{});for(const h of history.filter(h=>h.id>baseline&&!h.undone))await toolsRequest('undo',{operation:h.id});const records=await toolsRequest('page',{status:'confirmed',all:true,kind:'all',q:marker,groups:false,epoch:++epoch});check(records.total===0,'UI fixtures removed through undo');document.querySelector('[data-tab="finance"]').click();}
  catch(e){failure+=' Cleanup: '+e.message;}
  window.removeEventListener('error',error);window.polishResult=(failure?'FAIL: '+failure:'PASS: '+checks.length+' real-device finance UI checks')+'\n'+checks.join('\n');
 }
})();

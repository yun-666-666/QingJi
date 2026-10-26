 'use strict';
(async()=>{
 const host=document.getElementById('finance'),wait=ms=>new Promise(r=>setTimeout(r,ms));
 const errors=[],error=e=>errors.push(e.message);window.addEventListener('error',error);
 const check=(v,name)=>{if(!v)throw new Error(name);};
 async function until(fn,name){for(let i=0;i<100;i++){if(await fn())return;await wait(80);}throw new Error(name);}
 try{
  const before=JSON.stringify(await toolsRequest('history',{}));
  document.querySelector('[data-tab="finance"]').click();
  await until(()=>host.querySelectorAll('.finance-grid>button').length===12,'Twelve feature entries');
  const request=(op,args={})=>toolsRequest('finance.'+op,args);
  const state=await request('state');check(Array.isArray(state.tags)&&Array.isArray(state.goals),'Financial bridge');
  for(const number of ['01','05','07','08','09','12','14','15']){
   const b=[...host.querySelectorAll('.finance-grid button')].find(n=>n.textContent.startsWith(number));check(b,'Missing feature');b.click();await wait(250);check(!host.querySelector('.finance-error'),'Feature load error');
   host.querySelector('.finance-head button').click();await until(()=>host.querySelector('.finance-grid'),'Return to tools');
  }
  check(document.documentElement.scrollWidth<=innerWidth+1,'Phone width');
  check(before===JSON.stringify(await toolsRequest('history',{})),'Read-only review changed history');
  check(!errors.length,'JavaScript error');
  window.polishResult='PASS: Signed release starts, twelve entries visible, native finance reads and screens load, no history changes';
 }catch(e){window.polishResult='FAIL: '+e.message;}
 finally{window.removeEventListener('error',error);}
})();

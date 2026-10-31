'use strict';
(()=>{
 const $=id=>document.getElementById(id),button=$('heroBudget'),hero=button.closest('.hero');let usage=null,saving=false,requestVersion=0;
 const valid=s=>['A','B','D'].includes(s)?s:'A',money=n=>(n/100).toFixed(2),compact=n=>n>=10000000000?(n/10000000000).toFixed(2)+'亿':n>=1000000?(n/1000000).toFixed(2)+'万':money(n);
 function percent(r){return r>999?'999%+':(r<100?Math.min(99,Math.round(r)):r>100?Math.max(101,Math.round(r)):100)+'%';}
 let lastKey='',drawnStyle='',lastFill=0;
 function paint(value){usage=value&&Number.isSafeInteger(value.cents)&&Number.isSafeInteger(value.used)?{...value,cents:Math.max(0,value.cents),used:value.used,style:valid(value.style)}:null;
  const key=usage?[usage.cents,usage.used,usage.style,!!usage.income].join('|'):'none';if(key===lastKey)return;lastKey=key;
  const show=!!usage?.cents;hero.classList.toggle('has-budget',show);button.hidden=!show;document.querySelectorAll('[data-budget-style]').forEach(b=>{const selected=b.dataset.budgetStyle===valid(usage?.style);b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',selected);});if(!show){button.replaceChildren();drawnStyle='';lastFill=0;return;}
  const r=usage.used*100/usage.cents,fill=Math.max(0,Math.min(100,r)),over=r>100,left=Math.abs(usage.cents-usage.used),word=over?'超':'余',cls=over?'over':r===100?'full':r>=80?'near':'normal',style=valid(usage.style),p=percent(r);
  const label='<div class="bu-label"><span>月预算</span><em>'+p+'</em></div>',valueHtml='<div class="bu-value"><small>'+word+'</small>¥'+compact(left)+'</div>';button.className='budget-usage '+cls+(style==='B'?' bu-ring':'');
  const rebuild=drawnStyle!==style;if(rebuild){drawnStyle=style;lastFill=0;
   if(style==='B')button.innerHTML='<div class="bu-ring-graphic"><svg viewBox="0 0 44 44" aria-hidden="true"><circle class="track" cx="22" cy="22" r="18"/><circle class="fill" cx="22" cy="22" r="18" pathLength="100"/></svg><span></span></div><div class="bu-ring-copy"><div class="bu-label">月预算</div>'+valueHtml+'</div>';
   else button.innerHTML=label+(style==='D'?'<div class="bu-segments">'+Array.from({length:10},()=>'<i><b></b></i>').join('')+'</div>':'<div class="bu-rail"><i></i></div>')+valueHtml;
  }
  button.querySelector(style==='B'?'.bu-ring-graphic>span':'.bu-label em').textContent=p;const amount=button.querySelector('.bu-value'),amountHTML='<small>'+word+'</small>¥'+compact(left);if(amount.innerHTML!==amountHTML)amount.innerHTML=amountHTML;
  button.querySelectorAll('.bu-rail>i,.bu-segments b,.bu-ring-graphic .fill').forEach((el,i)=>{const ring=style==='B',to=style==='D'?Math.max(0,Math.min(100,(fill-i*10)*10)):fill,from=style==='D'?Math.max(0,Math.min(100,(lastFill-i*10)*10)):lastFill;if(ring)el.setAttribute('stroke-dasharray',to+' 100');else{el.style.width='100%';el.style.transform='scaleX('+to/100+')';}if(from!==to)window.motionProgress?.(el,from,to,ring);});lastFill=fill;button.setAttribute('aria-label','本月总预算（'+(usage.income?'净花销':'净支出')+'） '+money(usage.cents)+' 元，已用 '+money(usage.used)+' 元，'+(over?'超支':'剩余')+' '+money(left)+' 元，使用率 '+r.toFixed(1)+'%，点击详情');
 }
 const old=window.receive;window.receive=data=>{const result=old(data);if(Object.prototype.hasOwnProperty.call(data,'budgetUsage'))paint(data.budgetUsage);return result;};
 button.onclick=()=>{if(typeof Ledger!=='undefined')Ledger.budgetDetail();else window.toolsToast(button.getAttribute('aria-label'));};
 document.querySelectorAll('[data-budget-style]').forEach(b=>b.onclick=async()=>{if(saving)return;const style=b.dataset.budgetStyle,epoch=++requestVersion;saving=true;const buttons=[...document.querySelectorAll('[data-budget-style]')];buttons.forEach(x=>{x.disabled=true;x.setAttribute('aria-busy','true');});try{if(typeof Ledger!=='undefined')await window.toolsRequest('budgetStyle',{style});if(epoch!==requestVersion)return;paint({...usage,cents:usage?.cents||0,used:usage?.used||0,style});if(typeof Ledger!=='undefined')Ledger.refresh();}catch(e){window.toolsToast(e.message||'样式保存失败');}finally{saving=false;buttons.forEach(x=>{x.disabled=false;x.removeAttribute('aria-busy');});}});
 // Reserve the same six-digit amount (including currency, punctuation and minus)
 // for every period. Never measure the live amount or counter frames.
 const net=$('net'),row=net.parentElement;let sizeKey='',sizeFrame=0;
 function sizeAmount(){sizeFrame=0;if(document.hidden||!row.getClientRects().length)return;const width=row.clientWidth,narrow=innerWidth<=370,gap=narrow?6:9,count=narrow?54:60,ratio=narrow?1.07:1.18,slot=(width-gap*2-count)*ratio/(ratio+1),css=getComputedStyle(net),key=[width,narrow,css.fontFamily,css.fontWeight].join('|');if(width<=0||key===sizeKey)return;sizeKey=key;const sample=document.createElement('span');sample.style.cssText='position:fixed;visibility:hidden;white-space:pre;pointer-events:none;font-size:38px;letter-spacing:-1px';for(const prop of ['fontFamily','fontWeight','fontStyle','fontVariantNumeric'])sample.style[prop]=css[prop];sample.textContent='¥ -9,999.99';document.body.append(sample);const measured=sample.getBoundingClientRect().width;sample.remove();if(measured>0)net.style.setProperty('font-size',Math.max(1,Math.min(38,Math.floor(38*(slot-2)/measured)))+'px','important');}
 const queueSize=()=>{if(!sizeFrame&&!document.hidden)sizeFrame=requestAnimationFrame(sizeAmount);};
 if(typeof ResizeObserver!=='undefined')new ResizeObserver(queueSize).observe(row);window.addEventListener('resize',queueSize);document.addEventListener('visibilitychange',()=>{if(document.hidden){cancelAnimationFrame(sizeFrame);sizeFrame=0;}else queueSize();});document.fonts?.ready.then(()=>{sizeKey='';queueSize();});queueSize();
 window.paintBudgetUsage=paint;paint(null);
})();

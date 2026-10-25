'use strict';
(()=>{
 const root=document.documentElement,reduced=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
 // Motion-v3 spatial/effects tokens; the platform clock adapts to refresh rate.
 const schemes={a:{theme:820,page:440,base:360,fast:150,ease:'cubic-bezier(.22,1,.36,1)',press:.92},b:{theme:900,page:540,base:440,fast:180,ease:'cubic-bezier(.2,1.45,.38,1)',press:.88},c:{theme:760,page:380,base:300,fast:120,ease:'cubic-bezier(.22,.65,.25,1)',press:1}};
 let scheme='a',themeRun=null;const active=new Set(),slots=new WeakMap(),numbers=new Map();
 try{const saved=typeof Ledger!=='undefined'?Ledger.getMotion():localStorage.getItem('qingji-motion');if(schemes[saved])scheme=saved;}catch(_){}
 const cfg=()=>schemes[scheme];
 function setScheme(value){if(!schemes[value])return;finishTheme();scheme=value;root.dataset.motion=value;root.style.setProperty('--motion-ease',cfg().ease);root.style.setProperty('--motion-base',cfg().base+'ms');root.style.setProperty('--motion-fast',cfg().fast+'ms');root.style.setProperty('--motion-press',cfg().press);document.querySelectorAll('[data-motion-choice]').forEach(b=>{const selected=b.dataset.motionChoice===value;b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));});}
 function animate(el,frames,key='base',extra={}){if(!el||reduced()||!el.animate)return null;const a=el.animate(frames,{duration:cfg()[key],easing:cfg().ease,...extra});active.add(a);a.finished.catch(()=>{}).finally(()=>active.delete(a));return a;}
 function pose(dir=1){return scheme==='a'?'translateY(12px) scale(.88)':scheme==='b'?'translateY(76px) scale(.96)':`translateX(${110*dir}px)`;}
 function enter(el,key='base',dir=1){if(!el)return;slots.get(el)?.cancel();const a=animate(el,[{opacity:0,transform:pose(dir)},{opacity:1,transform:'none'}],key);if(a)slots.set(el,a);}
 window.motionPage=el=>{finishTheme();enter(el,'page');window.motionIndicators();};window.motionEnter=enter;
 window.motionMoney=(el,cents,prefix='')=>{
  const format=n=>prefix+(n/100).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}),old=numbers.get(el);
  if(old?.target===cents)return;if(old)cancelAnimationFrame(old.frame);
  const state={target:cents,value:old?.value??cents,frame:0,format};numbers.set(el,state);
  if(!old||reduced()||!el.getClientRects().length){state.value=cents;el.textContent=format(cents);return;}
  const from=state.value,start=performance.now(),ms=cfg().base;
  function tick(now){const p=Math.min(1,(now-start)/ms);state.value=Math.round(from+(cents-from)*(1-(1-p)**3));const text=format(state.value);if(el.textContent!==text)el.textContent=text;if(p<1)state.frame=requestAnimationFrame(tick);}
  state.frame=requestAnimationFrame(tick);
 };
 // One layout commit, followed by transform-only FLIP, bounded to visible rows.
 window.motionLayout=(parent,change)=>{
  if(reduced()||!parent.getClientRects().length){change();return;}
  const before=new Map([...parent.children].filter(n=>{const r=n.getBoundingClientRect();return r.bottom>0&&r.top<innerHeight;}).map(n=>[n,n.getBoundingClientRect()]));change();
  for(const [node,r] of before)if(!node.isConnected){const ghost=node.cloneNode(true);ghost.removeAttribute('id');ghost.querySelectorAll('[id]').forEach(n=>n.removeAttribute('id'));ghost.inert=true;ghost.setAttribute('aria-hidden','true');ghost.classList.add('motion-ghost');ghost.style.cssText=`position:fixed;left:${r.left}px;top:${r.top}px;width:${r.width}px;height:${r.height}px;margin:0;pointer-events:none;z-index:30`;document.body.append(ghost);const a=animate(ghost,[{opacity:1,transform:'none'},{opacity:0,transform:scheme==='c'?'translateX(-80px)':scheme==='b'?'translateY(-24px)':'scale(.9)'}]);if(a)a.finished.catch(()=>{}).finally(()=>ghost.remove());else ghost.remove();}
  for(const node of parent.children){const r=node.getBoundingClientRect();if(r.bottom<0||r.top>innerHeight)continue;const old=before.get(node);if(!old)enter(node);else if(Math.abs(old.top-r.top)>.5)animate(node,[{transform:`translateY(${old.top-r.top}px)`},{transform:'none'}]);}
 };
 window.motionToggle=(d,open)=>{const target=open??!d.open;if(target===d.open)return;window.motionLayout(d.parentElement,()=>{d.open=target;d.firstElementChild?.setAttribute('aria-expanded',String(target));});const body=d.querySelector(':scope > .group-rows,:scope > .details-body,:scope > .fold-body,:scope > .book-tool-body');if(target&&body)enter(body);};
 const layers=new WeakMap();
 window.setLayerVisible=(el,visible,done)=>{
  layers.get(el)?.forEach(a=>a.cancel());el.classList.remove('motion-closing');if(visible){el.hidden=false;window.syncLayers?.();}else if(el.hidden){done?.();return;}else el.classList.add('motion-closing');
  const sheet=el.firstElementChild,shift=scheme==='a'?'translateY(40px) scale(.72)':scheme==='b'?'translateY(110px) scale(.96)':'translateX(110px)';
  const list=[animate(el,visible?[{opacity:0},{opacity:1}]:[{opacity:1},{opacity:0}],'fast'),animate(sheet,visible?[{transform:shift,opacity:.3},{transform:'none',opacity:1}]:[{transform:'none',opacity:1},{transform:shift,opacity:0}],visible?'base':'fast')].filter(Boolean);layers.set(el,list);
  const finish=()=>{if(layers.get(el)!==list)return;layers.delete(el);el.classList.remove('motion-closing');if(!visible)el.hidden=true;window.syncLayers?.();done?.();};if(list.length)Promise.all(list.map(a=>a.finished.catch(()=>{}))).then(finish);else finish();
 };
 function finishTheme(){if(!themeRun)return;const run=themeRun;themeRun=null;cancelAnimationFrame(run.chromeFrame);delete root.dataset.themeRunning;run.change();if(typeof Ledger!=='undefined')Ledger.endTheme();run.animations.forEach(a=>a.cancel());run.decor?.remove();run.transition?.skipTransition();}
 // Follow the actual eased reveal, including a mid-flight reversal. The bars use
 // coverage at their own edge rather than a separate timer or the final theme.
 function syncThemeChrome(run,reveal,x,y,r){
  if(typeof Ledger==='undefined')return;
  const clamp=v=>Math.max(0,Math.min(1,v));
  const circleCoverage=(radius,edge)=>{const reach=Math.sqrt(Math.max(0,radius*radius-(edge-y)**2));return (Math.min(innerWidth,x+reach)-Math.max(0,x-reach))/innerWidth;};
  function tick(){
   if(themeRun!==run)return;
   const progress=reveal.effect.getComputedTiming().progress??0;let top=progress,bottom=progress;
   if(scheme==='a'){top=circleCoverage(r*progress,0);bottom=circleCoverage(r*progress,innerHeight);}
   if(scheme==='c'){top=clamp(-.35+2.1*progress);bottom=clamp(-.75+2.1*progress);}
   Ledger.themeProgress(top,bottom);run.chromeFrame=requestAnimationFrame(tick);
  }
  tick();
 }
 function decorate(run){
  if(scheme==='a')return;
  const night=run.to==='dark',el=document.createElement('div');el.className='theme-scene '+(night?'night':'day')+' scene-'+scheme;el.setAttribute('aria-hidden','true');
  // Light lives on the moving boundary; small trails dissolve into the new theme.
  el.innerHTML='<div class="theme-beam beam-left"></div><div class="theme-beam beam-right"></div>'+Array.from({length:night?18:8},(_,i)=>'<i class="theme-spark" style="--x:'+(6+(i*37)%89)+'%;--y:'+(7+(i*23)%82)+'%"></i>').join('');
  document.body.append(el);run.decor=el;
  [...el.children].forEach((node,i)=>node.style.viewTransitionName='qj-fx-'+i);
  run.effects=()=>{
   const options={duration:cfg().theme,easing:'cubic-bezier(.3,0,.2,1)',fill:'both'};
   const play=(node,frames)=>run.animations.push(root.animate(frames,{...options,pseudoElement:'::view-transition-new('+node.style.viewTransitionName+')'}));
   const slope=Math.atan(-.4*innerWidth/innerHeight)*180/Math.PI;
   for(const [i,beam] of [...el.querySelectorAll('.theme-beam')].entries()){
    const from=scheme==='b'?'translateX(0)':`translateX(-35vw) skewX(${slope}deg)`;
    const to=scheme==='b'?`translateX(${i?50:-50}vw)`:`translateX(175vw) skewX(${slope}deg)`;
    play(beam,[{opacity:0,transform:from},{opacity:1,offset:.16},{opacity:.8,offset:.68},{opacity:0,transform:to}]);
   }
   el.querySelectorAll('.theme-spark').forEach((spark,i)=>{
    const left=parseFloat(spark.style.getPropertyValue('--x'))<50;
    let drift=`translate(${left?-18:18}px,-12px) scale(.5)`;
    if(scheme==='c')drift=night?'translate(65px,-18px) scaleX(1.5)':'translate(40px,-30px)';
    play(spark,[{opacity:0,transform:'scale(.2)'},{opacity:0,offset:.18+(i%4)*.045},{opacity:night?.75:.5,transform:'none',offset:.48+(i%4)*.04},{opacity:0,transform:drift}]);
   });
  };
 }
 window.motionTheme=(to,change)=>{
  if(themeRun){themeRun.toTarget=to;themeRun.change=change;themeRun.animations.forEach(a=>{a.updatePlaybackRate(to===themeRun.to?1:-1);a.play();});return;}
  if(reduced()||!document.startViewTransition){change();return;}
  active.forEach(a=>a.finish());for(const [el,n] of numbers){cancelAnimationFrame(n.frame);n.value=n.target;el.textContent=n.format(n.target);}
  const rect=document.getElementById('themeToggle').getBoundingClientRect(),x=rect.left+rect.width/2,y=rect.top+rect.height/2,r=Math.hypot(Math.max(x,innerWidth-x),Math.max(y,innerHeight-y));
  const paths=scheme==='a'?[`circle(0px at ${x}px ${y}px)`,`circle(${r}px at ${x}px ${y}px)`]:scheme==='b'?['inset(0 50% 0 50% round 22px)','inset(0 0 0 0 round 0px)']:['polygon(-35% 0,-35% 0,-75% 100%,-75% 100%)','polygon(-35% 0,175% 0,135% 100%,-75% 100%)'];
  const run={to,toTarget:to,change,trigger:rect,animations:[],decor:null,transition:null,chromeFrame:0};themeRun=run;root.dataset.themeRunning='true';
  // Keep the old page outside C's moving clip. Only transform composited snapshots;
  // animating a full-screen polygon forces expensive clipping on Android WebView.
  const bodyStyle=document.body.style,background=bodyStyle.backgroundColor;
  if(scheme==='c'){bodyStyle.viewTransitionName='qj-before';bodyStyle.backgroundColor=getComputedStyle(root).backgroundColor;}
  const restoreBody=()=>{if(scheme==='c'){bodyStyle.removeProperty('view-transition-name');bodyStyle.backgroundColor=background;}};
  if(typeof Ledger!=='undefined')Ledger.beginTheme(to);const transition=document.startViewTransition(()=>{restoreBody();change();decorate(run);});run.transition=transition;
  transition.ready.then(()=>{
   if(themeRun!==run)return;
   const options={duration:cfg().theme,easing:'cubic-bezier(.3,0,.2,1)',fill:'both'};let reveal;
   if(scheme==='c'){
    const slope=Math.atan(-.4*innerWidth/innerHeight)*180/Math.PI;
    reveal=root.animate([-245,-35].map(x=>({transform:`translateX(${x}vw) skewX(${slope}deg)`})),{...options,pseudoElement:'::view-transition-image-pair(root)'});
    run.animations.push(root.animate([245,35].map(x=>({transform:`skewX(${-slope}deg) translateX(${x}vw)`})),{...options,pseudoElement:'::view-transition-new(root)'}));
   }else reveal=root.animate([{clipPath:paths[0]},{clipPath:paths[1]}],{...options,pseudoElement:'::view-transition-new(root)'});
   run.animations.push(reveal);run.effects?.();syncThemeChrome(run,reveal,x,y,r);if(run.toTarget!==run.to)run.animations.forEach(a=>{a.currentTime=0;a.reverse();});reveal.finished.then(()=>{if(themeRun===run)finishTheme();}).catch(()=>{});
  }).catch(()=>{if(themeRun===run)finishTheme();});transition.finished.finally(()=>{restoreBody();if(themeRun===run)finishTheme();});
 };
 window.motionIndicators=()=>{document.querySelectorAll('.period-tabs,nav').forEach(container=>{const selected=container.querySelector('button.selected,button.active');if(!selected)return;let marker=container.querySelector('.motion-indicator');if(!marker){marker=document.createElement('i');marker.className='motion-indicator';marker.setAttribute('aria-hidden','true');container.prepend(marker);}marker.style.width=selected.offsetWidth+'px';marker.style.height=selected.offsetHeight+'px';marker.style.top=selected.offsetTop+'px';const to=`translateX(${selected.offsetLeft}px)`;animate(marker,[{transform:getComputedStyle(marker).transform},{transform:to}]);marker.style.transform=to;});};
 document.addEventListener('DOMContentLoaded',()=>{
  setScheme(scheme);document.querySelectorAll('#settings details').forEach(d=>{d.open=false;const s=d.firstElementChild;s.setAttribute('aria-expanded','false');if(!d.querySelector(':scope > .details-body')){const body=document.createElement('div');body.className='fold-body';while(s.nextSibling)body.append(s.nextSibling);d.append(body);}d.addEventListener('toggle',()=>s.setAttribute('aria-expanded',String(d.open)));});
  document.querySelectorAll('[data-motion-choice]').forEach(b=>b.onclick=()=>{setScheme(b.dataset.motionChoice);try{if(typeof Ledger!=='undefined')Ledger.motion(scheme);else localStorage.setItem('qingji-motion',scheme);}catch(_){}enter(document.getElementById('motionSample'));});
  const toast=document.getElementById('toast');new MutationObserver(()=>{if(!toast.hidden)enter(toast);}).observe(toast,{attributes:true,attributeFilter:['hidden'],childList:true});
  for(const id of ['editError','rangeError','dateError']){const error=document.getElementById(id);new MutationObserver(()=>{if(error.textContent)animate(error,[{transform:'translateX(0)'},{transform:'translateX(-6px)'},{transform:'translateX(5px)'},{transform:'none'}]);}).observe(error,{childList:true,characterData:true,subtree:true});}window.motionIndicators();
 });
 document.addEventListener('click',e=>{if(themeRun&&e.target===root){const r=themeRun.trigger;if(e.clientX>=r.left&&e.clientX<=r.right&&e.clientY>=r.top&&e.clientY<=r.bottom)document.getElementById('themeToggle').click();return;}const s=e.target.closest('summary');if(s&&!e.defaultPrevented&&s.parentElement.matches('#settings details,.merchant-group')){e.preventDefault();window.motionToggle(s.parentElement);}const b=e.target.closest('.choice-row button,[data-kind],[data-period],.category-area');if(b){enter(b,'fast');requestAnimationFrame(window.motionIndicators);}});
 document.addEventListener('visibilitychange',()=>{if(document.hidden){finishTheme();active.forEach(a=>a.finish());}});window.addEventListener('resize',()=>{finishTheme();window.motionIndicators();});setScheme(scheme);
})();

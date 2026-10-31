'use strict';
(()=>{
 const root=document.documentElement,reduced=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
 // Motion-v3 spatial/effects tokens; the platform clock adapts to refresh rate.
 const schemes={a:{theme:620,page:240,base:240,fast:130,ease:'cubic-bezier(.22,1,.36,1)',press:.975},b:{theme:700,page:280,base:280,fast:150,ease:'cubic-bezier(.2,.9,.3,1)',press:.965},c:{theme:540,page:200,base:200,fast:110,ease:'cubic-bezier(.22,.65,.25,1)',press:.985}};
 let scheme='a',themeRun=null,numberFrame=0;const active=new Set(),slots=new WeakMap(),numbers=new WeakMap(),counting=new Map(),ghosts=new Set(),pages=new Set();
 const numberFormat=new Intl.NumberFormat('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});
 const visible=el=>!!el?.isConnected&&!!el.getClientRects().length;
 function parentMoving(el){for(let n=el?.parentElement;n;n=n.parentElement)if(slots.has(n))return true;return false;}
 function cancelTree(el){for(const a of active){const n=a.effect?.target;if(n&&(n===el||el.contains(n)))a.cancel();}}
 function stopPages(){finishFolds();for(const el of pages)cancelTree(el);pages.clear();}
 function endNumber(el,n){counting.delete(el);n.value=n.target;el.textContent=n.format(n.target);delete el.dataset.motionCounting;el.dispatchEvent(new Event('motionmoneyend'));}
 function settleNumbers(){cancelAnimationFrame(numberFrame);numberFrame=0;for(const [el,n] of counting)endNumber(el,n);}
 function numberTick(now){numberFrame=0;for(const [el,n] of counting){if(!el.isConnected||el.closest('[hidden]')||document.hidden||reduced()){endNumber(el,n);continue;}const p=Math.max(0,Math.min(1,(now-n.start)/n.ms));n.value=Math.round(n.from+(n.target-n.from)*(1-(1-p)**3));const text=n.format(n.value);if(el.textContent!==text)el.textContent=text;if(p===1)endNumber(el,n);}if(counting.size)numberFrame=requestAnimationFrame(numberTick);}
 try{const saved=typeof Ledger!=='undefined'?Ledger.getMotion():localStorage.getItem('qingji-motion');if(schemes[saved])scheme=saved;}catch(_){}
 const cfg=()=>schemes[scheme];
 function setScheme(value){if(!schemes[value])return;finishTheme();finishActive();scheme=value;root.dataset.motion=value;root.style.setProperty('--motion-ease',cfg().ease);root.style.setProperty('--motion-base',cfg().base+'ms');root.style.setProperty('--motion-fast',cfg().fast+'ms');root.style.setProperty('--motion-press',cfg().press);document.querySelectorAll('[data-motion-choice]').forEach(b=>{const selected=b.dataset.motionChoice===value;b.classList.toggle('selected',selected);b.setAttribute('aria-pressed',String(selected));});}
 function animate(el,frames,key='base',extra={}){if(!el)return null;slots.get(el)?.cancel();if(reduced()||document.hidden||!el.animate||!el.isConnected)return null;let a;try{a=el.animate(frames,{duration:cfg()[key],easing:cfg().ease,fill:'backwards',...extra});}catch(_){return null;}slots.set(el,a);active.add(a);const clean=()=>{active.delete(a);if(slots.get(el)===a)slots.delete(el);};a.finished.then(clean,clean);return a;}
 function pose(dir=1){return scheme==='a'?'translateY(7px)':scheme==='b'?'translateY(12px) scale(.99)':`translateX(${12*dir}px)`;}
 function enter(el,key='base',dir=1,delay=0){if(!visible(el)||parentMoving(el)||themeRun)return;const r=el.getBoundingClientRect();if(r.bottom<=0||r.top>=innerHeight)return;const old=slots.get(el),s=old?getComputedStyle(el):null;animate(el,[{opacity:s?s.opacity:0,transform:s?s.transform:pose(dir)},{opacity:1,transform:'none'}],key,{delay});}
 window.motionPage=(el,dir=1)=>{finishTheme();stopPages();settleNumbers();if(el){cancelTree(el);pages.add(el);animate(el,[{opacity:.5,transform:`translateX(${dir*(scheme==='b'?20:14)}px)`},{opacity:1,transform:'none'}],'page');}window.motionIndicators();};window.motionEnter=enter;
 window.motionMoney=(el,cents,prefix='')=>{
  if(!el||!Number.isSafeInteger(cents))return;const format=n=>prefix+numberFormat.format(n/100),old=numbers.get(el);
  if(old?.target===cents&&old.format(cents)===format(cents))return;
  const state={target:cents,value:old?.value??cents,from:old?.value??cents,start:performance.now(),ms:cfg().base,format};numbers.set(el,state);
  if(!old||reduced()||document.hidden||!visible(el)){endNumber(el,state);return;}
  counting.set(el,state);el.dataset.motionCounting='true';el.dispatchEvent(new CustomEvent('motionmoneystart',{detail:{from:format(state.from),to:format(cents)}}));
  if(!numberFrame)numberFrame=requestAnimationFrame(numberTick);
 };
 // Layout is committed once. Read both batches before starting transform-only FLIP.
 function positions(parent){const out=new Map();for(const n of parent.children){const r=n.getBoundingClientRect();if(r.width&&r.bottom>0&&r.top<innerHeight){out.set(n,r);if(out.size===24)break;}}return out;}
 function ghostRow(node,r){if(ghosts.size>=6||node.querySelectorAll('*').length>100)return;const ghost=node.cloneNode(true);ghost.removeAttribute('id');ghost.querySelectorAll('[id]').forEach(n=>n.removeAttribute('id'));ghost.inert=true;ghost.setAttribute('aria-hidden','true');ghost.classList.add('motion-ghost');ghost.style.cssText=`position:fixed;left:${r.left}px;top:${r.top}px;width:${r.width}px;height:${r.height}px;margin:0;pointer-events:none;z-index:9`;document.body.append(ghost);ghosts.add(ghost);const clean=()=>{ghost.remove();ghosts.delete(ghost);};const a=animate(ghost,[{opacity:.8,transform:'none'},{opacity:0,transform:scheme==='c'?'translateX(-12px)':'translateY(-4px)'}],'fast');if(a)a.finished.then(clean,clean);else clean();}
 window.motionLayout=(parent,change)=>{
  if(reduced()||document.hidden||!visible(parent)||parentMoving(parent)||themeRun){change();return;}
  if(parent.getAttribute('aria-busy')==='true'){cancelTree(parent);change();animate(parent,[{opacity:.72},{opacity:1}],'fast');return;}
  const before=positions(parent);cancelTree(parent);change();const after=positions(parent);
  for(const [node,r] of before)if(!node.isConnected)ghostRow(node,r);
  let added=0;for(const [node,r] of after){const old=before.get(node);if(!old)enter(node,'base',1,Math.min(added++,4)*16);else{const dx=old.left-r.left,dy=old.top-r.top;if(Math.abs(dx)+Math.abs(dy)>.5)animate(node,[{transform:`translate(${dx}px,${dy}px)`},{transform:'none'}]);}}
 };
 // A fold must continuously move its own boundary AND following content.
 // A compositor-only sibling FLIP cannot stand in for a real accordion.
 const folds=new WeakMap(),folding=new Set();
 window.motionExpanded=d=>folds.get(d)?.target??d.open;
 function closeFold(run){const {d,target}=run;if(folds.get(d)!==run)return;folds.delete(d);folding.delete(run);run.observer?.disconnect();cancelAnimationFrame(run.frame);run.animation?.cancel();d.open=target;d.style.removeProperty('height');d.classList.remove('motion-folding');if(!folding.size)root.classList.remove('motion-fold-active');}
 function finishFolds(parent){for(const run of [...folding])if(!parent||parent.contains(run.d))closeFold(run);}
 window.motionToggle=(d,open)=>{
  if(!d)return;const previous=folds.get(d),target=open??!window.motionExpanded(d);if(target===window.motionExpanded(d))return;
  // Finish only an expanding ancestor before starting a nested fold; don't run two height drivers on one subtree.
  for(const run of [...folding])if(run.d!==d&&(run.d.contains(d)||d.contains(run.d)))closeFold(run);
  const from=d.getBoundingClientRect().height,canMove=!reduced()&&!document.hidden&&visible(d.parentElement);
  if(d.children.length>2){const box=document.createElement('div');box.className='motion-fold-content';while(d.firstElementChild.nextSibling)box.append(d.firstElementChild.nextSibling);d.append(box);}
  previous?.observer?.disconnect();cancelAnimationFrame(previous?.frame);if(previous)folding.delete(previous);previous?.animation?.cancel();cancelTree(d);d.style.removeProperty('height');d.open=true;
  const body=d.children[1],summary=d.firstElementChild,css=getComputedStyle(d),px=v=>parseFloat(v)||0;
  const closed=(summary?.getClientRects().length?summary.getBoundingClientRect().height:0)+px(css.paddingTop)+px(css.paddingBottom)+px(css.borderTopWidth)+px(css.borderBottomWidth);
  const full=d.getBoundingClientRect().height,run={d,target,animation:null,observer:null,frame:0,to:target?full:closed};folds.set(d,run);folding.add(run);
  summary?.setAttribute('aria-expanded',String(target));d.dataset.expanded=String(target);
  const play=(start,end)=>{const previousAnimation=run.animation;run.to=end;d.style.height=end+'px';run.animation=animate(d,[{height:start+'px'},{height:end+'px'}],'base',{duration:Math.max(160,Math.min(340,cfg().base+Math.abs(end-start)*.045)),easing:'cubic-bezier(.25,.1,.25,1)',fill:'both'});previousAnimation?.cancel();const a=run.animation;if(a){const finish=()=>{if(run.animation===a)closeFold(run);};a.finished.then(finish,finish);}else closeFold(run);};
  if(!canMove||Math.abs(from-run.to)<.5){closeFold(run);return;}
  d.classList.add('motion-folding');root.classList.add('motion-fold-active');play(from,run.to);
  // Async merchant rows / overview data may arrive while opening. Retarget from the displayed height, never jump at completion.
  if(target&&body&&typeof ResizeObserver!=='undefined'){let size=body.getBoundingClientRect().height;run.observer=new ResizeObserver(()=>{const next=body.getBoundingClientRect().height;if(Math.abs(next-size)<.5)return;const delta=next-size;size=next;if(folds.get(d)!==run)return;run.pending=(run.pending||0)+delta;if(run.frame)return;run.frame=requestAnimationFrame(()=>{run.frame=0;if(folds.get(d)!==run)return;const height=d.getBoundingClientRect().height,end=run.to+run.pending;run.pending=0;play(height,end);});});run.observer.observe(body);}
 };
 const notices=new WeakMap();
 window.motionToast=(el,show)=>{const run={};notices.set(el,run);if(show){el.hidden=false;enter(el,'fast');return;}const a=animate(el,[{opacity:getComputedStyle(el).opacity,transform:'none'},{opacity:0,transform:'translateY(5px)'}],'fast');const finish=()=>{if(notices.get(el)===run){notices.delete(el);el.hidden=true;}};if(a)a.finished.then(finish,finish);else finish();};
 const layers=new WeakMap();
 window.setLayerVisible=(el,show,done)=>{
  if(!el){done?.();return;}const previous=layers.get(el);if(previous?.show===show){if(done)previous.callbacks.push(done);return;}
  const sheet=el.firstElementChild,wasHidden=el.hidden;let backdrop=el.querySelector(':scope > .motion-backdrop');
  if(!backdrop){backdrop=document.createElement('i');backdrop.className='motion-backdrop';backdrop.setAttribute('aria-hidden','true');el.append(backdrop);el.classList.add('motion-layer');}
  const s=previous||el.dataset.sheetDismiss?getComputedStyle(sheet):null,b=previous?getComputedStyle(backdrop).opacity:null,start=s?{opacity:s.opacity,transform:s.transform}:null;
  previous?.animations.forEach(a=>a.cancel());el.classList.remove('motion-closing');
  if(show){stopPages();el.hidden=false;window.syncLayers?.();}else if(wasHidden){done?.();return;}else el.classList.add('motion-closing');
  const centered=!sheet.classList.contains('sheet')||innerWidth>=760,shift=el.dataset.sheetDismiss?'translateY(100%)':centered?'translateY(8px) scale(.975)':scheme==='c'?'translateY(20px)':'translateY(32px)';
  const run={show,animations:[],callbacks:done?[done]:[]};layers.set(el,run);
  run.animations=[animate(backdrop,[{opacity:b??(show?0:1)},{opacity:show?1:0}],'fast'),animate(sheet,[start||{transform:show?shift:'none',opacity:show?0:1},{transform:show?'none':shift,opacity:show?1:0}],show?'base':'fast')].filter(Boolean);
  const finish=()=>{if(layers.get(el)!==run)return;layers.delete(el);el.classList.remove('motion-closing');if(!show)el.hidden=true;window.syncLayers?.();run.callbacks.forEach(fn=>fn());};if(run.animations.length)Promise.all(run.animations.map(a=>a.finished.catch(()=>{}))).then(finish);else finish();
 };
 function finishTheme(){if(!themeRun)return;const run=themeRun;themeRun=null;clearTimeout(run.timer);cancelAnimationFrame(run.chromeFrame);run.restore?.();delete root.dataset.themeRunning;try{run.change();}finally{if(typeof Ledger!=='undefined')Ledger.endTheme?.();run.animations.forEach(a=>a.cancel());run.decor?.remove();run.transition?.skipTransition();}}
 function finishActive(){finishFolds();for(const a of active)try{a.finish();}catch(_){a.cancel();}settleNumbers();}
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
   top=Math.max(0,Math.min(1,top));bottom=Math.max(0,Math.min(1,bottom));const key=Math.round(top*255)+'|'+Math.round(bottom*255);if(key!==run.chromeKey){run.chromeKey=key;Ledger.themeProgress(top,bottom);}if(progress<1&&themeRun===run)run.chromeFrame=requestAnimationFrame(tick);
  }
  tick();
 }
 function decorate(run){
  if(scheme==='a')return;
  const night=run.to==='dark',el=document.createElement('div');el.className='theme-scene '+(night?'night':'day')+' scene-'+scheme;el.setAttribute('aria-hidden','true');
  // Light lives on the moving boundary; small trails dissolve into the new theme.
  el.innerHTML='<div class="theme-beam beam-left"></div><div class="theme-beam beam-right"></div>'+Array.from({length:night?6:4},(_,i)=>'<i class="theme-spark" style="--x:'+(6+(i*37)%89)+'%;--y:'+(7+(i*23)%82)+'%"></i>').join('');
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
  if(reduced()||document.hidden){change();return;}
  if(!document.startViewTransition||!root.animate){change();const page=document.querySelector('main>section:not([hidden])');enter(page,'fast');return;}
  finishActive();
  const rect=document.getElementById('themeToggle').getBoundingClientRect(),x=rect.left+rect.width/2,y=rect.top+rect.height/2,r=Math.hypot(Math.max(x,innerWidth-x),Math.max(y,innerHeight-y));
  const paths=scheme==='a'?[`circle(0px at ${x}px ${y}px)`,`circle(${r}px at ${x}px ${y}px)`]:scheme==='b'?['inset(0 50% 0 50% round 22px)','inset(0 0 0 0 round 0px)']:['polygon(-35% 0,-35% 0,-75% 100%,-75% 100%)','polygon(-35% 0,175% 0,135% 100%,-75% 100%)'];
  const run={to,toTarget:to,change,trigger:rect,animations:[],decor:null,transition:null,chromeFrame:0};themeRun=run;root.dataset.themeRunning='true';
  // Keep the old page outside C's moving clip. Only transform composited snapshots;
  // animating a full-screen polygon forces expensive clipping on Android WebView.
  const bodyStyle=document.body.style,background=bodyStyle.backgroundColor,diagonal=scheme==='c';
  if(diagonal){bodyStyle.viewTransitionName='qj-before';bodyStyle.backgroundColor=getComputedStyle(root).backgroundColor;}
  let restored=false;const restoreBody=()=>{if(restored)return;restored=true;if(diagonal){bodyStyle.removeProperty('view-transition-name');bodyStyle.backgroundColor=background;}};run.restore=restoreBody;
  if(typeof Ledger!=='undefined')Ledger.beginTheme?.(to);let transition;try{transition=document.startViewTransition(()=>{restoreBody();if(themeRun!==run)return;change();decorate(run);});}catch(_){finishTheme();return;}run.transition=transition;run.timer=setTimeout(()=>{if(themeRun===run)finishTheme();},cfg().theme+1200);
  transition.ready.then(()=>{
   if(themeRun!==run)return;
   const options={duration:cfg().theme,easing:'cubic-bezier(.3,0,.2,1)',fill:'both'};let reveal;
   if(scheme==='c'){
    const slope=Math.atan(-.4*innerWidth/innerHeight)*180/Math.PI;
    reveal=root.animate([-245,-35].map(x=>({transform:`translateX(${x}vw) skewX(${slope}deg)`})),{...options,pseudoElement:'::view-transition-image-pair(root)'});
    run.animations.push(root.animate([245,35].map(x=>({transform:`skewX(${-slope}deg) translateX(${x}vw)`})),{...options,pseudoElement:'::view-transition-new(root)'}));
   }else reveal=root.animate([{clipPath:paths[0]},{clipPath:paths[1]}],{...options,pseudoElement:'::view-transition-new(root)'});
   run.animations.push(reveal);run.effects?.();syncThemeChrome(run,reveal,x,y,r);if(run.toTarget!==run.to)run.animations.forEach(a=>{a.currentTime=0;a.reverse();});reveal.finished.then(()=>{if(themeRun===run)finishTheme();}).catch(()=>{});
  }).catch(()=>{if(themeRun===run)finishTheme();});const complete=()=>{restoreBody();if(themeRun===run)finishTheme();};transition.finished.then(complete,complete);
 };
 let indicatorFrame=0;
 // Three equal segments: CSS owns the interpolation, with no geometry reads or RAF queue on taps.
 window.motionIndicators=()=>{
  document.querySelectorAll('nav>.motion-indicator').forEach(n=>n.remove());
  document.querySelectorAll('.period-tabs').forEach(container=>{const buttons=[...container.querySelectorAll('button[data-period]')],index=buttons.findIndex(b=>b.classList.contains('selected'));if(index<0)return;let marker=container.querySelector('.motion-indicator');if(!marker){marker=document.createElement('span');marker.className='motion-indicator';marker.setAttribute('aria-hidden','true');marker.style.transform=`translateX(${index*100}%)`;container.prepend(marker);container.classList.add('motion-segmented');}else if(marker.dataset.index!==String(index))marker.style.transform=`translateX(${index*100}%)`;marker.dataset.index=String(index);});
 };
 window.motionProgress=(el,from,to,ring=false)=>{if(!visible(el)||parentMoving(el)||themeRun)return;const previous=slots.get(el),s=previous?getComputedStyle(el):null;const frames=ring?[{strokeDasharray:s?s.strokeDasharray:from+' 100'},{strokeDasharray:to+' 100'}]:[{transform:s?s.transform:`scaleX(${from/100})`},{transform:`scaleX(${to/100})`}];animate(el,frames);};
 function settle(){finishTheme();finishActive();cancelAnimationFrame(indicatorFrame);indicatorFrame=0;for(const ghost of ghosts)ghost.remove();ghosts.clear();}
 document.addEventListener('DOMContentLoaded',()=>{
  setScheme(scheme);document.querySelectorAll('#settings details').forEach(d=>{d.open=false;const s=d.firstElementChild;s.setAttribute('aria-expanded','false');if(!d.querySelector(':scope > .details-body')){const body=document.createElement('div');body.className='fold-body';while(s.nextSibling)body.append(s.nextSibling);d.append(body);}d.addEventListener('toggle',()=>{if(!folds.has(d)){s.setAttribute('aria-expanded',String(d.open));d.dataset.expanded=String(d.open);}});});
  document.querySelectorAll('[data-motion-choice]').forEach(b=>b.onclick=()=>{setScheme(b.dataset.motionChoice);try{if(typeof Ledger!=='undefined')Ledger.motion(scheme);else localStorage.setItem('qingji-motion',scheme);}catch(_){}enter(document.getElementById('motionSample'));});
  // Toast lifecycle is explicit so repeated messages can reverse an exit without flashing.
  for(const id of ['editError','rangeError','dateError']){const error=document.getElementById(id);new MutationObserver(()=>{if(error.textContent)animate(error,[{transform:'translateX(0)'},{transform:'translateX(-6px)'},{transform:'translateX(5px)'},{transform:'none'}]);}).observe(error,{childList:true,characterData:true,subtree:true});}window.motionIndicators();
 });
 document.addEventListener('click',e=>{if(themeRun&&e.target===root){const r=themeRun.trigger;if(e.clientX>=r.left&&e.clientX<=r.right&&e.clientY>=r.top&&e.clientY<=r.bottom)document.getElementById('themeToggle').click();return;}const s=e.target.closest('summary');if(s&&!e.defaultPrevented&&s.parentElement.matches('details')){e.preventDefault();window.motionToggle(s.parentElement);}const b=e.target.closest('.choice-row button,[data-kind],[data-period],.category-area');if(b)window.motionIndicators();});
 document.addEventListener('visibilitychange',()=>{if(document.hidden)settle();});matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change',e=>{if(e.matches)settle();});window.addEventListener('resize',()=>{finishTheme();finishFolds();window.motionIndicators();});setScheme(scheme);
})();

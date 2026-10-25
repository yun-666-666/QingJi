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
 const sun='<circle cx="16" cy="16" r="6"/><path d="M16 2v4m0 20v4M2 16h4m20 0h4M6 6l3 3m14 14 3 3M6 26l3-3M23 9l3-3"/>',moon='<path d="M25 19A11 11 0 0 1 13 5a11 11 0 1 0 12 14Z"/><path d="m24 4 1 3 3 1-3 1-1 3-1-3-3-1 3-1Z"/>';
 function finishTheme(){if(!themeRun)return;const run=themeRun;themeRun=null;run.change();run.animations.forEach(a=>a.cancel());run.decor?.remove();run.transition?.skipTransition();delete root.dataset.themeRunning;}
 function decorate(run){
  if(scheme==='a')return;const el=document.createElement('div');el.className='theme-scene '+(run.to==='dark'?'night':'day')+' scene-'+scheme;el.setAttribute('aria-hidden','true');
  el.innerHTML='<div class="theme-orbit"></div><div class="theme-beam beam-left"></div><div class="theme-beam beam-right"></div><div class="theme-emblem"><svg viewBox="0 0 32 32">'+(run.to==='dark'?moon:sun)+'</svg><span>'+(run.to==='dark'?'月色入幕':'晨光初启')+'</span></div>'+Array.from({length:9},(_,i)=>'<i class="theme-spark" style="--x:'+(12+(i*31)%77)+'%;--y:'+(17+(i*19)%68)+'%;--turn:'+(i*37)+'deg"></i>').join('');
  document.body.append(el);run.decor=el;el.querySelectorAll('.theme-orbit,.theme-beam,.theme-emblem,.theme-spark').forEach((node,i)=>node.style.viewTransitionName='qj-fx-'+i);run.effects=()=>{
  const options={duration:cfg().theme,easing:'cubic-bezier(.3,0,.2,1)',fill:'both'},play=(node,frames)=>run.animations.push(root.animate(frames,{...options,pseudoElement:'::view-transition-new('+node.style.viewTransitionName+')'}));
  play(el.querySelector('.theme-emblem'),[{opacity:0,transform:scheme==='b'?'scale(.55) rotate(-35deg)':'translate(-80px,24px) rotate(-18deg)'},{opacity:1,transform:'scale(1)',offset:.5},{opacity:0,transform:scheme==='b'?'translateY(-20px) scale(1.1)':'translate(100px,-50px) rotate(18deg)'}]);
  play(el.querySelector('.theme-orbit'),[{transform:'scale(.3) rotate(-60deg)',opacity:0},{opacity:.7,offset:.4},{transform:'scale(1.6) rotate(50deg)',opacity:0}]);
  for(const [i,beam] of [...el.querySelectorAll('.theme-beam')].entries())play(beam,[{opacity:0,transform:scheme==='b'?'translateX(0)':'translateX(-110vw) skewX(-22deg)'},{opacity:.9,offset:.2},{opacity:0,transform:scheme==='b'?`translateX(${i?50:-50}vw)`:'translateX(110vw) skewX(-22deg)'}]);
  el.querySelectorAll('.theme-spark').forEach((spark,i)=>play(spark,[{opacity:0,transform:'scale(.2) translateY(16px)'},{opacity:i%2?.9:.55,transform:'scale(1)',offset:.3+i*.025},{opacity:0,transform:'scale(.4) translateY(-24px)'}]));
  };
 }
 window.motionTheme=(to,change)=>{
  if(themeRun){themeRun.toTarget=to;themeRun.change=change;themeRun.animations.forEach(a=>{a.updatePlaybackRate(to===themeRun.to?1:-1);a.play();});return;}
  if(reduced()||!document.startViewTransition){change();return;}
  active.forEach(a=>a.finish());for(const [el,n] of numbers){cancelAnimationFrame(n.frame);n.value=n.target;el.textContent=n.format(n.target);}
  const rect=document.getElementById('themeToggle').getBoundingClientRect(),x=rect.left+rect.width/2,y=rect.top+rect.height/2,r=Math.hypot(Math.max(x,innerWidth-x),Math.max(y,innerHeight-y));
  const paths=scheme==='a'?[`circle(0px at ${x}px ${y}px)`,`circle(${r}px at ${x}px ${y}px)`]:scheme==='b'?['inset(0 50% 0 50% round 22px)','inset(0 0 0 0 round 0px)']:['polygon(-35% 0,-35% 0,-75% 100%,-75% 100%)','polygon(-35% 0,175% 0,135% 100%,-75% 100%)'];
  const run={to,toTarget:to,change,trigger:rect,animations:[],decor:null,transition:null};themeRun=run;root.dataset.themeRunning='true';const transition=document.startViewTransition(()=>{change();decorate(run);});run.transition=transition;
  transition.ready.then(()=>{if(themeRun!==run)return;const reveal=root.animate([{clipPath:paths[0]},{clipPath:paths[1]}],{duration:cfg().theme,easing:'cubic-bezier(.3,0,.2,1)',fill:'both',pseudoElement:'::view-transition-new(root)'});run.animations.push(reveal);run.effects?.();if(run.toTarget!==run.to)run.animations.forEach(a=>{a.currentTime=0;a.reverse();});reveal.finished.then(()=>{if(themeRun===run)finishTheme();}).catch(()=>{});}).catch(()=>{if(themeRun===run)finishTheme();});transition.finished.finally(()=>{if(themeRun===run)finishTheme();});
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

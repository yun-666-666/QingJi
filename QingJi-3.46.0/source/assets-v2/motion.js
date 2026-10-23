'use strict';
(()=>{
 const reduced=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
 const folds=new WeakMap(),layers=new WeakMap();
 function animate(el,frames,options){if(reduced()||!el.animate)return null;return el.animate(frames,{duration:220,easing:'cubic-bezier(.2,.75,.25,1)',...options});}
 window.motionToggle=(d,open)=>{
  const old=folds.get(d),target=open===undefined?!(old?old.target:d.open):open;if(!old&&d.open===target)return;
  const body=d.querySelector(':scope > .group-rows,:scope > .details-body,:scope > .fold-body');
  if(!body){d.open=target;return;}const start=d.getBoundingClientRect().height;old?.animation?.cancel();old?.observer?.disconnect();
  const state={target,animation:null,observer:null};folds.set(d,state);d.dataset.expanded=String(target);d.firstElementChild.setAttribute('aria-expanded',String(target));
  if(!d.open)d.open=true;body.inert=!target;
  function finish(){if(folds.get(d)!==state)return;clearTimeout(state.timer);state.observer?.disconnect();state.animation?.cancel();d.open=target;d.style.height='';d.style.overflow='';body.inert=false;delete d.dataset.expanded;folds.delete(d);}
  if(reduced()||!d.animate||!d.getClientRects().length){finish();return;}
  d.style.height=start+'px';d.style.overflow='hidden';
  function run(from){state.animation?.cancel();d.style.height='auto';const end=target?d.getBoundingClientRect().height:d.firstElementChild.getBoundingClientRect().height+parseFloat(getComputedStyle(d).paddingTop||0)+parseFloat(getComputedStyle(d).paddingBottom||0)+parseFloat(getComputedStyle(d).borderTopWidth||0)+parseFloat(getComputedStyle(d).borderBottomWidth||0);d.style.height=from+'px';state.animation=animate(d,[{height:from+'px'},{height:end+'px'}],{duration:target?240:190});if(state.animation){state.animation.onfinish=finish;clearTimeout(state.timer);state.timer=setTimeout(finish,target?300:250);}else finish();}
  run(start);if(target&&typeof ResizeObserver!=='undefined'){let h=body.getBoundingClientRect().height;state.observer=new ResizeObserver(()=>{const next=body.getBoundingClientRect().height;if(Math.abs(next-h)>.5&&folds.get(d)===state){h=next;run(d.getBoundingClientRect().height);}});state.observer.observe(body);}
 };
 window.setLayerVisible=(el,visible,done)=>{
  const old=layers.get(el);old?.forEach(a=>a.cancel());layers.delete(el);el.classList.remove('motion-closing');
  if(visible){el.hidden=false;window.syncLayers?.();}else if(el.hidden){done?.();return;}else el.classList.add('motion-closing');
  const sheet=el.firstElementChild,shift=el.id==='modal'?'translateY(28px)':'translateY(10px) scale(.98)';
  const a=animate(el,visible?[{opacity:0},{opacity:1}]:[{opacity:1},{opacity:0}],{duration:visible?200:160});
  const b=sheet&&animate(sheet,visible?[{transform:shift,opacity:.65},{transform:'none',opacity:1}]:[{transform:'none',opacity:1},{transform:shift,opacity:0}],{duration:visible?260:160});
  const list=[a,b].filter(Boolean);layers.set(el,list);
  let timer;const finish=()=>{if(layers.get(el)!==list)return;clearTimeout(timer);layers.delete(el);list.forEach(a=>a.cancel());el.classList.remove('motion-closing');if(!visible)el.hidden=true;window.syncLayers?.();done?.();};if(list.length){Promise.all(list.map(a=>a.finished.catch(()=>{}))).then(finish);timer=setTimeout(finish,visible?320:220);}else finish();
 };
 window.motionPage=el=>{if(el)animate(el,[{opacity:.4,transform:'translateY(7px)'},{opacity:1,transform:'none'}],{duration:200});};
 let themeEpoch=0;window.motionTheme=change=>{const epoch=++themeEpoch;let applied=false;const apply=()=>{if(epoch!==themeEpoch||applied)return;applied=true;change();};if(reduced()){apply();return;}if(document.startViewTransition){window._themeTransition?.skipTransition();const t=document.startViewTransition(apply);window._themeTransition=t;const timer=setTimeout(()=>{if(!applied){t.skipTransition();apply();}},180);t.finished.catch(()=>{}).finally(()=>{clearTimeout(timer);apply();if(window._themeTransition===t)window._themeTransition=null;});}else{document.documentElement.classList.add('theme-changing');apply();clearTimeout(window._themeTimer);window._themeTimer=setTimeout(()=>document.documentElement.classList.remove('theme-changing'),280);}};
 document.addEventListener('DOMContentLoaded',()=>{
  document.querySelectorAll('#settings details').forEach(d=>{d.open=false;const s=d.firstElementChild;s.setAttribute('aria-expanded','false');if(!d.querySelector(':scope > .details-body')){const body=document.createElement('div');body.className='fold-body';while(s.nextSibling)body.append(s.nextSibling);d.append(body);}d.addEventListener('toggle',()=>{if(!folds.has(d))s.setAttribute('aria-expanded',String(d.open));});});
 });
 document.addEventListener('click',e=>{const s=e.target.closest('summary');if(!s||e.defaultPrevented)return;const d=s.parentElement;if(!d.matches('#settings details,.merchant-group'))return;e.preventDefault();window.motionToggle(d);});
})();

'use strict';
(()=>{
 const el=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;};
 const layer=el('div');layer.id='appDialog';layer.className='overlay confirm-overlay';layer.hidden=true;layer.setAttribute('role','dialog');layer.setAttribute('aria-modal','true');layer.setAttribute('aria-labelledby','appDialogTitle');
 const sheet=el('div');sheet.className='confirm-sheet';const title=el('h2');title.id='appDialogTitle';const body=el('div');body.className='app-dialog-body';const actions=el('div');actions.className='actions';sheet.append(title,body,actions);layer.append(sheet);document.body.append(layer);
 let active=null,closing=false;const queue=[];
 function button(parent,label,fn){const b=el('button',label);b.type='button';b.onclick=fn;parent.append(b);return b;}
 function next(){if(active||closing||!queue.length)return;const q=queue.shift();active=q;q.focus=document.activeElement;document.activeElement?.blur();title.textContent=q.title||'提示';body.replaceChildren();actions.replaceChildren();let input=null;
  if(q.message){const p=el('p',q.message);p.className='app-dialog-message';body.append(p);}
  if(q.options){const box=el('div');box.className='app-options';box.setAttribute('role','group');box.setAttribute('aria-label',q.title);for(const [value,label] of q.options){const b=button(box,label,()=>close(value));b.classList.toggle('selected',value===q.value);b.setAttribute('aria-pressed',String(value===q.value));}body.append(box);}
  if(q.displayValue){const code=el('textarea');code.value=q.displayValue;code.readOnly=true;code.rows=4;code.setAttribute('aria-label','备份恢复码');code.className='app-secret';body.append(code);}
  if(q.input){input=el('input');input.type='password';input.autocomplete='off';input.spellcheck=false;input.placeholder=q.placeholder||'请输入';input.setAttribute('aria-label',q.title);body.append(input);}
  button(actions,q.negative||'取消',()=>close(null));if(!q.options){const yes=button(actions,q.positive||'确定',()=>close(input?input.value:true));yes.className=q.danger?'primary danger-confirm':'primary';}
  window.setLayerVisible(layer,true);(actions.firstElementChild).focus({preventScroll:true});
 }
 function close(value){if(!active||closing)return;closing=true;const q=active;window.setLayerVisible(layer,false,()=>{active=null;closing=false;body.replaceChildren();q.focus?.isConnected&&q.focus.focus({preventScroll:true});q.resolve(value);next();});}
 window.appDialog=options=>new Promise(resolve=>{queue.push({...options,resolve});next();});
 window.currentAppDialog=()=>layer.hidden?null:layer;
 window.closeAppDialog=()=>{if(layer.hidden)return false;close(null);return true;};
 layer.onclick=e=>{if(e.target===layer)close(null);};
 window.nativeAppDialog=async q=>{const value=await window.appDialog(q);if(typeof Ledger!=='undefined')Ledger.dialogResult(q.id,value!==null,typeof value==='string'?value:'');};
 window.appSelectField=(parent,label,items,value)=>{const wrap=el('div');wrap.className='finance-field';const caption=el('span',label),input=el('input');input.type='hidden';input.value=value??items[0][0];const b=button(wrap,'',async()=>{const choice=await window.appDialog({title:label,options:items,value:input.value});if(choice===null||!b.isConnected)return;input.value=choice;paint();input.dispatchEvent(new Event('change',{bubbles:true}));});b.className='app-select';b.setAttribute('aria-haspopup','dialog');b.setAttribute('aria-label',label);function paint(){b.textContent=(items.find(x=>String(x[0])===input.value)?.[1]||'请选择')+' ▾';}wrap.prepend(caption,input);parent.append(wrap);paint();return input;};
 window.appDateField=(parent,label,value)=>{const wrap=el('div');wrap.className='finance-field';const input=el('input');input.type='hidden';input.value=value||'';wrap.append(el('span',label),input);const row=el('div');row.className='app-date-field';const b=button(row,'',()=>window.openBookDate(input.value?new Date(input.value+'T12:00:00'):new Date(),'day',d=>{input.value=pickerISO(d).slice(0,10);paint();input.dispatchEvent(new Event('change',{bubbles:true}));}));b.className='app-select';b.setAttribute('aria-haspopup','dialog');b.setAttribute('aria-label',label);button(row,'清除',()=>{input.value='';paint();input.dispatchEvent(new Event('change',{bubbles:true}));});function paint(){b.textContent=input.value||'选择日期';}wrap.append(row);parent.append(wrap);paint();return input;};
})();

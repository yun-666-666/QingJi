'use strict';
(async () => {
 const $ = id => document.getElementById(id), wait = ms => new Promise(resolve => setTimeout(resolve, ms));
 const checks = [], marker = 'QJ-真机测试-' + Date.now(), errors = [];
 const originalTheme = Ledger.getTheme(), originalMotion = Ledger.getMotion();
 const onError = e => errors.push(e.message || 'Unhandled JavaScript error');
 window.addEventListener('error', onError);
 const check = (value, name) => { if (!value) throw new Error(name); checks.push(name); };
 const until = async (fn, name) => {
  for (let i = 0; i < 80; i++) { if (await fn()) return; await wait(100); }
  throw new Error(name);
 };
 const click = id => $(id).click();
 const tab = name => document.querySelector('[data-tab="' + name + '"]').click();
 const search = value => { $('search').value = value; $('search').dispatchEvent(new Event('input', {bubbles:true})); };
 let readEpoch=0;
 const fixturePage = name => toolsRequest('page', {status:'confirmed',all:true,kind:'all',q:name,page:0,groups:false,epoch:++readEpoch});
 const rows = async () => (await fixturePage(marker)).rows;
 const cleanup = async name => {
  if(!/^QJ-真机测试-\d+$/.test(name))throw new Error('Invalid fixture marker');
  const fixture = (await fixturePage(name)).rows;
  if(fixture.some(r=>r.merchant!==name))throw new Error('Fixture identity mismatch');
  for(const row of fixture){
   const history=await toolsRequest('history',{entry:row._id});
   for(const operation of history.filter(h=>!h.undone)){
    if(operation.count!==1||operation.after?.merchant!==name||(operation.before&&operation.before.merchant!==name))throw new Error('Non-fixture history refused');
    await toolsRequest('undo',{operation:operation.id});
   }
  }
  const remaining=await fixturePage(name);
  check(remaining.total===0&&remaining.order.length===0,'Test entries removed by undo');
 };
 let failure = '', fixtureStarted = false;
 try {
  await until(() => $('count').textContent.includes('笔'), 'Initial summary');
  if(window.polishCleanupMarker)await cleanup(window.polishCleanupMarker);
  if(window.polishPhase!=='remaining'){
  check(document.documentElement.scrollWidth <= innerWidth + 1, 'No horizontal overflow');
  for (const scheme of ['a','b','c']) {
   tab('settings');
   $('displayPanel').open = $('motionPanel').open = true;
   document.querySelector('[data-motion-choice="' + scheme + '"]').click();
   await wait(380);
   check(document.documentElement.dataset.motion === scheme, 'Motion scheme ' + scheme);
   const fold = $('capturePanel');
   motionToggle(fold, true); await wait(60); motionToggle(fold, false); await wait(40); motionToggle(fold, true);
   await wait(420);
   check(fold.open && !fold.style.height && !fold.style.overflow && fold.firstElementChild.getAttribute('aria-expanded') === 'true', 'Interruptible fold ' + scheme);
   motionToggle(fold, false); await wait(420);
   check(!fold.open && fold.firstElementChild.getAttribute('aria-expanded') === 'false', 'Fold close ' + scheme);
   click('themeToggle'); await wait(1150);
   check(!document.documentElement.dataset.themeRunning && !document.querySelector('.theme-scene'), 'Theme cleanup ' + scheme);
   tab('book'); click('add'); await wait(380);
   check(!$('modal').hidden && isModalLocked(), 'Sheet open ' + scheme);
   click('editTimeButton'); await wait(380);
   check(!$('dateModal').hidden && !$('modal').hidden, 'Nested date sheet ' + scheme);
   const month = $('dateMonth').textContent;
   click('datePrev'); await wait(190); click('dateNext'); await wait(190);
   check($('dateMonth').textContent === month, 'Calendar navigation ' + scheme);
   click('dateCancel'); await wait(220); click('close'); await wait(250);
   check($('modal').hidden && $('dateModal').hidden && !isModalLocked(), 'Sheet close unlocks ' + scheme);
  }
  tab('settings'); $('capturePanel').open = $('privacyPanel').open = true;
  Ledger.refresh(); await until(() => $('sources').children.length > 0, 'Sources loaded'); await wait(400);
  const source = $('sources').firstElementChild;
  Ledger.refresh(); await wait(450);
  check($('sources').firstElementChild === source, 'Unchanged settings retain source nodes');
  window.scrollTo(0, 150); await wait(80); const savedY = scrollY;
  tab('book'); await wait(350); tab('settings'); await wait(400);
  check(savedY > 0 && Math.abs(scrollY - savedY) < 2 && $('capturePanel').open, 'Tab restores scroll and expanded panels');
  tab('book'); await wait(400);
  motionIndicators(); motionIndicators(); await wait(80);
  check([...document.querySelectorAll('.motion-indicator')].every(n => n.getAnimations().length === 0), 'Unchanged indicators do not animate');
  click('overviewToggle'); await until(() => !$('categoryAreas').hidden && $('categoryAreas').children.length === 7, 'Overview loaded');
  const category = $('categoryAreas').firstElementChild;
  Ledger.refresh(); await wait(450);
  check($('categoryAreas').firstElementChild === category, 'Unchanged overview retains category nodes');
  click('overviewToggle'); await wait(400);
  }
  for (const amount of ['1.23','2.34']) {
   click('add'); await wait(380);
   $('editMerchant').value = marker;
   $('editAmount').value = '0'; click('save'); await wait(80);
   check(!!$('editError').textContent && !$('modal').hidden, 'Invalid amount stays editable ' + amount);
   $('editAmount').value = amount; fixtureStarted = true;
   click('save'); click('save');
   await until(() => $('modal').hidden, 'Fixture saved');
   await wait(350);
  }
  let fixture = await rows();
  check(fixture.length === 2 && fixture.every(r => r.merchant === marker) && fixture.reduce((n,r) => n+r.cents,0) === 357, 'Two real saves with no duplicate submission');
  search(marker); await until(() => $('entries').textContent.includes(marker)&&$('entries').children.length===1, 'Search fixture');
  const group = $('entries').querySelector('.merchant-group');
  check(!!group && $('entries').children.length === 1, 'Merchant group search');
  group.querySelector('summary').click();
  await until(() => group.querySelectorAll('.transaction').length === 2, 'Group details loaded');
  check(group.open, 'Native grouped list expands');
  const beforeComposition = $('entries').firstElementChild;
  $('search').dispatchEvent(new CompositionEvent('compositionstart', {bubbles:true}));
  search('no-such-' + marker); await wait(250);
  check($('entries').firstElementChild === beforeComposition, 'IME composition does not query partial text');
  $('search').dispatchEvent(new CompositionEvent('compositionend', {bubbles:true}));
  await until(() => $('entries').textContent.includes('没有匹配'), 'Composition commits search');
  click('clearSearch'); await wait(350);
  check($('search').value === '' && $('clearSearch').hidden, 'Clear search resets results');
  $('search').blur(); search(marker); await until(() => $('entries').textContent.includes(marker), 'Restore fixture filter');
  const beforeDate = $('monthLabel').textContent;
  for (let i=0;i<4;i++) { click('nextMonth'); click('prevMonth'); }
  await wait(700);
  check($('monthLabel').textContent === beforeDate && $('entries').textContent.includes(marker), 'Rapid period navigation keeps latest results');
  check(!errors.length, 'No JavaScript runtime errors');
 } catch (e) {
  failure = e.message;
 } finally {
  try {
   if (fixtureStarted) {
    await until(() => !$('save').disabled, 'Pending fixture save completes before cleanup');
    await cleanup(marker);
   }
   if (!$('dateModal').hidden) click('dateCancel');
   if (!$('modal').hidden) click('close');
   await wait(250);
   tab('settings'); $('displayPanel').open = $('motionPanel').open = true;
   document.querySelector('[data-motion-choice="' + originalMotion + '"]')?.click();
   if (document.documentElement.dataset.theme !== originalTheme) { click('themeToggle'); await wait(1150); }
   document.querySelectorAll('#settings details').forEach(d => { d.open=false; });
   tab('book'); search(''); $('clearSearch').hidden=true; Ledger.refresh(); await wait(500); scrollTo(0,0);
  } catch (e) { failure += ' Cleanup: ' + e.message; }
  window.removeEventListener('error', onError);
  window.polishResult = (failure ? 'FAIL: '+failure : 'PASS: '+checks.length+' real-device UI checks; fixtures removed') + '\n' + checks.join('\n');
 }
})();

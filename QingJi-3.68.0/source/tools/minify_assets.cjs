// Build-only dependency: npm install terser@5.44.0. No dependency enters the APK.
const fs=require('fs'),path=require('path'),{minify}=require('terser');
(async()=>{const [src,dst]=process.argv.slice(2);fs.mkdirSync(dst,{recursive:true});let before=0,after=0;
for(const name of fs.readdirSync(src)){const p=path.join(src,name);if(!fs.statSync(p).isFile())continue;const raw=fs.readFileSync(p);let out=raw;
if(name.endsWith('.js')){const r=await minify(raw.toString(),{compress:{defaults:false,dead_code:true,conditionals:true,booleans:true,evaluate:true,sequences:true,join_vars:true,drop_debugger:false},mangle:{toplevel:false,keep_fnames:true,keep_classnames:true},format:{comments:false,semicolons:true}});out=Buffer.from(r.code+'\n')}
fs.writeFileSync(path.join(dst,name),out);before+=raw.length;after+=out.length}
console.log('JS conservative syntax compression; no unsafe/inlining/unused removal, global/property/function names retained:',before,'->',after);
// Only adjacent plain links are bundled. Script barriers and every CSS byte/order are retained.
const index=path.join(dst,'index.html');let html=fs.readFileSync(index,'utf8'),bundle=0;
html=html.replace(/(?:<link rel="stylesheet" href="[\w.-]+\.css">){2,}/g,group=>{const names=[...group.matchAll(/href="([\w.-]+\.css)"/g)].map(m=>m[1]);const chunks=names.map(n=>fs.readFileSync(path.join(dst,n),'utf8'));if(chunks.some(t=>/@(?:import|charset|namespace)\b|url\(/i.test(t)))throw Error('CSS bundle requires explicit path/directive review');const name='bundle'+(++bundle)+'.css';fs.writeFileSync(path.join(dst,name),chunks.join('\n'));for(const n of names)fs.unlinkSync(path.join(dst,n));console.log('Lossless ordered CSS bundle:',names.length,'-> 1');return '<link rel="stylesheet" href="'+name+'">';});fs.writeFileSync(index,html);
})().catch(e=>{console.error(e);process.exitCode=1});

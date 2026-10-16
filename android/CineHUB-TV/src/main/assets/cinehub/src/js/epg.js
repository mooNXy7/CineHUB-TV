/*
 * CineHUB TV EPG — multi-source programme guide.
 *
 * IMPORTANT:
 * - EPG is enrichment only. It NEVER removes, replaces or filters channels.
 * - Keeps the existing window.CineHUBEPG API used by cinehub.js.
 * - Uses a byte-scanner instead of DOMParser over the
 *   entire XML document, which avoids large synchronous DOM work on Android.
 * - Tries public XMLTV feeds and channel-specific schedule pages,
 *   then Pluto TV. A failed source never aborts the other sources.
 */
(function(){
'use strict';

const CACHE_KEY='cinehub_epg_cache_v3';
const SOURCE_KEY='cinehub_epg_source_v2';
const TTL=6*60*60*1000;
const PAST=6*60*60*1000;
const FUTURE=3*24*60*60*1000;
const REQUEST_TIMEOUT=60000;

/* Public XMLTV feeds provide broad fallback coverage; Pluto is matched by ID. */
const SOURCES={
  default:[
    ['Pluto TV Brasil','https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/PlutoTV/br.xml'],
    ['IPTV EPG Brasil','https://iptv-epg.org/files/epg-br.xml'],
    ['Open EPG Brasil','https://www.open-epg.com/files/brazil3.xml']
  ],
  alternate:[
    ['IPTV EPG Brasil','https://iptv-epg.org/files/epg-br.xml'],
    ['Open EPG Brasil','https://www.open-epg.com/files/brazil3.xml'],
    ['Pluto TV Brasil','https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/PlutoTV/br.xml']
  ]
};
const ACERVO_MAP_URL='https://raw.githubusercontent.com/mooNXy7/CineHUB-ACERVO/main/catalog/epg/channel-map.json';
const MEUGUIA_BASE='https://meuguia.tv/programacao/canal/';
const GUIADETV_BASE='https://www.guiadetv.com/canal/';

const state={
  programmes:new Map(),
  loading:false,
  source:'default',
  updatedAt:0,
  error:null,
  matchedChannels:0,
  revision:0,
  sourceResults:[]
};

const aliases={
  'adult swim':'trutv',
  'history':'history channel',
  'sony channel':'sony',
  'sportv 2':'sportv2',
  'sportv 3':'sportv3',
  'gnt':'gnt hd',
  'band':'band sp',
  'warner':'warner channel',
  'sbt':'sbt sp',
  'globo rj':'globo rj',
  'globonews':'globonews',
  'discovery id':'investigacao discovery',
  'amc':'amc brasil',
  'cnn brasil money':'cnn brasil money hd br',
  'universal premiere':'universal premiere hd br',
  'universal reality':'universal reality br',
  'tnt novelas':'tnt novelas br',
  'trace brazuca':'trace brasil hd br',
  'record sp':'recordtv sp'
};

const noise=new Set(['hd','sd','fhd','uhd','4k','br']);
const cleanText=v=>String(v||'').trim();

function normalise(s){
  let text=decodeEntities(cleanText(s));
  text=text.replace(/^[A-Z]{2}\s*[-|]\s*/,'');
  text=text.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  text=text.replace(/[^a-z0-9]+/g,' ').trim();
  let tokens=text.split(/\s+/).filter(Boolean);
  while(tokens.length>1&&noise.has(tokens[tokens.length-1]))tokens.pop();
  return tokens.join(' ');
}

function decodeEntities(s){
  return String(s||'')
    .replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>')
    .replace(/&quot;/g,'"').replace(/&apos;/g,"'").replace(/&#39;/g,"'")
    .replace(/&#(\d+);/g,(_,n)=>{try{return String.fromCodePoint(Number(n))}catch{return _}})
    .replace(/&#x([0-9a-f]+);/gi,(_,n)=>{try{return String.fromCodePoint(parseInt(n,16))}catch{return _}});
}

function channelKey(ch){
  return normalise(ch?.nome||ch?.name||'');
}
function channelAliases(ch){
  const vals=[ch?.tvgId,ch?.tvgID,ch?.nome,ch?.name,ch?.id].map(normalise).filter(Boolean);
  return [...new Set(vals)];
}

function readCache(){
  try{
    const x=JSON.parse(localStorage.getItem(CACHE_KEY)||'null');
    if(!x||!x.t||!Array.isArray(x.programmes))return null;
    if(Date.now()-x.t>TTL)return null;
    return x;
  }catch{return null}
}
function writeCache(){
  try{
    const payload={
      v:3,t:Date.now(),source:state.source,
      programmes:[...state.programmes.entries()]
    };
    localStorage.setItem(CACHE_KEY,JSON.stringify(payload));
  }catch(e){
    /* localStorage can be too small on Android/WebView. EPG remains usable. */
    try{localStorage.removeItem(CACHE_KEY)}catch{}
    console.warn('[CineHUB] EPG cache indisponível:',e);
  }
}

function setSource(source){
  const migrated=source==='saimo'?'default':source==='iptvcom'?'alternate':source;
  state.source=SOURCES[migrated]?migrated:'default';
  try{localStorage.setItem(SOURCE_KEY,state.source)}catch{}
}
function getSource(){
  try{
    const saved=localStorage.getItem(SOURCE_KEY);
    if(saved==='saimo')return 'default';
    if(saved==='iptvcom')return 'alternate';
    return SOURCES[saved]?saved:'default';
  }catch{return 'default'}
}
state.source=getSource();

function notify(){
  state.revision++;
  window.dispatchEvent(new CustomEvent('cinehub:epg-updated',{detail:{
    count:state.programmes.size,
    source:state.source,
    updatedAt:state.updatedAt,
    error:state.error,
    matchedChannels:state.matchedChannels,
    sourceResults:state.sourceResults
  }}));
}

function parseXMLTVDate(v){
  const m=String(v||'').trim().match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?/);
  if(!m)return 0;
  const y=+m[1],mo=+m[2],d=+m[3],h=+m[4],mi=+m[5],s=+m[6];
  const utc=Date.UTC(y,mo-1,d,h,mi,s);
  if(!Number.isFinite(utc))return 0;
  if(!m[7])return utc;
  const sign=m[7][0]==='-'?-1:1,off=(+m[7].slice(1,3)*60*60+(+m[7].slice(3,5))*60)*sign;
  return utc-off*1000;
}

function bytesOf(text){
  return new TextEncoder().encode(String(text||''));
}
function find(bytes,needle,from=0,before=bytes.length){
  const p=typeof needle==='string'?bytesOf(needle):needle;
  if(!p.length)return -1;
  const end=Math.min(before,bytes.length)-p.length;
  for(let i=Math.max(0,from);i<=end;i++){
    if(bytes[i]!==p[0])continue;
    let ok=true;
    for(let k=1;k<p.length;k++){if(bytes[i+k]!==p[k]){ok=false;break}}
    if(ok)return i;
  }
  return -1;
}
function sliceText(bytes,start,end){
  if(start<0||end<=start||end>bytes.length)return '';
  return decodeEntities(new TextDecoder('utf-8').decode(bytes.subarray(start,end)));
}
function tagText(bytes,tag,from,before){
  const open=find(bytes,'<'+tag,from,before);
  if(open<0)return '';
  const body=find(bytes,'>',open,before);
  if(body<0)return '';
  const end=find(bytes,'</'+tag+'>',body+1,before);
  if(end<0)return '';
  return sliceText(bytes,body+1,end).trim();
}
function attr(bytes,open,end,name){
  const needle=name+'="';
  const p=find(bytes,needle,open,end);
  if(p<0)return '';
  const q=find(bytes,'"',p+needle.length,end);
  return q<0?'':sliceText(bytes,p+needle.length,q);
}

/* XMLTV parser: scan bytes, materialise text only for relevant blocks. */
function parseXML(text,channels,{plutoIds=null}={}){
  const bytes=bytesOf(text);
  const xmlToChannel=new Map();
  const nameToXML=new Map();
  let cursor=0;
  let matched=0;

  if(plutoIds&&plutoIds.size){
    for(const [xmlId,ch] of plutoIds)xmlToChannel.set(xmlId,ch.nome);
  }

  if(!plutoIds||!plutoIds.size){
    while(true){
      const open=find(bytes,'<channel id="',cursor);if(open<0)break;
      const idStart=open+'<channel id="'.length;
      const idEnd=find(bytes,'"',idStart);if(idEnd<0)break;
      const close=find(bytes,'</channel>',idEnd);if(close<0)break;
      const id=sliceText(bytes,idStart,idEnd);
      const dnOpen=find(bytes,'<display-name',idEnd,close);
      if(dnOpen>=0){
        const dnBody=find(bytes,'>',dnOpen,close),dnEnd=dnBody>=0?find(bytes,'</display-name>',dnBody+1,close):-1;
        if(dnBody>=0&&dnEnd>=0){
          const key=normalise(sliceText(bytes,dnBody+1,dnEnd));
          if(key){if(!nameToXML.has(key))nameToXML.set(key,[]);nameToXML.get(key).push(id)}
        }
      }
      cursor=close+10;
    }

    const claimed=new Set(),unresolved=[];
    for(const ch of channels){
      const key=channelKey(ch);
      if(!key)continue;
      let ids=nameToXML.get(key);
      if((!ids||!ids.length)&&aliases[key])ids=nameToXML.get(normalise(aliases[key]));
      /* tvg-id is a stronger signal than name when supplied. */
      const tvg=normalise(ch.tvgId||ch.tvgID||'');
      if(tvg&&nameToXML.has(tvg))ids=nameToXML.get(tvg);
      if(ids?.length){
        ids.forEach(id=>xmlToChannel.set(id,ch.nome));
        ids.forEach(id=>claimed.add(id));
        matched++;
      }else unresolved.push({ch,key});
    }

    for(const {ch,key} of unresolved){
      const candidates=[];
      for(const [xmlKey,ids] of nameToXML){
        if((xmlKey+' ').startsWith(key+' ')||(key+' ').startsWith(xmlKey+' '))candidates.push([xmlKey,ids]);
      }
      if(candidates.length!==1)continue;
      const free=candidates[0][1].filter(id=>!claimed.has(id));
      if(!free.length)continue;
      free.forEach(id=>{xmlToChannel.set(id,ch.nome);claimed.add(id)});
      matched++;
    }
  }

  if(!xmlToChannel.size)return {map:new Map(),matched:0};

  const from=Date.now()-PAST,to=Date.now()+FUTURE;
  const out=new Map();cursor=0;
  while(true){
    const open=find(bytes,'<programme ',cursor);if(open<0)break;
    const attrsEnd=find(bytes,'>',open+11);if(attrsEnd<0)break;
    const close=find(bytes,'</programme>',attrsEnd);if(close<0)break;
    cursor=close+12;

    const xmlId=attr(bytes,open,attrsEnd,'channel');
    const channel=xmlToChannel.get(xmlId);if(!channel)continue;
    const start=parseXMLTVDate(attr(bytes,open,attrsEnd,'start'));
    const stop=parseXMLTVDate(attr(bytes,open,attrsEnd,'stop'));
    if(!start||!stop||stop<=from||start>=to)continue;
    const title=tagText(bytes,'title',attrsEnd,close);
    if(!title)continue;
    const desc=tagText(bytes,'desc',attrsEnd,close);
    const category=tagText(bytes,'category',attrsEnd,close);
    let episode=tagText(bytes,'episode-num',attrsEnd,close);
    const epOpen=find(bytes,'<episode-num',attrsEnd,close);
    if(epOpen>=0){
      const epBody=find(bytes,'>',epOpen,close);
      if(epBody>=0&&attr(bytes,epOpen,close,'system')==='original-air-date')episode='';
    }
    const year=tagText(bytes,'date',attrsEnd,close);
    const posterOpen=find(bytes,'<icon',attrsEnd,close);
    let poster='';
    if(posterOpen>=0)poster=attr(bytes,posterOpen,close,'src');
    const actors=[];let tc=attrsEnd;
    while(true){
      const ao=find(bytes,'<actor',tc,close);if(ao<0)break;
      const ab=find(bytes,'>',ao,close),ae=ab>=0?find(bytes,'</actor>',ab+1,close):-1;
      if(ab>=0&&ae>=0){const a=sliceText(bytes,ab+1,ae).trim();if(a)actors.push(a);tc=ae+8}else break;
    }
    const directors=[];tc=attrsEnd;
    while(true){
      const ao=find(bytes,'<director',tc,close);if(ao<0)break;
      const ab=find(bytes,'>',ao,close),ae=ab>=0?find(bytes,'</director>',ab+1,close):-1;
      if(ab>=0&&ae>=0){const a=sliceText(bytes,ab+1,ae).trim();if(a)directors.push(a);tc=ae+11}else break;
    }
    const credits=[actors.length?actors.join(', '):'',directors.length?'Direção: '+directors.join(', '):''].filter(Boolean).join(' · ');
    const p={title,desc,category,episode:episode||'',year:year||'',credits,poster:poster||'',start,stop};
    if(!out.has(channel))out.set(channel,[]);out.get(channel).push(p);
  }
  for(const list of out.values())list.sort((a,b)=>a.start-b.start);
  return {map:out,matched};
}


function textOnly(html){
  return decodeEntities(String(html||'').replace(/<[^>]*>/g,' ').replace(/\s+/g,' ')).trim();
}
function saoPauloDate(year,month,day,hour,minute){return Date.UTC(year,month-1,day,hour+3,minute,0)}
function parseMeuGuia(html){
  const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Sao_Paulo',year:'numeric',month:'numeric',day:'numeric'}).formatToParts(new Date());
  const current={year:+parts.find(x=>x.type==='year').value,month:+parts.find(x=>x.type==='month').value,day:+parts.find(x=>x.type==='day').value};
  let day={...current};const partial=[];
  for(const item of String(html||'').split(/<li\b/i).slice(1)){
    if(/subheader/i.test(item.slice(0,180))){
      const raw=(item.match(/subheader[\s\S]*?>\s*([^<]+)</i)||[])[1]||'';
      const m=raw.match(/,\s*(\d{1,2})\/(\d{1,2})/);
      if(m){const d=+m[1],mo=+m[2];day={year:mo<current.month?current.year+1:current.year,month:mo,day:d};}
      continue;
    }
    const clock=(item.match(/lileft\s+time["']?\s*>\s*(\d{1,2}:\d{2})</i)||[])[1];
    const title=(item.match(/<h2[^>]*>([\s\S]*?)<\/h2>/i)||[])[1];
    if(!clock||!title)continue;
    const hm=clock.split(':').map(Number),titleText=textOnly(title);
    if(!titleText||hm.length!==2||hm[0]>23||hm[1]>59)continue;
    const category=textOnly((item.match(/<h3[^>]*>([\s\S]*?)<\/h3>/i)||[])[1]||'');
    partial.push({start:saoPauloDate(day.year,day.month,day.day,hm[0],hm[1]),title:titleText,desc:'',category,episode:'',year:'',credits:'',poster:''});
  }
  partial.sort((a,b)=>a.start-b.start);
  return partial.map((p,i)=>({...p,stop:partial[i+1]?.start||p.start+3600000})).filter(p=>p.stop>Date.now()-PAST&&p.start<Date.now()+FUTURE);
}
function parseGuiaDeTv(html){
  const partial=[];const re=/data-dt="(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):\d{2}[^"]*"[\s\S]*?<a[^>]*href="[^"]*programa\/[^"]+"[^>]*>([\s\S]*?)<\/a>/gi;
  let m;
  while((m=re.exec(String(html||'')))!==null){
    const title=textOnly(m[6]);if(title.length<2)continue;
    partial.push({start:saoPauloDate(+m[1],+m[2],+m[3],+m[4],+m[5]),title,desc:'',category:'',episode:'',year:'',credits:'',poster:''});
  }
  partial.sort((a,b)=>a.start-b.start);
  return partial.map((p,i)=>({...p,stop:partial[i+1]?.start||p.start+3600000})).filter(p=>p.stop>Date.now()-PAST&&p.start<Date.now()+FUTURE);
}
async function loadAcervoMap(){
  try{return await fetch(ACERVO_MAP_URL,{cache:'no-store',signal:AbortSignal.timeout(8000)}).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.json()})}
  catch(e){console.warn('[CineHUB] Mapa EPG remoto indisponível; XMLTV segue ativo.',e);return null}
}
async function fetchMappedSchedules(channels,mapDoc,kind,already){
  if(!mapDoc)return new Map();
  const codes=kind==='meuguia'?(mapDoc.meuguia_codes||{}):(mapDoc.guiadetv_slugs||{});
  const entries=new Map();
  for(const ch of channels){
    const name=channelKey(ch),key=Object.keys(codes).find(k=>normalise(k)===name);
    if(!key)continue;
    const code=codes[key];if(!entries.has(code))entries.set(code,[]);entries.get(code).push(ch);
  }
  if(kind==='guiadetv'){
    for(const [code,items] of [...entries]){
      const remaining=items.filter(ch=>!(already.get(ch.nome)||[]).length);
      if(!remaining.length)entries.delete(code);else entries.set(code,remaining);
    }
  }
  if(!entries.size)return new Map();
  const base=kind==='meuguia'?MEUGUIA_BASE:GUIADETV_BASE;
  const preferredProbe=kind==='meuguia'?(codes.HBO||'HBO'):(codes['SONY Movies']||'sony-movies');
  const codesList=[...entries.keys()],probeCode=entries.has(preferredProbe)?preferredProbe:codesList[0];
  const results=new Map();let ok=0,failed=0,index=0;
  try{
    const html=await fetchText(base+encodeURIComponent(probeCode),8000);
    const parsed=kind==='meuguia'?parseMeuGuia(html):parseGuiaDeTv(html);
    if(!parsed.length)throw new Error('Página acessível, mas sem grade reconhecível.');
    ok++;
    for(const ch of entries.get(probeCode)||[])results.set(ch.nome,parsed);
  }catch(e){
    state.sourceResults.push({label:kind==='meuguia'?'Meu Guia Brasil':'Guia de TV Brasil',ok:false,error:e?.message||String(e),skipped:'probe/CORS'});
    return results;
  }
  const worker=async()=>{
    while(true){
      const i=index++;if(i>=codesList.length)return;
      const code=codesList[i];if(code===probeCode)continue;
      try{
        const html=await fetchText(base+encodeURIComponent(code),8000);
        const programmes=kind==='meuguia'?parseMeuGuia(html):parseGuiaDeTv(html);
        if(!programmes.length)throw new Error('Sem programas reconhecíveis');
        for(const ch of entries.get(code)||[])results.set(ch.nome,programmes);
        ok++;
      }catch(e){failed++;}
    }
  };
  await Promise.all(Array.from({length:Math.min(4,codesList.length)},worker));
  state.sourceResults.push({label:kind==='meuguia'?'Meu Guia Brasil':'Guia de TV Brasil',ok:ok>0,matched:results.size,programmes:[...results.values()].reduce((n,x)=>n+x.length,0),failedPages:failed});
  return results;
}

function programmeLabel(p){
  if(!p)return '';
  const parts=[];
  if(p.category)parts.push(p.category);
  if(p.episode){
    let ep=p.episode;
    if(ep.includes('.')){
      const f=ep.split('.').map(x=>parseInt(x.split('/')[0],10));
      if(Number.isFinite(f[0])&&f[0]<=39){ep='T'+(f[0]+1)+(Number.isFinite(f[1])&&f[1]<=199?' E'+(f[1]+1):'')}
    }
    if(/^[Ss]\d+/.test(ep))ep=ep.replace(/^S/,'T');
    if(/T\d+/.test(ep))parts.push(ep);
  }
  if(p.year)parts.push(p.year);
  return parts.filter(Boolean).join(' · ');
}

function mergeParsed(merged,parsed){
  parsed.forEach((list,name)=>{
    if(!list.length)return;
    const current=merged.get(name)||[];
    if(!current.length){merged.set(name,list);return}
    const seen=new Set(current.map(p=>p.start+'|'+normalise(p.title)));
    for(const p of list){const k=p.start+'|'+normalise(p.title);if(!seen.has(k)){current.push(p);seen.add(k)}}
    current.sort((a,b)=>a.start-b.start);merged.set(name,current);
  });
}

async function fetchText(url,timeout=REQUEST_TIMEOUT){
  const ctl=new AbortController(),timer=setTimeout(()=>ctl.abort(),timeout);
  try{
    const r=await fetch(url,{cache:'no-store',signal:ctl.signal,headers:{Accept:'application/xml,text/xml,text/plain,*/*'}});
    if(!r.ok)throw new Error('HTTP '+r.status);
    return await r.text();
  }finally{clearTimeout(timer)}
}

function getPlutoMap(channels){
  const out=new Map();
  for(const ch of channels){
    const values=[ch.stream,...(Array.isArray(ch.sources)?ch.sources.map(x=>typeof x==='string'?x:x?.url):[]) ,ch.logo].filter(Boolean).join(' ');
    const m=values.match(/(?:plu-|images\.pluto\.tv\/channels\/)([0-9a-f]{24})/i);
    if(m)out.set(m[1],ch);
  }
  return out;
}

function apply(merged){
  state.programmes=merged;
  state.updatedAt=Date.now();
  state.matchedChannels=merged.size;
  state.error=null;
  writeCache();
  notify();
}

async function load(channels,force=false){
  if(state.loading)return;
  const list=Array.isArray(channels)?channels:[];
  state.loading=true;state.error=null;state.sourceResults=[];
  try{
    if(!force){
      const cached=readCache();
      if(cached){
        state.programmes=new Map(cached.programmes||[]);
        state.updatedAt=cached.t||0;
        state.matchedChannels=state.programmes.size;
        notify();
        if(Date.now()-cached.t<TTL){state.loading=false;return}
      }
    }

    const merged=new Map();
    const plutoMap=getPlutoMap(list);
    const wanted=list.filter(ch=>!plutoMapHasChannel(plutoMap,ch));
    const sources=SOURCES[state.source]||SOURCES.default;

    /* Each source is isolated: one CORS/HTTP failure cannot hide data from the
     * next source; a single upstream failure never aborts the other sources. */
    for(const [label,url] of sources){
      try{
        const xml=await fetchText(url);
        const parsed=parseXML(xml,wanted);
        const pCount=[...parsed.map.values()].reduce((n,a)=>n+a.length,0);
        mergeParsed(merged,parsed.map);
        state.sourceResults.push({label,url,ok:true,matched:parsed.matched,programmes:pCount});
        if(label==='Pluto TV Brasil'&&plutoMap.size){
          const pp=parseXML(xml,list,{plutoIds:plutoMap});
          mergeParsed(merged,pp.map);
          state.sourceResults[state.sourceResults.length-1].matched=pp.matched||pp.map.size;
        }
      }catch(e){
        state.sourceResults.push({label,url,ok:false,error:e?.message||String(e)});
        console.warn('[CineHUB] EPG fonte indisponível',url,e);
      }
    }

    // Channel-specific schedules override generic XMLTV where available.
    const epgMap=await loadAcervoMap();
    const meuguia=await fetchMappedSchedules(wanted,epgMap,'meuguia',merged);
    meuguia.forEach((programmes,name)=>merged.set(name,programmes));
    const guiadetv=await fetchMappedSchedules(wanted,epgMap,'guiadetv',merged);
    guiadetv.forEach((programmes,name)=>{if(!(merged.get(name)||[]).length)merged.set(name,programmes)});

    if(merged.size){apply(merged)}
    else{
      state.error=new Error('Nenhuma fonte de EPG respondeu com canais compatíveis.');
      notify();
    }
  }catch(e){
    state.error=e;notify();
  }finally{state.loading=false}
}

function plutoMapHasChannel(map,ch){
  for(const value of map.values())if(value===ch)return true;
  return false;
}

function nowNext(name){
  const key=typeof name==='string'?name:name?.nome;
  const list=state.programmes.get(key)||[];const now=Date.now();
  let lo=0,hi=list.length-1,current=null,index=-1;
  while(lo<=hi){
    const mid=(lo+hi)>>1,p=list[mid];
    if(p.stop<=now)lo=mid+1;
    else if(p.start>now)hi=mid-1;
    else{current=p;index=mid;break}
  }
  const next=index>=0?list[index+1]||null:list.find(p=>p.start>now)||null;
  return {current,next,progress:current&&current.stop>current.start?Math.max(0,Math.min(1,(now-current.start)/(current.stop-current.start))):0};
}
function forChannel(ch){return nowNext(ch?.nome||'')}
function guide(ch,limit=8){return (state.programmes.get(ch?.nome||'')||[]).filter(p=>p.stop>Date.now()).slice(0,limit)}
function sourceLabel(){return state.source==='alternate'?'EPG: Alternativo':'EPG: CineHUB'}

window.CineHUBEPG={
  state,SOURCES,aliases,normalise,load,setSource,nowNext,forChannel,guide,sourceLabel,
  programmeLabel,refresh:()=>load(window.canaisM3U8||[],true),
  clearCache:()=>{try{localStorage.removeItem(CACHE_KEY)}catch{}state.programmes=new Map();state.updatedAt=0;notify()}
};

})();

/* CineHUB TV Live Engine — channels, external sources and EPG only. */
(function(){
  'use strict';
  const current=()=>Array.isArray(window.canaisM3U8)?window.canaisM3U8:[];
  function channels(query='',category='Todos'){
    let a=current();
    if(category&&category!=='Todos')a=a.filter(x=>x.grupo===category);
    const q=String(query||'').trim().toLocaleLowerCase('pt-BR');
    if(q)a=a.filter(x=>(String(x.nome||'')+' '+String(x.grupo||'')).toLocaleLowerCase('pt-BR').includes(q));
    return a;
  }
  function categories(){return [...new Set(current().map(x=>x.grupo).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt-BR'))}
  function popular(){
    const counts=window.CineHUBEngineState?.watchedChannels?.()||{};
    const base=current().slice().sort((a,b)=>(counts[b.nome]||0)-(counts[a.nome]||0));
    const known=['TV Globo','SBT','RECORD','Band','RedeTV!','TV Cultura'];
    const preferred=[];for(const n of known){const x=current().find(c=>c.nome?.toLowerCase()===n.toLowerCase());if(x)preferred.push(x)}
    return [...new Set([...base,...preferred])].slice(0,8);
  }
  async function refresh(includeLocal=true){return window.CineHUBExternalSources?.loadIptv?.({remote:true,local:includeLocal})||null}
  async function loadSaimo(){return window.CineHUBExternalSources?.loadSaimo?.()||null}
  async function epgLoad(force=false){return window.CineHUBEPG?.load?.(current(),force)||null}
  function epgFor(ch){return window.CineHUBEPG?.forChannel?.(ch)||{current:null,next:null,progress:0}}
  function guide(ch,limit=8){return window.CineHUBEPG?.guide?.(ch,limit)||[]}
  window.CineHUBLive={channels,categories,popular,refresh,loadSaimo,epgLoad,epgFor,guide,state:()=>window.CineHUBExternalSources?.state||{}};
})();
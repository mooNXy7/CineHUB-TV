/* CineHUB TV Player Engine — wraps the existing HLS/MegaEmbed paths. */
(function(){
  'use strict';
  const state={hls:null,token:0,current:null,type:null,sources:[],sourceIndex:0,timers:{load:0,embed:0}};
  const playbackCandidates=sources=>[...new Set((sources||[]).flatMap(x=>{const u=String(typeof x==='string'?x:x?.url||'').trim();if(!u)return [];return /^http:\/\//i.test(u)?[u,u.replace(/^http:\/\//i,'https://')]:[u]}))];
  function destroy(){if(state.hls){try{state.hls.destroy()}catch{}state.hls=null}}
  function loadHLS(){
    if(window.Hls)return Promise.resolve(window.Hls);if(window.__cinehubHlsPromise)return window.__cinehubHlsPromise;
    window.__cinehubHlsPromise=new Promise((resolve,reject)=>{const s=document.createElement('script');s.src='https://cdn.jsdelivr.net/npm/hls.js@1.6.2/dist/hls.min.js';s.async=true;s.onload=()=>window.Hls?resolve(window.Hls):reject(new Error('HLS indisponível'));s.onerror=()=>reject(new Error('HLS indisponível'));document.head.appendChild(s)});return window.__cinehubHlsPromise;
  }
  function progressKey(item,type){return {titulo:item?.titulo||item?.nome||'',imagem:item?.imagem||item?.logo||'',banner:item?.banner||item?.imagem||'',type:type||'movie',at:Date.now()}}
  function sameTitle(a,b){return String(a||'').trim().toLocaleLowerCase('pt-BR')===String(b||'').trim().toLocaleLowerCase('pt-BR')}
  function resume(target,item){if(state.type==='live')return;const p=window.CineHUBEngineState?.progress?.();if(!p||!sameTitle(p.titulo,item?.titulo||item?.nome)||!(Number(p.tempo)>3))return;try{const t=Math.min(Number(p.tempo),Number.isFinite(target.video.duration)&&target.video.duration>0?target.video.duration-1:Number(p.tempo));if(t>3)target.video.currentTime=t}catch{}}
  function playDirect(target,item,sources,index,onEvent){
    const video=target.video;if(!video)return Promise.reject(new Error('Video indisponível'));const url=sources[index];if(!url)return Promise.reject(new Error('Fonte inválida'));
    state.token++;const token=state.token;state.sourceIndex=index;state.sources=sources;destroy();if(target.iframe){target.iframe.src='about:blank';target.iframe.style.display='none'}
    video.style.display='block';video.controls=true;video.autoplay=true;video.playsInline=true;target.loading?.(true,'Preparando fonte '+(index+1)+'…');
    return new Promise(resolve=>{
      let done=false;const finish=(ok,error)=>{if(done||token!==state.token)return;done=true;clearTimeout(state.timers.load);video.onloadedmetadata=null;video.onerror=null;onEvent?.(ok?'ready':'error',{url,error,index});if(ok){resume(target,item);video.play().catch(()=>{});resolve(true)}else if(index+1<sources.length){playDirect(target,item,sources,index+1,onEvent)}else resolve(false)};
      state.timers.load=setTimeout(()=>finish(false,new Error('Tempo excedido')),12000);
      if(/\.m3u8(?:\?|$)/i.test(url)&&!video.canPlayType('application/vnd.apple.mpegurl')){
        loadHLS().then(H=>{if(token!==state.token)return;if(!H?.isSupported?.()){finish(false,new Error('HLS indisponível'));return}const h=new H({enableWorker:true,lowLatencyMode:false,maxBufferLength:18,backBufferLength:8});state.hls=h;h.loadSource(url);h.attachMedia(video);h.on(H.Events.MANIFEST_PARSED,()=>finish(true));h.on(H.Events.ERROR,(_,d)=>{if(d?.fatal){destroy();finish(false,new Error('HLS fatal'))}})}).catch(err=>finish(false,err));
      }else{video.src=url;video.onloadedmetadata=()=>finish(true);video.onerror=()=>finish(false,new Error('Stream indisponível'))}
      const persist=()=>{const p={...progressKey(item,state.type),tempo:Number.isFinite(video.currentTime)?video.currentTime:0,duracao:Number.isFinite(video.duration)&&video.duration>0?video.duration:0,provider:url,season:state.current?.season||1,episode:state.current?.episode||1};window.CineHUBEngineState?.saveProgress(p)};
      video.ontimeupdate=()=>{if(token!==state.token)return;if(video.currentTime>0)persist()};video.onended=()=>{persist();window.CineHUBEngineState?.saveProgress({...progressKey(item,state.type),tempo:0,duracao:0,ended:true})};
    });
  }
  async function play(target,item,type='movie',opts={}){
    state.current={...item,season:opts.season||1,episode:opts.episode||1};state.type=type;state.sources=[];state.sourceIndex=0;
    const direct=playbackCandidates([...(Array.isArray(item?.sources)?item.sources:[]),item?.stream,item?.video]);
    window.CineHUBEngineState?.bumpWatched?.(item);if(type==='series')window.CineHUBEngineState?.saveLastEpisode?.({titulo:item.titulo,season:opts.season||1,episode:opts.episode||1});
    if(direct.length){state.sources=direct;return playDirect(target,item,direct,0,opts.onEvent)}
    if(type==='live')throw new Error('Este canal não possui uma URL de transmissão válida. Atualize as fontes ou escolha outro canal.');
    const candidates=window.CineHUBMegaEmbed?.candidates?.(type,item,opts.season||1,opts.episode||1)||[];if(!candidates.length)throw new Error('Nenhuma fonte disponível');
    destroy();target.video.pause();target.video.removeAttribute('src');target.video.load();target.video.style.display='none';if(target.iframe){target.iframe.style.display='block';target.loading?.(true,type==='series'?'Preparando T'+String(opts.season||1).padStart(2,'0')+'E'+String(opts.episode||1).padStart(2,'0')+'…':'Preparando reprodução…')}
    const token=++state.token;for(let i=0;i<candidates.length;i++){
      await new Promise(resolve=>{let settled=false;clearTimeout(state.timers.embed);state.timers.embed=setTimeout(()=>{if(!settled){settled=true;resolve(false)}},12000);target.iframe.onload=()=>{if(!settled){settled=true;clearTimeout(state.timers.embed);target.loading?.(false);resolve(true)}};target.iframe.onerror=()=>{if(!settled){settled=true;clearTimeout(state.timers.embed);resolve(false)}};target.iframe.src=candidates[i]});if(token!==state.token)return false;if(i===candidates.length-1){target.loading?.(false);throw new Error('A fonte interna não respondeu') } else if(state.current){continue}}
    return false;
  }
  async function playSource(target,index){const item=state.current;if(!item||!state.sources[index])return false;return playDirect(target,item,state.sources,index,(ok)=>{if(target.loading)target.loading(!ok,'Tentando fonte alternativa…')})}
  async function retryNext(target){const item=state.current,type=state.type;if(!item)return false;const next=(state.sourceIndex||0)+1;if(next<state.sources.length)return playDirect(target,item,state.sources,next,()=>{});return play(target,item,type,{season:state.current?.season,episode:state.current?.episode})}
  function stop(target){state.token++;clearTimeout(state.timers.load);clearTimeout(state.timers.embed);destroy();try{target.video.pause();target.video.removeAttribute('src');target.video.load()}catch{}try{target.iframe.src='about:blank'}catch{}}
  window.CineHUBPlayerEngine={state,play,playSource,stop,retryNext,destroy,playbackCandidates,loadHLS};
})();
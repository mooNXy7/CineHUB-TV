/* Adapter: live engine -> TV view model. */
(function(){
'use strict';
function channel(x){const e=window.CineHUBLive?.epgFor?.(x)||{};return {raw:x,name:x?.nome||x?.name||'Canal sem nome',logo:x?.logo||'imagens/sem-capa.jpg',group:x?.grupo||'Outros',stream:x?.stream||'',sources:Array.isArray(x?.sources)?x.sources:[],current:e.current?.title||'Ao vivo',next:e.next?.title||'',start:e.current?.start||'',end:e.current?.end||'',progress:Number(e.progress)||0,live:true};}
function cards(xs){return (Array.isArray(xs)?xs:[]).map(channel).filter(x=>x.name)}
window.CineHUBLiveAdapter={channel,cards};
})();
/* Adapter: engine content -> TV view model. */
(function(){
'use strict';
const image=v=>String(v||'').trim()||'imagens/sem-capa.jpg';
const cleanTitle=v=>{let t=String(v??'').trim();const m=t.match(/^[^,]*?tvg-logo=.*?group-title=.*?,\s*(.+)$/i);if(m)t=m[1].trim();return t||'Sem título'};
const rating=v=>{const n=Number(v);return Number.isFinite(n)&&n>0?n.toFixed(1):'—'};
const year=v=>String(v||'').slice(0,4);
function card(x,type){
 const t=type||x?.__type||x?.tipo||'movie';
 return {raw:x,type:t,id:x?.tmdb||x?.imdb||x?.titulo||x?.nome,title:cleanTitle(x?.titulo||x?.nome||x?.title||x?.name||'Sem título'),poster:image(x?.imagem||x?.poster||x?.logo),backdrop:image(x?.banner||x?.backdrop||x?.imagem||x?.poster||x?.logo),rating:rating(x?.nota||x?.rating||x?.vote_average),year:year(x?.ano||x?.year||x?.release_date||x?.first_air_date),genre:x?.categoria||x?.group||x?.genre||'',synopsis:x?.sinopse||x?.overview||'',stream:x?.stream||'',sources:Array.isArray(x?.sources)?x.sources:[],__seriesIndex:!!x?.__seriesIndex};
}
function cards(items,type){return (Array.isArray(items)?items:[]).map(x=>card(x,type)).filter(x=>x.title)}
function detail(item,d){
 const raw={...item,...(d||{}),imagem:d?.poster_path?'https://image.tmdb.org/t/p/w780'+d.poster_path:item?.imagem,banner:d?.backdrop_path?'https://image.tmdb.org/t/p/w1280'+d.backdrop_path:item?.banner,sinopse:d?.overview||item?.sinopse,titulo:d?.title||d?.name||item?.titulo,ano:(d?.release_date||d?.first_air_date||item?.ano||'').slice(0,4),tmdb:d?.id||item?.tmdb};
 const x=card(raw,item?.__type||item?.tipo);
 return {...x,genres:Array.isArray(d?.genres)?d.genres.map(g=>g.name||g).filter(Boolean):[],runtime:d?.runtime||d?.episode_run_time?.[0]||'',rating:rating(d?.vote_average||item?.nota),cast:Array.isArray(d?.credits?.cast)?d.credits.cast.slice(0,8).map(a=>a.name).filter(Boolean):[],seasons:Number(d?.number_of_seasons||item?.temporadas||0)||0,tmdb:d?.id||item?.tmdb||''};
}
window.CineHUBContentAdapter={card,cards,detail};
})();
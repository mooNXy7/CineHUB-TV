/* CineHUB TV Catalog Engine — data only; no DOM. */
(function(){
  'use strict';
  const clean=v=>String(v??'').trim();
  const typeOf=item=>item?.__type==='series'||item?.tipo==='series'?'series':'movie';
  const normalizeSeriesTitle=title=>String(title||'').replace(/\s*(?:S|T)\d{1,2}\s*E\d{1,3}\b.*$/i,'').replace(/\s+/g,' ').trim();
  function uniqueSeries(items){const seen=new Map();for(const raw of (items||[])){const title=normalizeSeriesTitle(raw?.titulo||raw?.nome);if(!title)continue;const key=title.toLocaleLowerCase('pt-BR');let x=seen.get(key);if(!x){x={...raw,titulo:title,__type:'series',tipo:'series',files:[]};seen.set(key,x)}if(raw?.arquivo&&!x.files.includes(raw.arquivo))x.files.push(raw.arquivo);if(!x.imagem&&raw?.imagem)x.imagem=raw.imagem;if(raw?.banner)x.banner=raw.banner;if(raw?.stream){x.sources=[...(x.sources||[]),raw.stream]}}return [...seen.values()]}
  const normalize=(x,type)=>{
    const t=type==='series'?'series':'movie';
    const title=clean(x?.titulo||x?.nome||x?.title||x?.name||'Sem título');
    const poster=clean(x?.imagem||x?.poster||x?.logo||'');
    const backdrop=clean(x?.banner||x?.backdrop||x?.imagem||x?.poster||x?.logo||'');
    return {...x,tipo:t,__type:t,titulo:title,imagem:poster,banner:backdrop,categoria:clean(x?.categoria||x?.group||x?.genre||''),sinopse:clean(x?.sinopse||x?.overview||''),ano:clean(x?.ano||x?.year||(x?.release_date||x?.first_air_date||'').slice(0,4)),nota:x?.nota||x?.rating||(Number.isFinite(Number(x?.vote_average))?Number(x.vote_average).toFixed(1):'—')};
  };
  async function home(){
    const out={trending:[],movies:[],series:[]};
    const uniq=(arr,type,limit=36)=>{const seen=new Set(),res=[];for(const raw of (arr||[])){const x=normalize(raw,type);const k=String(x.titulo||'').toLocaleLowerCase('pt-BR');if(!k||seen.has(k))continue;seen.add(k);res.push(x);if(res.length>=limit)break}return res};
    let online={trendingMovies:[],trendingSeries:[],newMovies:[],newSeries:[]};
    try{online=await window.CineHUBWeb?.homeFeeds?.()||online}catch(e){console.warn('[CineHUB TV] online home unavailable',e)}
    let localMovies=[],localSeries=[];const large=window.CineHUBLargeCatalog;
    if(large){try{localMovies=await large.featured('movie')}catch{}try{localSeries=await large.featured('series')}catch{}if(!localMovies.length){try{localMovies=await large.first('Filmes | Lançamentos')}catch{}}if(!localSeries.length){try{localSeries=await large.first('Séries | Netflix')}catch{}}}
    const onlineMovies=(online.newMovies||[]).map(x=>normalize(x,'movie'));
    const onlineSeries=uniqueSeries((online.newSeries||[]).map(x=>normalize(x,'series')));
    const trendingOnline=[...(online.trendingMovies||[]).map(x=>normalize(x,'movie')),...(online.trendingSeries||[]).map(x=>normalize(x,'series'))];
    const localM=localMovies.map(x=>normalize(x,'movie')),localS=uniqueSeries(localSeries.map(x=>normalize(x,'series')));
    out.movies=uniq([...localM,...onlineMovies],'movie',24);out.series=uniq([...localS,...onlineSeries],'series',24);
    const trendSeen=new Set();for(const x of [...trendingOnline,...localM,...localS]){const key=String(x.titulo||'').toLocaleLowerCase('pt-BR');if(!key||trendSeen.has(key))continue;trendSeen.add(key);out.trending.push(x);if(out.trending.length>=30)break}
    return out;
  }
  async function findByTitle(type,title){const q=clean(title).toLocaleLowerCase('pt-BR');if(!q)return null;try{const ext=Array.isArray(window.CineHUBExternalCatalog)?window.CineHUBExternalCatalog:[];const hit=ext.find(x=>String(x?.titulo||'').toLocaleLowerCase('pt-BR')===q && (type!=='series'||x?.tipo==='series'));if(hit)return normalize(hit,type)}catch{}try{const large=await window.CineHUBLargeCatalog?.findByTitle?.(type,title);if(large)return normalize(large,type)}catch{}try{const r=await search(type,title,8);return r.find(x=>String(x?.titulo||'').toLocaleLowerCase('pt-BR')===q)||r[0]||null}catch{return null}}
  function categories(type){return window.CineHUBLargeCatalog?.categories?.(type)||[];}
  async function page(type,category='__featured',offset=0,limit=48){
    const large=window.CineHUBLargeCatalog;if(!large)return [];
    if(type==='series'){
      const need=offset+limit,seen=new Map();let guard=0;
      const add=arr=>{for(const raw of (arr||[])){const title=normalizeSeriesTitle(raw?.titulo||raw?.nome);if(!title)continue;const key=title.toLocaleLowerCase('pt-BR');let x=seen.get(key);if(!x){x={...raw,titulo:title,__type:'series',tipo:'series',files:[]};seen.set(key,x)}if(raw?.arquivo&&!x.files.includes(raw.arquivo))x.files.push(raw.arquivo);if(raw?.stream){x.sources=[...(x.sources||[]),raw.stream]}}};
      if(category==='__featured'){try{add(await large.featured('series'))}catch{}if(!seen.size){const cats=(large.categories?.('series')||[]).map(x=>typeof x==='string'?x:x?.name).filter(Boolean);for(const name of cats){try{add(await large.first(name))}catch{}if(seen.size>=need)break}}return [...seen.values()].slice(offset,need).map(x=>normalize(x,'series'));}
      while(seen.size<need && guard<64){const chunk=guard===0?await large.first(category):await large.next(category);if(!chunk.length)break;add(chunk);guard++}
      return [...seen.values()].slice(offset,need).map(x=>normalize(x,'series'));
    }
    if(category==='__featured'){let featured=[];try{featured=await large.featured(type)}catch{}if(!featured.length){const cats=(large.categories?.(type)||[]).map(x=>typeof x==='string'?x:x?.name).filter(Boolean);for(const name of cats){try{featured=await large.first(name)}catch{}if(featured.length)break}}return featured.slice(offset,offset+limit).map(x=>normalize(x,type));}
    if(offset===0)return (await large.first(category)).slice(0,limit).map(x=>normalize(x,type));
    const chunks=[];let cursor=0;
    while(cursor<offset+limit){const x=await large.next(category);if(!x.length)break;chunks.push(...x);cursor+=x.length;if(chunks.length>=offset+limit)break;}
    return chunks.slice(offset,offset+limit).map(x=>normalize(x,type));
  }
  async function search(type,query,limit=30){
    const q=clean(query);if(!q)return [];
    try{
      let ext=window.CineHUBExternalCatalog;
      if(!ext&&window.CineHUBExternalSources?.loadCatalog){const r=await window.CineHUBExternalSources.loadCatalog();if(r?.ok)ext=window.CineHUBExternalCatalog;}
      const hit=(Array.isArray(ext)?ext:[]).filter(x=>{const t=(String(x?.titulo||'')+' '+String(x?.categoria||'')).toLocaleLowerCase('pt-BR');return (x?.tipo===type||!x?.tipo)&&t.includes(q.toLocaleLowerCase('pt-BR'))}).slice(0,limit);
      if(hit.length)return hit.map(x=>normalize(x,type));
    }catch{}
    try{const local=(await window.CineHUBLargeCatalog?.search?.(type,q,limit)||[]).map(x=>normalize(x,type));if(local.length)return local;}catch{}
    try{const r=await window.CineHUBWeb?.searchTMDBResults?.(q,type,1);if(r?.results?.length)return r.results.slice(0,limit).map(x=>normalize(window.CineHUBWeb.tmdbItem?window.CineHUBWeb.tmdbItem(x,type):x,type));}catch{}
    return [];
  }
  async function details(item,type){const t=type||typeOf(item);try{const d=await window.CineHUBWeb?.detailsTMDB?.(item,t);if(d)return d;}catch{}try{if(t==='series')return await window.CineHUBWeb?.tvmaze?.(item.titulo);}catch{}return null}
  async function seriesEpisodes(item,details){
    const base=item?.files?.length?item:((await window.CineHUBLargeCatalog?.findByTitle?.('series',item?.titulo||''))||item);
    const files=Array.isArray(base?.files)?base.files:[];const found=[];const seen=new Set();
    for(const file of files){try{const r=await fetch('dados/conteudo/catalogo/'+file,{cache:'force-cache'});if(!r.ok)continue;const lines=(await r.text()).split(/\r?\n/);for(let i=0;i<lines.length;i++){const l=lines[i];if(!l.startsWith('#EXTINF'))continue;const comma=l.indexOf(','),label=comma>=0?l.slice(comma+1).trim():(l.match(/tvg-name="([^"]+)"/i)?.[1]||'');const m=label.match(/\bS0?(\d{1,2})\s*E0?(\d{1,3})\b/i);if(!m)continue;const normalized=normalizeSeriesTitle(label);if(normalized.toLocaleLowerCase('pt-BR')!==String(item?.titulo||'').toLocaleLowerCase('pt-BR'))continue;const url=(lines[i+1]||'').trim();const key=m[1]+':'+m[2];if(url&&!url.startsWith('#')&&!seen.has(key)){seen.add(key);found.push({id:key,season:Number(m[1]),number:Number(m[2]),name:'Episódio '+String(m[2]).padStart(2,'0'),summary:'',stream:url,thumb:base.imagem||item.imagem})}}}catch{}}
    if(found.length)return found.sort((a,b)=>a.season-b.season||a.number-b.number);
    if(Array.isArray(details?._embedded?.episodes))return details._embedded.episodes;
    try{const tv=await window.CineHUBWeb?.tvmaze?.(item.titulo);return tv?._embedded?.episodes||[];}catch{return []}
  }
  async function resolveEpisode(item,season,episode){try{const base=item?.__seriesIndex?item:((await window.CineHUBLargeCatalog?.findByTitle?.('series',item?.titulo||''))||item);const urls=await window.CineHUBLargeCatalog?.findEpisodeStreams?.(base,season,episode);return urls?.length?{...item,stream:urls[0],sources:[...new Set(urls)]}:item}catch{return item}}
  window.CineHUBCatalog={normalize,typeOf,home,categories,page,search,details,seriesEpisodes,resolveEpisode,findByTitle};
})();
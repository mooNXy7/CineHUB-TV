/* CineHUB TV Online Catalog — compatible with CineHUB OFC webdata.js.
 * Public Supabase key only; TMDB token remains in the Edge Function secret.
 */
(() => {
  'use strict';
  const TTL = 6 * 60 * 60 * 1000;
  const SUPABASE_URL = 'https://qdheeiitzlvynshvkwcs.supabase.co';
  const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_AzpaAW6_qoeiBxy4Pywa4g_XDn2up0z';
  const FUNCTION_URL = SUPABASE_URL + '/functions/v1/cinehub-catalog';
  const get = (k, fallback = null) => { try { const v = localStorage.getItem(k); return v === null ? fallback : JSON.parse(v); } catch { return fallback; } };
  const set = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };
  const cacheGet = key => { const x = get('cinehub_web_' + key); return x && Date.now() - x.t < TTL ? x.v : null; };
  const cacheSet = (key, v) => set('cinehub_web_' + key, { t: Date.now(), v });
  const img = (p, size='w780') => p ? (String(p).startsWith('http') ? p : 'https://image.tmdb.org/t/p/' + size + p) : '';
  const invoke = async body => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(FUNCTION_URL, { method:'POST', headers:{ apikey:SUPABASE_PUBLISHABLE_KEY, 'Content-Type':'application/json' }, body:JSON.stringify(body), signal:controller.signal });
      const text = await response.text(); let payload = null; try { payload = JSON.parse(text); } catch {}
      if (!response.ok) throw new Error(payload?.error || `Supabase HTTP ${response.status}`);
      if (!payload?.success) throw new Error(payload?.error || 'Catálogo online indisponível.');
      return payload.data;
    } finally { clearTimeout(timer); }
  };
  const normalizeType = type => ['series','tv','series','dorama','anime'].includes(String(type||'').toLowerCase()) ? 'tv' : 'movie';
  const GENRES={28:'Ação',12:'Aventura',16:'Animação',35:'Comédia',80:'Crime',99:'Documentário',18:'Drama',10751:'Família',14:'Fantasia',36:'História',27:'Terror',10402:'Música',9648:'Mistério',10749:'Romance',878:'Ficção científica',10770:'Cinema TV',53:'Suspense',10752:'Guerra',37:'Faroeste',10759:'Ação e aventura',10762:'Infantil',10763:'Notícias',10764:'Reality',10765:'Ficção científica e fantasia',10766:'Novela',10767:'Talk show',10768:'Guerra e política'};
  const tmdbItem = (r, type) => {
    const series = normalizeType(type)==='tv', date = r.release_date || r.first_air_date || '';
    return { id:'tmdb-'+(series?'tv':'movie')+'-'+r.id, tmdb:String(r.id||''), title:r.title||r.name||'Sem título',
      type:series?'series':'movie', view:series?'series':'filmes', category:(r.genre_ids||[]).map(id=>GENRES[id]).filter(Boolean).slice(0,2).join(' · ') || (series?'Série':'Filme'),
      year:date.slice(0,4), rating:r.vote_average ? Number(r.vote_average).toFixed(1) : '—',
      poster:img(r.poster_path), backdrop:img(r.backdrop_path,'w1280'), description:r.overview||'',
      sinopse:r.overview||'', url:'', online:true, originalData:r };
  };
  const searchTMDBResults = async (query,type,page=1) => {
    const q=String(query||'').trim(); if(!q)return {page:1,total_pages:0,total_results:0,results:[]};
    const t=normalizeType(type), p=Math.max(1,Number(page)||1), key=`search_${t}_${q.toLowerCase()}_${p}`;
    const cached=cacheGet(key); if(cached)return cached;
    const data=await invoke({action:'search',type:t,query:q,page:p}); cacheSet(key,data); return data;
  };
  const searchTMDB = async (item,type) => {
    const data=await searchTMDBResults(item?.title||item?.titulo||'',type,1); return data.results?.[0]||null;
  };
  const detailsTMDB = async (item,type) => {
    const t=normalizeType(type), direct=item?.tmdb||item?.tmdb_id||item?.tmdbId;
    const hit=direct?{id:direct}:await searchTMDB(item,t); if(!hit)return null;
    const key=`details_${t}_${hit.id}`, cached=cacheGet(key); if(cached)return cached;
    const data=await invoke({action:'details',type:t,id:String(hit.id)}); cacheSet(key,data); return data;
  };
  const feed = async (action,type,limit=20) => {
    const t=normalizeType(type), key=`feed_${action}_${t}`, cached=cacheGet(key);
    const data=cached||await invoke({action,type:t,page:1}); if(!cached)cacheSet(key,data);
    return (data?.results||[]).filter(x=>x.poster_path).slice(0,limit).map(x=>tmdbItem(x,t));
  };
  const homeFeeds = async () => {
    const cached=cacheGet('home_feeds_tv_v1'); if(cached)return cached;
    const results=await Promise.allSettled([feed('trending','movie',20),feed('trending','tv',20),feed('now_playing','movie',20),feed('now_playing','tv',20)]);
    const val=x=>x.status==='fulfilled'?x.value:[];
    const out={trendingMovies:val(results[0]),trendingSeries:val(results[1]),newMovies:val(results[2]),newSeries:val(results[3])};
    cacheSet('home_feeds_tv_v1',out); return out;
  };
  const tvmaze=async title=>{const key='tvmaze_'+encodeURIComponent(title).replace(/%/g,'_'),cached=cacheGet(key);if(cached)return cached;const r=await fetch('https://api.tvmaze.com/singlesearch/shows?q='+encodeURIComponent(title)+'&embed=episodes');if(!r.ok)throw new Error('TVmaze HTTP '+r.status);const data=await r.json();cacheSet(key,data);return data;};
  const schedule=async date=>{const d=date||new Date().toISOString().slice(0,10),key='tvmaze_schedule_'+d,cached=cacheGet(key);if(cached)return cached;const r=await fetch('https://api.tvmaze.com/schedule?country=BR&date='+d);if(!r.ok)throw new Error('TVmaze HTTP '+r.status);const data=await r.json();cacheSet(key,data);return data;};
  const clearCache=()=>Object.keys(localStorage).filter(k=>k.startsWith('cinehub_web_')).forEach(k=>localStorage.removeItem(k));
  window.CineHUBWeb={token:()=>'',invoke,searchTMDB,searchTMDBResults,tmdbItem,detailsTMDB,tvmaze,schedule,homeFeeds,feed,imageUrl:img,hasTMDB:()=>true,clearCache};
})();

(() => {
  "use strict";
  const views={
    buscar:{title:"Buscar",description:"Encontre conteúdos conforme digita.",kicker:"PESQUISA DINÂMICA",prompt:"O que vamos assistir?",overline:"BUSCA CINEHUB!",shelf:"Resultados para descobrir"},
    inicio:{title:"Home",description:"A sua central de entretenimento.",kicker:"CINEHUB TV"},
    filmes:{title:"Filmes",description:"Explore filmes por categoria ou pesquise pelo título.",kicker:"CATÁLOGO",shelf:"Filmes para descobrir"},
    series:{title:"Séries",description:"Explore séries por categoria ou pesquise pelo título.",kicker:"CATÁLOGO",shelf:"Séries para maratonar"},
    "ao-vivo":{title:"Ao vivo",description:"Canais, categorias e programação em uma área feita para TV.",kicker:"TV AO VIVO",shelf:"Canais para maratonar"},
    radio:{title:"Rádios",description:"Explore estações por categoria ou pesquise pelo nome.",kicker:"ÁUDIO",shelf:"Estações para descobrir"},
    doramas:{title:"Doramas",description:"Histórias para se apaixonar e maratonar.",kicker:"CATÁLOGO",prompt:"Qual história vamos começar?",overline:"DORAMAS CINEHUB!",shelf:"Doramas para descobrir"},
    animes:{title:"Animes",description:"Novos mundos esperam por você.",kicker:"CATÁLOGO",prompt:"Qual universo vamos explorar?",overline:"ANIMES CINEHUB!",shelf:"Animes para descobrir"},
    lista:{title:"Minha Lista",description:"Seus filmes, séries e canais favoritos em um só lugar.",kicker:"SEUS FAVORITOS",shelf:"Seus favoritos de conteúdo"},
    guia:{title:"Guia de programação",description:"Consulte os canais e a programação disponível.",kicker:"CINEHUB | GUIA",prompt:"O que está passando?",overline:"GUIA DE TV",shelf:"Canais no guia"}
  };
  const $=id=>document.getElementById(id);
  const home=$('mainContent'),catalog=$('catalogView'),nav=$('mainNav'),title=$('catalogTitle'),description=$('catalogDescription'),kicker=$('catalogKicker');
  const input=$('searchInput'),searchSummary=$('searchSummary'),searchResults=$('searchResults'),catalogCards=$('catalogCards'),emptyState=$('emptyState');
  const settingsDialog=$('settingsDialog');let currentView='inicio',activeCategory='Tudo',loaderTimer=null;
  const STORE={favorites:'cinehub.v12.favorites',last:'cinehub.v12.last',history:'cinehub.v15.history',welcomed:'cinehub.v13.welcomed'};
  let heroItem=null;
  let catalogItems=[
    {id:'demo-movie-01',title:'Filme de teste',type:'movie',view:'filmes',category:'Demonstração',demo:true,description:'Card demonstrativo para testar a interface.'},
    {id:'demo-series-01',title:'Série de teste',type:'series',view:'series',category:'Demonstração',demo:true,description:'Card demonstrativo para testar a interface.'},
    {id:'demo-channel-01',title:'Canal de teste',type:'channel',view:'ao-vivo',category:'Demonstração',demo:true,description:'Card demonstrativo para testar a interface.'}
  ];
  let onlineSearchTimer=null, onlineSearchRun=0, onlineReady=false;
  const mergeOnlineItems=(items)=>{const clean=(items||[]).filter(x=>x&&x.id&&x.title);const keep=catalogItems.filter(x=>!x.demo);const map=new Map(keep.map(x=>[String(x.id),x]));clean.forEach(x=>map.set(String(x.id),x));catalogItems=[...catalogItems.filter(x=>x.demo),...map.values()];return clean;};
  const tvTypeForView=view=>view==='series'||view==='doramas'||view==='animes'?'tv':'movie';
  const toOnlineCard=x=>({...x,year:x.year||'',description:x.description||x.sinopse||'',poster:x.poster||x.imagem||'',backdrop:x.backdrop||x.banner||'',view:x.view||(x.type==='series'?'series':'filmes')});
  const read=(key,fallback=[])=>{try{const value=JSON.parse(localStorage.getItem(key));return value??fallback}catch{return fallback}};
  const write=(key,value)=>{try{localStorage.setItem(key,JSON.stringify(value))}catch{}};
  const safe=(s='')=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const normalize=s=>String(s||'').toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g,'');
  function setActive(view){nav.querySelectorAll('[data-view]').forEach(b=>{const on=b.dataset.view===view;b.classList.toggle('is-active',on);if(on)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current')})}
  function toast(message,titleText='CineHUB TV',duration=4800){const stack=$('toastStack');const node=document.createElement('div');node.className='cine-toast';node.style.setProperty('--toast-duration',duration+'ms');node.innerHTML=`<div class="cine-toast-logo"><img src="assets/logos/simbolo.png" alt="CineHUB"></div><div class="cine-toast-copy"><strong>${safe(titleText)}</strong><p>${safe(message)}</p></div><button class="cine-toast-close" type="button" aria-label="Fechar notificação">×</button><span class="cine-toast-progress"></span>`;stack.appendChild(node);let timer=setTimeout(remove,duration);function remove(){clearTimeout(timer);if(!node.isConnected)return;node.classList.add('is-leaving');setTimeout(()=>node.remove(),230)}node.querySelector('.cine-toast-close').addEventListener('click',remove);while(stack.children.length>3)stack.firstElementChild.remove();return node}
  function showLoader(){const el=$('pageLoader');clearTimeout(loaderTimer);el.classList.add('is-visible');loaderTimer=setTimeout(()=>el.classList.remove('is-visible'),460)}
  function cardMarkup(item,opts={}){const canonicalCategory=({'Action':'Ação','Adventure':'Aventura','Horror':'Terror','Kids':'Infantil','Children':'Infantil','News':'Notícias'})[item.category]||item.category;item={...item,category:canonicalCategory};const favs=read(STORE.favorites),isFav=favs.some(x=>String(x.id)===String(item.id));const typeLabel=item.type==='channel'?'Canal':item.type==='series'?'Série':item.type==='anime'?'Anime':item.type==='dorama'?'Dorama':item.type==='radio'?'Rádio':'Filme';return `<article class="media-card ${opts.compact?'media-card-compact':''}" data-item-id="${safe(item.id)}"><button class="favorite-button ${isFav?'is-favorite':''}" data-favorite="${safe(item.id)}" aria-label="${isFav?'Remover dos favoritos':'Adicionar aos favoritos'}" aria-pressed="${isFav}" title="${isFav?'Remover da Minha Lista':'Adicionar à Minha Lista'}">${isFav?'♥':'♡'}</button><button class="media-card-open" data-open-item="${safe(item.id)}" aria-label="Ver detalhes de ${safe(item.title||'conteúdo sem título')}"><div class="media-poster">${item.poster?`<img src="${safe(item.poster)}" alt="" loading="lazy" decoding="async">`:`<span class="poster-symbol">${item.type==='channel'?'◉':item.type==='series'?'▤':item.type==='radio'?'♫':'▰'}</span><span class="poster-placeholder">${item.demo?'CARD DE TESTE':'SEM CAPA'}</span>`}</div><div class="media-meta"><strong>${safe(item.title||'Conteúdo sem título')}</strong><span>${safe(item.category||typeLabel)}${item.year?' · '+safe(String(item.year).slice(0,4)):''}${item.rating?' · ★ '+safe(String(item.rating)):''}${item.demo?' · teste':''}</span></div></button></article>`}
  function contentForView(view){if(view==='lista')return read(STORE.favorites);if(view==='guia')return catalogItems.filter(x=>x.type==='channel'||x.view==='ao-vivo');return catalogItems.filter(x=>x.view===view||x.type===view||(view==='ao-vivo'&&x.type==='channel')||(view==='filmes'&&x.type==='movie')||(view==='series'&&x.type==='series')||(view==='doramas'&&x.type==='dorama')||(view==='animes'&&x.type==='anime')||(view==='radio'&&x.type==='radio'))}
  function renderOnlineShelf(id,items){const el=$(id);if(!el)return;el.innerHTML=items.length?items.map(x=>cardMarkup(x,{compact:true})).join(''):`<div class="section-empty"><strong>Nenhum conteúdo online disponível agora.</strong></div>`;}
  async function loadViewFeed(view){
    if(!window.CineHUBWeb)return;
    try{
      let items=[];
      if(view==='filmes')items=await window.CineHUBWeb.feed?.('trending','movie',20);
      else if(view==='series'||view==='doramas'||view==='animes')items=await window.CineHUBWeb.feed?.('trending','tv',20);
      else if(view==='guia') { const channels=contentForView('ao-vivo').filter(x=>!x.demo); $('catalogShelfTitle').textContent='Canais disponíveis'; $('catalogCount').textContent=`${channels.length} CANAIS`; catalogCards.innerHTML=channels.map(x=>cardMarkup(x)).join(''); catalogCards.hidden=!channels.length; emptyState.hidden=channels.length>0; renderCategoryRail(view); return; }
      else return;
      const mapped=(items||[]).map(toOnlineCard);
      if(view==='doramas')mapped.forEach(x=>{x.view='doramas';x.type='dorama'});
      if(view==='animes')mapped.forEach(x=>{x.view='animes';x.type='anime'});
      mergeOnlineItems(mapped);
      const list=filteredItems(view).filter(x=>!x.demo);
      catalogCards.innerHTML=list.map(x=>cardMarkup(x)).join('');catalogCards.hidden=!list.length;emptyState.hidden=true;
      $('catalogCount').textContent=`${list.length} CONTEÚDO(S) ONLINE`;
      renderCategoryRail(view);renderCatalogFavorites(view);
    }catch(e){$('catalogCount').textContent='CATÁLOGO ONLINE INDISPONÍVEL';toast('Não foi possível conectar ao catálogo online.','Conexão',3500)}
  }
  async function loadOnlineHome(){
    const status=$('onlineStatus');
    if(!window.CineHUBWeb){if(status)status.textContent='Catálogo não configurado';return}
    try{
      const feeds=await window.CineHUBWeb.homeFeeds();
      const yearNow='2026';
      let releases=(feeds.newMovies||[]).filter(x=>String(x.year)===yearNow);
      if(releases.length<5)releases=[...releases,...(feeds.trendingMovies||[]).filter(x=>String(x.year)===yearNow&&!releases.some(y=>y.id===x.id))];
      if(!releases.length)releases=(feeds.newMovies||[]).slice(0,12);
      const trending=(feeds.trendingMovies||[]).map(toOnlineCard), series=(feeds.trendingSeries||[]).map(toOnlineCard), mappedReleases=releases.map(toOnlineCard);
      mergeOnlineItems([...mappedReleases,...trending,...series]);
      renderOnlineShelf('newMoviesContent',mappedReleases);
      renderOnlineShelf('trendingMoviesContent',trending);
      renderOnlineShelf('trendingSeriesContent',series);
      if(status)status.textContent='Catálogo online conectado';
      const heroPool=[...mappedReleases,...trending,...series]
        .filter((item,index,all)=>item&&!item.demo&&all.findIndex(x=>String(x.id)===String(item.id))===index)
        .sort((a,b)=>Number(String(b.year||0)) - Number(String(a.year||0)));
      let heroIndex=0;
      const paintHero=(item)=>{
        if(!item)return;
        heroItem=item;
        const section=$('homeHero'),heroImage=item.backdrop||item.poster;
        section.style.backgroundImage=heroImage
          ? `linear-gradient(90deg,rgba(5,5,5,.92) 0%,rgba(5,5,5,.70) 37%,rgba(5,5,5,.12) 100%),linear-gradient(0deg,rgba(5,5,5,.72),transparent 55%),url("${heroImage}")`
          : 'linear-gradient(105deg,#140607,#080808)';
        $('heroTitle').textContent=item.title||'Lançamento';
        $('heroMeta').textContent=`${item.category|| (item.type==='series'?'Série':'Filme')}${item.rating&&item.rating!=='—'?'  ·  ★ '+item.rating:''}${item.year?'  ·  '+item.year:''}`;
        $('heroCopy').textContent=item.description||item.sinopse||'Confira os detalhes deste destaque do catálogo online.';
        $('heroDetails').dataset.openItem=item.id;
        $('heroPlay').dataset.heroWatch=item.id;
        section.dataset.heroType=item.type||'movie';
      };
      if(heroPool.length){
        paintHero(heroPool[0]);
        if(window.cinehubHeroTimer)clearInterval(window.cinehubHeroTimer);
        if(heroPool.length>1)window.cinehubHeroTimer=setInterval(()=>{
          if(document.hidden||currentView!=='inicio')return;
          heroIndex=(heroIndex+1)%heroPool.length;
          const section=$('homeHero');
          section.classList.add('hero-changing');
          window.setTimeout(()=>{paintHero(heroPool[heroIndex]);section.classList.remove('hero-changing')},220);
        },8000);
      }
      onlineReady=true;
      if(currentView!=='inicio'&&['filmes','series','doramas','animes'].includes(currentView)){
        const items=filteredItems(currentView).filter(x=>!x.demo);
        if(items.length){catalogCards.innerHTML=items.map(x=>cardMarkup(x)).join('');catalogCards.hidden=false;emptyState.hidden=true;$('catalogCount').textContent=`${items.length} CONTEÚDO(S) ONLINE`;}
      }
    }catch(err){if(status)status.textContent='Não foi possível conectar; tente novamente.';const copy=$('heroCopy');if(copy)copy.textContent='Não foi possível carregar os lançamentos online. Confira a conexão e tente novamente.'}
  }
  function renderHomeSections(){const history=read(STORE.history,[]),continueSection=$('continueSection'),continueEl=$('continueContent'),listEl=$('myListContent');continueSection.hidden=!history.length;continueEl.innerHTML=history.length?history.slice(0,12).map(x=>cardMarkup(x,{compact:true})).join(''):'';const favs=read(STORE.favorites);if(listEl)listEl.innerHTML=favs.length?favs.map(x=>cardMarkup(x,{compact:true})).join(''):`<div class="section-empty"><span class="empty-mini-icon">♡</span><div><strong>Sua lista está vazia</strong><p>Use o coração nos cards para guardar seus favoritos.</p></div></div>`;const online=$('onlineHomeSection');if(online&&history.length){const first=online.firstElementChild; if(first&&first.id!=='continueSection'){} }}
  function filteredItems(view){let items=contentForView(view);if(activeCategory!=='Tudo')items=items.filter(x=>(x.category||'Sem categoria')===activeCategory);return items}
  function renderCategoryRail(view){const items=contentForView(view);const defaults={filmes:['Ação','Aventura','Comédia','Drama','Ficção científica','Suspense'],series:['Drama','Comédia','Crime','Ficção científica','Aventura'], 'ao-vivo':['Infantil','Abertos','24h','Esportes','Notícias','Filmes e séries'],guia:['Infantil','Abertos','24h','Esportes','Notícias'],radio:['Nacional','Sul','Música','Notícias','Esportes'],doramas:['Romance','Drama','Comédia','Suspense'],animes:['Ação','Aventura','Fantasia','Comédia'],buscar:[],lista:[]};const categories=['Tudo',...new Set([...(defaults[view]||[]),...items.map(x=>x.category||'Sem categoria')])];$('categoryRail').innerHTML=categories.map(cat=>`<button type="button" class="category-chip ${activeCategory===cat?'is-active':''}" data-category="${safe(cat)}">${safe(cat)}</button>`).join('')}
  function renderCatalog(view){activeCategory='Tudo';const items=filteredItems(view).filter(x=>!x.demo);catalogCards.innerHTML=items.map(x=>cardMarkup(x)).join('');$('catalogCount').textContent=items.length?`${items.length.toLocaleString('pt-BR')} conteúdos encontrados`:'Carregando catálogo online…';$('catalogShelfTitle').textContent=view==='buscar'?'Conteúdos recomendados':((views[view]||views.filmes).shelf||'Conteúdos');emptyState.hidden=true;catalogCards.hidden=!items.length;renderCategoryRail(view);renderCatalogFavorites(view);const isLive=view==='ao-vivo';$('guideShortcut').hidden=!isLive;$('liveGuidePanel').hidden=!isLive;$('catalogToolbar').hidden=view==='buscar';$('categoryRail').hidden=view==='buscar';$('favoritesSection').hidden=view==='buscar';$('catalogShelfSection').hidden=false;catalog.classList.toggle('is-search-view',view==='buscar');catalog.classList.toggle('is-live-view',isLive);renderSearchResults();if(!items.length)loadViewFeed(view);}
  function renderCatalogFavorites(view){const el=$('catalogFavorites');const titleEl=$('catalogFavoritesTitle');const typeNames={filmes:'Filmes',series:'Séries','ao-vivo':'Canais',radio:'Rádio',doramas:'Doramas',animes:'Animes',buscar:'Conteúdos',lista:'Conteúdos'};titleEl.textContent=`Seus favoritos de ${typeNames[view]||'Conteúdo'}`;const all=read(STORE.favorites);const allowed=view==='buscar'||view==='lista'?all:all.filter(x=>x.view===view||x.type===view||(view==='ao-vivo'&&x.type==='channel')||(view==='filmes'&&x.type==='movie'));el.innerHTML=allowed.length?allowed.map(x=>cardMarkup(x,{compact:true})).join(''):`<div class="section-empty"><span class="empty-mini-icon">♡</span><div><strong>Nenhum favorito nesta área ainda</strong><p>Toque no coração de um card para guardar aqui.</p></div></div>`}
  function renderSearchResults(){
    if(!input)return;
    const q=input.value.trim(), run=++onlineSearchRun, searching=!!q;
    const pool=currentView==='buscar'?catalogItems:contentForView(currentView);
    let results=searching?pool.filter(x=>!x.demo&&[x.title,x.category,x.description,x.type,x.year].some(v=>normalize(v).includes(normalize(q)))):[];
    searchSummary.textContent=!searching?'Digite para pesquisar no catálogo local e online.':`${results.length} resultado(s) local(is). Pesquisando online…`;
    searchResults.innerHTML=results.map(x=>cardMarkup(x)).join('');
    $('resultsCount').textContent=`${results.length} ${results.length===1?'item':'itens'}`;
    $('searchResultsArea').hidden=!searching;
    $('catalogDefaultContent').hidden=searching&&currentView!=='buscar';
    if(currentView==='buscar'){ $('favoritesSection').hidden=true; $('catalogShelfSection').hidden=false; $('catalogShelfTitle').textContent='Conteúdos recomendados'; }
    $('searchEmpty').hidden=!searching||results.length>0;
    searchResults.hidden=!results.length;
    if(!searching||!window.CineHUBWeb)return;
    clearTimeout(onlineSearchTimer);
    onlineSearchTimer=setTimeout(async()=>{
      try{
        const types=(currentView==='series'||currentView==='doramas'||currentView==='animes')?['tv']:(currentView==='filmes'?['movie']:(currentView==='buscar'?['movie','tv']:[]));
        if(!types.length)return;
        const responses=await Promise.allSettled(types.map(t=>window.CineHUBWeb.searchTMDBResults(q,t,1)));
        if(run!==onlineSearchRun)return;
        const found=[];
        responses.forEach((r,i)=>{if(r.status==='fulfilled')(r.value.results||[]).forEach(x=>found.push(window.CineHUBWeb.tmdbItem(x,types[i])))});
        const mapped=found.map(toOnlineCard);mergeOnlineItems(mapped);
        const byId=new Map();[...results,...mapped].forEach(x=>{if(!x.demo)byId.set(String(x.id),x)});
        const final=[...byId.values()];
        searchSummary.textContent=final.length?`${final.length} resultado(s) · catálogo local e online para “${q}”.`:`Nenhum resultado encontrado para “${q}”.`;
        searchResults.innerHTML=final.map(x=>cardMarkup(x)).join('');
        $('resultsCount').textContent=`${final.length} ${final.length===1?'item':'itens'}`;
        $('searchEmpty').hidden=final.length>0;searchResults.hidden=!final.length;
      }catch(err){if(run===onlineSearchRun)searchSummary.textContent=`Pesquisa online indisponível no momento. ${results.length?results.length+' resultado(s) local(is) exibido(s).':'Tente novamente mais tarde.'}`}
    },320);
  }
  function syncSearch(){renderSearchResults()}
  function showView(view,updateHash=true,animate=true){if(!views[view])view='inicio';if(animate&&view!==currentView)showLoader();currentView=view;setActive(view);const isHome=view==='inicio';document.body.classList.toggle('catalog-active',!isHome);home.hidden=!isHome;catalog.hidden=isHome;if(!isHome){const meta=views[view];title.textContent=meta.title;description.textContent=meta.description;kicker.textContent=meta.kicker;$('catalogSearchLayout').hidden=false;$('searchLabel').textContent=view==='buscar'?'Buscar em todo o CineHUB':'Pesquisar em '+meta.title;$('searchInput').placeholder=view==='ao-vivo'?'Nome do canal...':view==='radio'?'Nome da estação...':'Digite o nome...';$('catalogToolbar')?.removeAttribute('hidden');$('guideShortcut').hidden=view!=='ao-vivo';$('liveGuidePanel').hidden=view!=='ao-vivo';renderCatalog(view)}if(updateHash){const hash=view==='inicio'?'#inicio':`#${view}`;if(location.hash!==hash)history.replaceState(null,'',hash)}window.scrollTo({top:0,behavior:'smooth'});(isHome?home:catalog).focus({preventScroll:true})}
  function toggleFavorite(id){let favs=read(STORE.favorites);const item=catalogItems.find(x=>String(x.id)===String(id))||favs.find(x=>String(x.id)===String(id))||(read(STORE.last,null)?.id===id?read(STORE.last,null):null);if(!item)return;const exists=favs.some(x=>String(x.id)===String(id));favs=exists?favs.filter(x=>String(x.id)!==String(id)):[item,...favs];write(STORE.favorites,favs);renderHomeSections();renderCatalogFavorites(currentView);if(currentView==='lista')renderCatalog('lista');else if(currentView==='buscar')renderSearchResults();else {renderCategoryRail(currentView);const items=filteredItems(currentView);catalogCards.innerHTML=items.map(x=>cardMarkup(x)).join('');catalogCards.hidden=!items.length;emptyState.hidden=items.length>0}if(!exists)toast(`${item.title} adicionado em “Minha lista”. Não se esqueça de assistir, hein!`,'Boa!',5200);else toast(`${item.title} removido da sua lista.`,'Lista atualizada',3200)}
  let activeDetailItem=null;
  async function openItem(id){
    const item=catalogItems.find(x=>String(x.id)===String(id))||read(STORE.favorites).find(x=>String(x.id)===String(id));
    if(!item)return;
    if(item.demo){toast('Este é um card de demonstração.','Card de teste',4300);return}
    activeDetailItem=item;
    const dialog=$('detailsDialog'),body=$('detailsBody');
    $('detailsTitle').textContent=item.title||'Detalhes do conteúdo';
    body.innerHTML='<div class="details-loading">Carregando sinopse e informações online…</div>';
    if(!dialog.open)dialog.showModal();
    try{
      let detail=null;
      const mediaType=['series','anime','dorama'].includes(item.type)?'tv':'movie';
      if(window.CineHUBWeb&&(item.tmdb||item.online))detail=await window.CineHUBWeb.detailsTMDB(item,mediaType);
      const d=detail||item,overview=d.overview||d.sinopse||d.description||'Sinopse indisponível para este título.';
      const poster=d.poster_path&&window.CineHUBWeb?window.CineHUBWeb.imageUrl(d.poster_path):item.poster;
      const date=d.release_date||d.first_air_date||item.year||'';
      const rating=d.vote_average?Number(d.vote_average).toFixed(1):(item.rating||'—');
      const genres=Array.isArray(d.genres)?d.genres.map(g=>g.name).join(' · '):item.category||'';
      const cast=Array.isArray(d.credits?.cast)?d.credits.cast.slice(0,8).map(c=>c.name).join(', '):'';
      const isFav=read(STORE.favorites).some(x=>String(x.id)===String(item.id));
      const typeLabel=item.type==='series'?'Série':item.type==='anime'?'Anime':item.type==='dorama'?'Dorama':item.type==='channel'?'Canal':'Filme';
      body.innerHTML=`<div class="details-layout">${poster?`<img class="details-poster" src="${safe(poster)}" alt="Capa de ${safe(item.title)}" loading="eager">`:'<div class="details-poster details-poster-fallback">CINEHUB</div>'}<div class="details-copy"><p class="section-kicker">${safe(typeLabel)} · ${safe(String(date).slice(0,4)||'Ano não informado')}</p><h3>${safe(d.title||d.name||item.title)}</h3><p class="details-meta">★ ${safe(String(rating))} ${genres?' · '+safe(genres):''}</p><p class="details-overview">${safe(overview)}</p>${cast?`<p class="details-cast"><strong>Elenco:</strong> ${safe(cast)}</p>`:''}<div class="details-actions"><button class="primary-button details-watch" data-detail-watch="${safe(item.id)}">▶ Assistir</button><button class="secondary-button details-favorite ${isFav?'is-favorite':''}" data-detail-favorite="${safe(item.id)}" aria-pressed="${isFav}">${isFav?'♥ Na Minha Lista':'♡ Favoritar'}</button></div><p class="details-source">Informações do catálogo online. A reprodução depende de uma fonte disponível para este título.</p></div></div>`;
    }catch(err){
      const isFav=read(STORE.favorites).some(x=>String(x.id)===String(item.id));
      body.innerHTML=`<div class="details-layout">${item.poster?`<img class="details-poster" src="${safe(item.poster)}" alt="Capa de ${safe(item.title)}">`:'<div class="details-poster details-poster-fallback">CINEHUB</div>'}<div class="details-copy"><p class="section-kicker">${safe(item.type||'Conteúdo')} · ${safe(String(item.year||'Ano não informado').slice(0,4))}</p><h3>${safe(item.title||'Detalhes do conteúdo')}</h3><p class="details-meta">★ ${safe(String(item.rating||'—'))} · ${safe(item.category||'')}</p><p class="details-overview">${safe(item.description||'Não foi possível carregar os detalhes online. O catálogo local continua disponível.')}</p><div class="details-actions"><button class="primary-button details-watch" data-detail-watch="${safe(item.id)}">▶ Assistir</button><button class="secondary-button details-favorite ${isFav?'is-favorite':''}" data-detail-favorite="${safe(item.id)}" aria-pressed="${isFav}">${isFav?'♥ Na Minha Lista':'♡ Favoritar'}</button></div><p class="details-source">Os dados online não responderam. Você ainda pode usar as ações disponíveis para este conteúdo.</p></div></div>`;
    }
  }
  function insertKey(key){const start=input.selectionStart??input.value.length,end=input.selectionEnd??input.value.length;let value=input.value;if(key==='CLEAR')value='';else if(key==='BACKSPACE'){if(start===end&&start>0)value=value.slice(0,start-1)+value.slice(end);else value=value.slice(0,start)+value.slice(end)}else if(key==='SPACE')value=value.slice(0,start)+' '+value.slice(end);else value=value.slice(0,start)+key+value.slice(end);input.value=value;input.focus();const pos=key==='BACKSPACE'?Math.max(0,start-(start===end?1:0)):key==='CLEAR'?0:start+(key==='SPACE'?1:key.length);try{input.setSelectionRange(pos,pos)}catch{}syncSearch()}
  document.addEventListener('click',e=>{const heroDetail=e.target.closest('#heroDetails[data-open-item]');if(heroDetail){openItem(heroDetail.dataset.openItem);return}const heroWatch=e.target.closest('#heroPlay[data-hero-watch]');if(heroWatch){const item=catalogItems.find(x=>String(x.id)===String(heroWatch.dataset.heroWatch))||heroItem;if(item&&item.url){window.location.href=item.url}else if(item){toast('A fonte de reprodução não está vinculada a este título. Abrindo os detalhes disponíveis.','Reprodução',3800);openItem(item.id)}return}const viewBtn=e.target.closest('[data-view]');if(viewBtn){showView(viewBtn.dataset.view);return}const fav=e.target.closest('[data-favorite]');if(fav){e.preventDefault();e.stopPropagation();toggleFavorite(fav.dataset.favorite);return}const detailFav=e.target.closest('[data-detail-favorite]');if(detailFav){e.preventDefault();toggleFavorite(detailFav.dataset.detailFavorite);if(activeDetailItem)openItem(activeDetailItem.id);return}const detailWatch=e.target.closest('[data-detail-watch]');if(detailWatch){const item=activeDetailItem;if(!item)return;if(item.url){write(STORE.last,item);let history=read(STORE.history,[]).filter(x=>String(x.id)!==String(item.id));history.unshift(item);write(STORE.history,history.slice(0,20));renderHomeSections();window.location.href=item.url}else toast('Ainda não há uma fonte de reprodução vinculada a este título.','Reprodução indisponível',4000);return}const open=e.target.closest('[data-open-item]');if(open){openItem(open.dataset.openItem);return}const key=e.target.closest('[data-key]');if(key){insertKey(key.dataset.key);return}const cat=e.target.closest('[data-category]');if(cat){activeCategory=cat.dataset.category;renderCategoryRail(currentView);const items=filteredItems(currentView);catalogCards.innerHTML=items.map(x=>cardMarkup(x)).join('');catalogCards.hidden=!items.length;emptyState.hidden=items.length>0;$('catalogCount').textContent=items.length?`${items.length} ${items.length===1?'ITEM':'ITENS'} · ${activeCategory.toUpperCase()}`:'NENHUM ITEM NESTA CATEGORIA';return}if(e.target.closest('#backHome')||e.target.closest('#emptyBack')){showView('inicio');return}if(e.target.closest('#catalogSearch')){showView('buscar');input.focus();return}if(e.target.closest('#clearSearch')){insertKey('CLEAR');return}if(e.target.closest('#settingsToggle')){settingsDialog.showModal();return}});
  input.addEventListener('input',syncSearch);$('closeDetails').addEventListener('click',()=> $('detailsDialog').close());$('detailsDialog').addEventListener('click',e=>{if(e.target===$('detailsDialog'))$('detailsDialog').close()});$('closeSettings').addEventListener('click',()=>settingsDialog.close());settingsDialog.addEventListener('click',e=>{if(e.target===settingsDialog)settingsDialog.close()});
  $('clearLocalData').addEventListener('click',()=>{try{localStorage.removeItem(STORE.favorites);localStorage.removeItem(STORE.last);localStorage.removeItem(STORE.history)}catch{}renderHomeSections();renderCatalogFavorites(currentView);if(currentView!=='inicio')renderCatalog(currentView);$('settingsFeedback').textContent='Favoritos e histórico local foram limpos.';toast('Seus favoritos e histórico locais foram limpos.','Tudo limpo',3200)});
  function updateClock(){const now=new Date();$('brClock').textContent=new Intl.DateTimeFormat('pt-BR',{timeZone:'America/Sao_Paulo',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(now);$('brClock').dateTime=now.toISOString()}updateClock();setInterval(updateClock,1000);
  document.addEventListener('keydown',e=>{if(settingsDialog.open){if(e.key==='Escape')settingsDialog.close();return}if(['Escape','BrowserBack'].includes(e.key)&&currentView!=='inicio'){e.preventDefault();showView('inicio')}else if(e.key==='Home'&&document.activeElement!==input){e.preventDefault();showView('inicio')}});
  window.addEventListener('hashchange',()=>{const view=location.hash.replace(/^#/,'')||'inicio';if(views[view]&&view!==currentView)showView(view,false,false)});
  window.CineHUB={registerContent(items){if(!Array.isArray(items))return;const incoming=items.filter(x=>x&&x.id!=null&&x.title).map(x=>({...x,id:String(x.id)}));const map=new Map(catalogItems.filter(x=>!x.demo).map(x=>[x.id,x]));incoming.forEach(x=>map.set(x.id,x));catalogItems=[...catalogItems.filter(x=>x.demo),...map.values()];renderHomeSections();if(currentView!=='inicio')renderCatalog(currentView);toast(`${incoming.length} conteúdo(s) registrado(s) no catálogo.`,'Catálogo atualizado',3000)},getContent(){return [...catalogItems]}};
  // Progressive reveal: applies to current and dynamically inserted cards without blocking navigation.
  document.documentElement.classList.add('reveal-ready');
  let revealObserver=null;
  if('IntersectionObserver' in window){
    revealObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.add('is-revealed');revealObserver.unobserve(entry.target)}}),{threshold:.08,rootMargin:'0px 0px -28px 0px'});
    const observeReveal=root=>{(root.matches?.('.content-section,.catalog-subsection,.category-card,.media-card')?[root]:[]).concat([...root.querySelectorAll?.('.content-section,.catalog-subsection,.category-card,.media-card')||[]]).forEach(el=>{if(!el.classList.contains('is-revealed'))revealObserver.observe(el)})};
    observeReveal(document);
    new MutationObserver(records=>records.forEach(record=>record.addedNodes.forEach(node=>{if(node.nodeType===1)observeReveal(node)}))).observe(document.getElementById('mainContent'),{childList:true,subtree:true});
    new MutationObserver(records=>records.forEach(record=>record.addedNodes.forEach(node=>{if(node.nodeType===1)observeReveal(node)}))).observe(document.getElementById('catalogView'),{childList:true,subtree:true});
  }else document.querySelectorAll('.content-section,.catalog-subsection,.category-card,.media-card').forEach(el=>el.classList.add('is-revealed'));
  renderHomeSections();loadOnlineHome();const initial=location.hash.replace(/^#/,'')||'inicio';showView(views[initial]?initial:'inicio',false,false);
  const splash=$('splashScreen');setTimeout(()=>{splash.classList.add('is-done');setTimeout(()=>splash.remove(),650);try{if(!sessionStorage.getItem(STORE.welcomed)){toast('Opa! Bem-vindo ao CineHUB! Você sabia que tudo aqui é grátis? Então aproveite...','Boas-vindas',6000);sessionStorage.setItem(STORE.welcomed,'1')}}catch{}},1750);
})();

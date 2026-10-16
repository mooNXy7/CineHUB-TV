/* TV compatibility channel bridge. External sources are loaded by integracoes.js. */
(function(){
  const src=Array.isArray(window.catalogoCanais)?window.catalogoCanais:[];
  if(!Array.isArray(window.canaisM3U8)||!window.canaisM3U8.length){
    window.canaisM3U8=src.map(x=>({nome:x.nome||'',grupo:x.categoria||'Aberta',logo:x.logo||'',descricao:x.descricao||'',stream:x.aoVivo||'',site:x.site||'',status:x.status||''}));
  }
  window.canaisM3U8Grupos=[...new Set((window.canaisM3U8||[]).map(x=>x.grupo).filter(Boolean))];
})();

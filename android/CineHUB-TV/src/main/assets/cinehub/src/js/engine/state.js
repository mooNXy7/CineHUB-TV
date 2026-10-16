/* CineHUB TV Engine State — storage-only service, no DOM access. */
(function(){
  'use strict';
  const PREFIX='cinehub_';
  function get(key,fallback){try{const v=localStorage.getItem(PREFIX+key);return v===null?fallback:JSON.parse(v)}catch{return fallback}}
  function set(key,value){try{localStorage.setItem(PREFIX+key,JSON.stringify(value));return true}catch{return false}}
  function remove(key){try{localStorage.removeItem(PREFIX+key)}catch{}}
  function favKey(item,type){const t=type||item?.__type||item?.tipo||'movie';return t+":"+(item?.tmdb||item?.imdb||String(item?.titulo||item?.nome||'').trim().toLowerCase())}
  function favorites(){const a=get('favoritos',[]);return Array.isArray(a)?a:[]}
  function isFavorite(item,type){const k=favKey(item,type);return favorites().some(x=>x.key===k)}
  function toggleFavorite(item,type){
    const t=type||item?.__type||item?.tipo||'movie',key=favKey(item,t);let a=favorites();
    const exists=a.some(x=>x.key===key);
    if(exists)a=a.filter(x=>x.key!==key); else a=[...a,{key,titulo:item?.titulo||item?.nome||'',type:t,tmdb:item?.tmdb||'',imdb:item?.imdb||'',imagem:item?.imagem||item?.logo||'',banner:item?.banner||item?.imagem||'',ano:item?.ano||'',categoria:item?.categoria||'',sinopse:item?.sinopse||'',temporadas:item?.temporadas||''}];
    set('favoritos',a);return !exists;
  }
  function progress(){return get('progress',null)}
  function saveProgress(p){return set('progress',p)}
  function lastEpisode(){return get('last_episode',null)}
  function saveLastEpisode(v){return set('last_episode',v)}
  function stats(){return get('stats',{watched:0,lastWatched:null})}
  function bumpWatched(item){const s=stats();s.watched=(s.watched||0)+1;s.lastWatched=item?.titulo||item?.nome||null;set('stats',s);return s}
  function watchedChannels(){return get('watched_channels',{})||{}}
  function markChannel(name){const a=watchedChannels();a[name]=(a[name]||0)+1;set('watched_channels',a);return a[name]}
  function user(){return get('user',null)}
  function setUser(v){return set('user',v)}
  function clearUser(){remove('user')}
  function lastChannel(){return get('last_channel',null)}
  function saveLastChannel(v){return set('last_channel',v)}
  function lang(){try{return localStorage.getItem('cinehub_lang')||'pt'}catch{return 'pt'}}
  function setLang(v){try{localStorage.setItem('cinehub_lang',String(v||'pt'))}catch{}}
  function mode(){return window.CineHUBPerformance?.getMode?.()||'performance'}
  function setMode(v){window.CineHUBPerformance?.setMode?.(v);try{localStorage.setItem('cinehub_mode_choice_v1',String(v||'performance'))}catch{}}
  function adultSecret(){return get('parental',null)}
  async function digest(value){const bytes=new TextEncoder().encode(value);const hash=await crypto.subtle.digest('SHA-256',bytes);return [...new Uint8Array(hash)].map(x=>x.toString(16).padStart(2,'0')).join('')}
  async function derivePin(pin,salt){
    const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(pin),'PBKDF2',false,['deriveBits']);
    const bits=await crypto.subtle.deriveBits({name:'PBKDF2',salt:new TextEncoder().encode(salt),iterations:120000,hash:'SHA-256'},key,256);
    return [...new Uint8Array(bits)].map(x=>x.toString(16).padStart(2,'0')).join('');
  }
  async function setParentalPin(pin){
    if(!/^\d{4,8}$/.test(pin))throw new Error('PIN inválido');
    const salt=typeof crypto?.randomUUID==='function'?crypto.randomUUID():String(Date.now())+Math.random();
    const hash=await derivePin(pin,salt);set('parental',{enabled:true,salt,hash});return true;
  }
  function parental(){return adultSecret()||{enabled:false}}
  async function verifyParentalPin(pin){const p=parental();if(!p.enabled)return true;if(!/^\d{4,8}$/.test(pin))return false;return (await derivePin(pin,p.salt))===p.hash}
  function disableParental(){const p=parental();p.enabled=false;set('parental',p)}
  function enableParental(){const p=parental();p.enabled=true;set('parental',p)}
  window.CineHUBEngineState={get,set,remove,user,setUser,clearUser,lastChannel,saveLastChannel,favKey,favorites,isFavorite,toggleFavorite,progress,saveProgress,lastEpisode,saveLastEpisode,stats,bumpWatched,watchedChannels,markChannel,lang,setLang,mode,setMode,parental,setParentalPin,verifyParentalPin,disableParental,enableParental,digest};
})();
/* CineHUB Stable 9 — splash ambience bridge */
(function(){
  "use strict";
  window.cinehubSplashPending=true;
  document.addEventListener("DOMContentLoaded",function(){
    try{if(typeof window.cinehubPlaySplashAmbient==="function")window.cinehubPlaySplashAmbient();}catch(_){}
  },{once:true});
  window.cinehubPlaySplashAmbient=function(){
    try{if(window.cinehubSplashPending&&typeof window.sound==="function"){} }catch(_){}
  };
})();
/* CineHUB TV 1.0.9 — TV-first navigation and presentation bridge */
(function(){
'use strict';
var TV=window.CineHUBTV||{};TV.version='1.0.9';
function q(s){return document.querySelector(s)}
function qa(s){return Array.prototype.slice.call(document.querySelectorAll(s))}
function esc(s){return String(s||'').replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function ensureNav(){
 if(q('#tvMainNav'))return;
 var n=document.createElement('nav');n.id='tvMainNav';n.setAttribute('aria-label','Navegação TV');
 n.innerHTML='<div class="tv-nav-brand"><img src="assets/logos/logo-horizontal.png" alt="CineHUB"></div><button class="tv-nav-item active" data-tv-route="home">Início</button><button class="tv-nav-item" data-tv-route="movies">Filmes</button><button class="tv-nav-item" data-tv-route="series">Séries</button><button class="tv-nav-item" data-tv-route="tv">TV ao vivo</button><button class="tv-nav-item" data-tv-route="search">Buscar</button><span class="tv-nav-spacer"></span><span class="tv-nav-hint">◀ ▶ navegar&nbsp;&nbsp; OK selecionar&nbsp;&nbsp; BACK voltar</span></nav>';
 document.body.insertBefore(n,document.body.firstChild);
 qa('[data-tv-route]').forEach(function(b){b.addEventListener('click',function(){route(b.dataset.tvRoute)})});
}
function route(r){
 qa('.tv-nav-item').forEach(function(b){b.classList.toggle('active',b.dataset.tvRoute===r)});
 if(r==='home'){closeScreens();window.scrollTo(0,0);focusFirst();return}
 if(r==='movies'){q('[data-screen="movies"]')?.click();setTimeout(focusFirst,100);return}
 if(r==='series'){q('[data-screen="series"]')?.click();setTimeout(focusFirst,100);return}
 if(r==='tv'){q('[data-screen="tv"]')?.click();setTimeout(focusFirst,100);return}
 if(r==='search'){q('[data-action="search"]')?.click();setTimeout(focusFirst,100)}
}
function closeScreens(){qa('.screen.active,.detail.active,.player.active,.sheet.active').forEach(function(x){if(x.id!=='welcomeModal'){var b=x.querySelector('[data-close]');if(b)b.click();else x.classList.remove('active')}})}
function visible(x){if(!x||x.disabled||x.hidden)return false;var s=getComputedStyle(x),r=x.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&+s.opacity>0&&r.width>1&&r.height>1}
function context(x){var c=x&&x.closest('.screen,.detail,.player,.sheet,.system-modal');return c?c.classList.contains('active'):true}
function items(){return qa('button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])').filter(function(x){return visible(x)&&context(x)&&!x.closest('#welcomeModal')})}
function focus(x){if(!visible(x))return;qa('.cinehub-tv-focus').forEach(function(e){e.classList.remove('cinehub-tv-focus')});x.classList.add('cinehub-tv-focus');try{x.focus({preventScroll:true})}catch(_){try{x.focus()}catch(__){}}try{x.scrollIntoView({block:'nearest',inline:'nearest',behavior:'auto'})}catch(_){}}
function focusFirst(){var a=items();focus(a.find(function(x){return x.matches('#heroPlay,.tv-nav-item.active,.primary')})||a[0])}
function center(x){var r=x.getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2}}
function move(dir){
 var all=items(),from=document.activeElement;if(!visible(from)||!context(from))from=all[0];if(!from)return;
 var a=center(from),best=null,score=1e9;
 all.forEach(function(x){if(x===from)return;var b=center(x),dx=b.x-a.x,dy=b.y-a.y;
   var primary=(dir==='left'?-dx:dir==='right'?dx:dir==='up'?-dy:dy);if(primary<12)return;
   var cross=(dir==='left'||dir==='right')?Math.abs(dy):Math.abs(dx);
   var sameRow=cross<Math.max(45,Math.min(a.y,b.y)*0+70);
   var s=primary+(sameRow?cross*1.2.6:cross*4.5);if(s<score){score=s;best=x}
 });
 if(best)focus(best);
}
function press(){var x=document.activeElement;if(!x||!visible(x)){focusFirst();return}if(/^(INPUT|TEXTAREA|SELECT)$/.test(x.tagName))return;x.click()}
function key(e){
 var k=e.keyCode||e.which;
 if([19,20,21,22].indexOf(k)>=0){e.preventDefault();e.stopPropagation();move(k===21?'left':k===22?'right':k===19?'up':'down');return}
 if(k===23||k===66){e.preventDefault();e.stopPropagation();press();return}
 if(k===4||e.key==='Escape'){e.preventDefault();if(window.CineHUBTV&&window.CineHUBTV.back)window.CineHUBTV.back();return}
}
function back(){
 var x=q('.player.active,.detail.active,.sheet.active,.screen.active');
 if(x){var b=x.querySelector('[data-close]');if(b)b.click();else x.classList.remove('active');setTimeout(focusFirst,80);routeFromVisible();return true}
 window.scrollTo(0,0);route('home');focusFirst();return false;
}
function routeFromVisible(){
 var x=q('#tvScreen.active'),m=q('#moviesScreen.active'),s=q('#seriesScreen.active'),search=q('#searchScreen.active');
 var r=x?'tv':m?'movies':s?'series':search?'search':'home';qa('.tv-nav-item').forEach(function(b){b.classList.toggle('active',b.dataset.tvRoute===r)});
}
document.addEventListener('keydown',key,true);
document.addEventListener('focusin',function(e){if(visible(e.target))focus(e.target)});
document.addEventListener('click',function(e){var x=e.target.closest('button,a,.card,.channel,.explore-card');if(x)setTimeout(function(){if(visible(x))focus(x)},20)},true);
new MutationObserver(function(){routeFromVisible()}).observe(document.body,{subtree:true,attributes:true,attributeFilter:['class','hidden']});
document.addEventListener('DOMContentLoaded',function(){
 document.documentElement.classList.add('cinehub-performance');
 try{localStorage.setItem('cinehub_mode','performance')}catch(_){}
 ensureNav();
 var wm=q('#welcomeModal');if(wm){wm.classList.remove('active');wm.setAttribute('aria-hidden','true')}
 setTimeout(focusFirst,900);
});
TV.back=back;TV.goHome=function(){return back()};window.CineHUBTV=TV;
})();
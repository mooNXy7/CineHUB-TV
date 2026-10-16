/* CineHUB TV Focus Controller — deterministic D-pad navigation. */
(function(){
'use strict';
const KEY={LEFT:21,RIGHT:22,UP:19,DOWN:20,OK:23,ENTER:66,BACK:4,ESC:111};
const last=new Map();let currentScreen='home';
const visible=el=>{if(!el||el.disabled)return false;const s=getComputedStyle(el),r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0};
const items=()=>[...document.querySelectorAll('.tv-screen.active .tv-focusable')].filter(visible);
function remember(el){if(el?.id)last.set(currentScreen,el.id);}
function focus(el){if(!el)return false;el.focus({preventScroll:true});el.scrollIntoView({block:'nearest',inline:'nearest'});remember(el);return true;}
function first(){const list=items();return focus((last.get(currentScreen)&&document.getElementById(last.get(currentScreen)))||list[0])}
function move(dx,dy){const src=document.activeElement;if(!visible(src)){first();return true}const sr=src.getBoundingClientRect(),sx=sr.left+sr.width/2,sy=sr.top+sr.height/2;const list=items().filter(x=>x!==src);let best=null,bestScore=Infinity;for(const el of list){const r=el.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2,ax=x-sx,ay=y-sy;const primary=dx?ax*dx:ay*dy;if(primary<=4)continue;const cross=dx?Math.abs(ay):Math.abs(ax),dist=Math.hypot(ax,ay);const score=primary*1.4+cross*1.8+dist*.15;if(score<bestScore){bestScore=score;best=el}}return best?focus(best):true;}
function setScreen(name){currentScreen=name;setTimeout(first,0)}
function ok(){const el=document.activeElement;if(el&&visible(el)){el.click();return true}return false}
function back(){return !!window.CineHUBTVApp?.back?.()}
function goHome(){return !!window.CineHUBTVApp?.openScreen?.('home')}
function onKey(e){if(!document.body.classList.contains('cinehub-tv-ready'))return;const k=e.keyCode||e.which;if([KEY.LEFT,KEY.RIGHT,KEY.UP,KEY.DOWN,KEY.OK,KEY.ENTER,KEY.BACK,KEY.ESC].includes(k))e.preventDefault();switch(k){case KEY.LEFT:move(-1,0);break;case KEY.RIGHT:move(1,0);break;case KEY.UP:move(0,-1);break;case KEY.DOWN:move(0,1);break;case KEY.OK:case KEY.ENTER:ok();break;case KEY.BACK:case KEY.ESC:back();break;}}
document.addEventListener('keydown',onKey,true);
window.CineHUBTVNavigation={setScreen,focusFirst:first,focus,remember,move,back,goHome,current:()=>currentScreen};
})();
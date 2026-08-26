# NetMovies — Ortak JS-player çözücü
# Kaynak siteler (HDFilmCehennemi, rapidrame/CloseLoad ailesi) oynatma linkini
# her istekte YAPISI DEĞİŞEN bir JS obfuscation'ı (dc_* fonksiyonları) ardında
# saklıyor. Sabitleri regex'le çıkarmak kırılgan; bunun yerine sitenin KENDİ
# player script'ini bir gömülü V8 (py_mini_racer) içinde, tarayıcı global'leri
# stub'lanmış olarak çalıştırıp jwplayer(...).setup(configs) çağrısını yakalarız.
# Böylece obfuscation nasıl değişirse değişsin sonucu site kendisi üretir.

from __future__ import annotations

import re
import json

try:
    from py_mini_racer import MiniRacer
except Exception:  # motor yoksa çözücü devre dışı kalır, plugin boş döner
    MiniRacer = None

# Tarayıcı ortamı stub'ları + gerçek atob/btoa. jwplayer().setup(cfg) çağrısı
# yakalanıp __out'a yazılır; script'in ilerisinde (DOM/olay) hata çıksa bile
# setup zaten çağrıldığı için sonucu kaybetmeyiz.
_POLYFILL = r"""
var __out = null;
function atob(s){
  var chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  s=String(s).replace(/=+$/,"");var out="",bits=0,val=0;
  for(var i=0;i<s.length;i++){var c=chars.indexOf(s[i]);if(c<0)continue;val=(val<<6)|c;bits+=6;if(bits>=8){bits-=8;out+=String.fromCharCode((val>>bits)&0xFF);}}
  return out;
}
function btoa(s){
  var chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  var out="";s=String(s);for(var i=0;i<s.length;){var a=s.charCodeAt(i++),b=s.charCodeAt(i++),c=s.charCodeAt(i++);
  var n=(a<<16)|((isNaN(b)?0:b)<<8)|(isNaN(c)?0:c);out+=chars[(n>>18)&63]+chars[(n>>12)&63]+(isNaN(b)?"=":chars[(n>>6)&63])+(isNaN(c)?"=":chars[n&63]);}
  return out;
}
function __noop(){return __el();}
function __el(){return new Proxy(function(){return __el();},{get:function(t,k){if(k==="style")return {};if(k==="length")return 0;return __el();},set:function(){return true;},apply:function(){return __el();}});}
var window=this, self=this, top=this, parent=this;
var screen={width:1920,height:1080}, location={href:"",protocol:"https:",host:"",hostname:"",search:"",origin:"https://player"};
var navigator={userAgent:"Mozilla/5.0 (Linux; Android 10; K)",platform:"Linux",language:"tr"};
var localStorage=new Proxy({},{get:function(){return function(){return null;};}});
window.addEventListener=function(){};window.removeEventListener=function(){};
window.matchMedia=function(){return {matches:false,addListener:function(){},addEventListener:function(){}};};
window.setTimeout=function(){return 0;};window.setInterval=function(){return 0;};
window.requestAnimationFrame=function(){return 0;};
var document=new Proxy({cookie:"",title:"",referrer:"",readyState:"complete",addEventListener:function(){},removeEventListener:function(){},querySelector:__noop,querySelectorAll:function(){return [];},getElementById:__noop,getElementsByTagName:function(){return [];},getElementsByClassName:function(){return [];},createElement:__noop,body:__el(),documentElement:__el(),head:__el()},{get:function(t,k){if(k in t)return t[k];return __noop;}});
function jwplayer(){return {setup:function(cfg){__out=cfg;return this;},on:function(){return this;},once:function(){return this;},addButton:function(){return this;},getButton:function(){return this;},play:function(){return this;},pause:function(){return this;},setControls:function(){return this;},remove:function(){return this;},registerPlugin:function(){return this;},addPlugin:function(){return this;},setCurrentQuality:function(){return this;},getConfig:function(){return {};}};}
jwplayer.key="";jwplayer.defaults={};
function $(){return __el();}
var jQuery=$;
"""


def extract_player_config(html: str) -> dict | None:
    """Sitenin player HTML'inden {sources:[...], tracks:[...]} config'ini döndürür.

    Obfuscation'ı sitenin kendi JS'ini çalıştırarak çözer. Motor yoksa veya
    uygun script bulunamazsa None döner.
    """
    if MiniRacer is None:
        return None

    scripts = re.findall(r"<script[^>]*>(.*?)</script>", html, re.DOTALL)
    # jwplayer + sources içeren script öncelikli; yoksa sadece sources içeren
    target = next((s for s in scripts if "sources:" in s and "jwplayer" in s), None)
    if not target:
        target = next((s for s in scripts if "sources:" in s), None)
    if not target:
        return None

    ctx = MiniRacer()
    ctx.eval(_POLYFILL)
    try:
        ctx.eval(target)
    except Exception:
        # setup() çağrısı hata ANINDAN önce olabilir; __out'a yine de bakarız
        pass

    try:
        raw = ctx.eval("JSON.stringify(__out)")
    except Exception:
        raw = None
    if not raw:
        return None
    try:
        return json.loads(raw)
    except Exception:
        return None

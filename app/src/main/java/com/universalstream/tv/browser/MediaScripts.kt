package com.universalstream.tv.browser

/**
 * Small, read-only scripts run with evaluateJavascript (no JavaScript interface is exposed to pages).
 * They only reach media elements in the top-level document; players inside cross-origin iframes
 * get the remote key forwarded instead (see BrowserController.dispatchFallbackKey).
 */
internal object MediaScripts {

    /** Returns "PU": P=1 if something is playing, U=1 if a media element reported an unsupported source. */
    const val STATUS = """(function(){try{var m=document.querySelectorAll('video,audio');var p=0,u=0;
for(var i=0;i<m.length;i++){var e=m[i];if(!e.paused&&!e.ended&&e.readyState>2)p=1;if(e.error&&e.error.code===4)u=1;}
return ''+p+u;}catch(x){return '00';}})()"""

    const val PAUSE_ALL = """(function(){try{document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();}catch(e){}});}catch(x){}return 'ok';})()"""

    /** action: toggle | play | pause | seek. Returns 'ok', 'none' (no reachable video) or 'error'. */
    fun command(action: String, seekSeconds: Int): String = """(function(a,d){try{
var vs=[].slice.call(document.querySelectorAll('video'));
var f=document.fullscreenElement||document.webkitFullscreenElement;
if(f){if(f.tagName==='VIDEO'){vs.unshift(f);}else if(f.querySelector){var inner=f.querySelector('video');if(inner)vs.unshift(inner);}}
var v=null,i;
for(i=0;i<vs.length;i++){if(!vs[i].paused&&!vs[i].ended){v=vs[i];break;}}
if(!v){var best=0;for(i=0;i<vs.length;i++){var r=vs[i].getBoundingClientRect();var s=r.width*r.height;if(s>best){best=s;v=vs[i];}}}
if(!v)return 'none';
var play=function(){var pr=v.play();if(pr&&pr.catch)pr.catch(function(){});};
if(a==='toggle'){if(v.paused)play();else v.pause();}
else if(a==='play'){play();}
else if(a==='pause'){v.pause();}
else if(a==='seek'){var t=v.currentTime+d;if(isFinite(v.duration))t=Math.min(v.duration,t);v.currentTime=Math.max(0,t);}
return 'ok';}catch(e){return 'error';}})('$action',$seekSeconds)"""

    private val UNSUPPORTED_MARKERS = listOf(
        "MEDIA_ERR_SRC_NOT_SUPPORTED",
        "no supported source",
        "NotSupportedError",
        "DEMUXER_ERROR",
        "PIPELINE_ERROR_DECODE",
        "DECODER_ERROR_NOT_SUPPORTED",
    )

    fun isUnsupportedMediaMessage(message: String): Boolean =
        UNSUPPORTED_MARKERS.any { message.contains(it, ignoreCase = true) }
}

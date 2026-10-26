<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>React &#038; Spring Boot | Pauls Blog</title>
<link rel="profile" href="http://gmpg.org/xfn/11">
<link rel="pingback" href="https://sterl.org/xmlrpc.php">

<meta name='robots' content='max-image-preview:large' />
	<style>img:is([sizes="auto" i], [sizes^="auto," i]) { contain-intrinsic-size: 3000px 1500px }</style>
	<link rel='dns-prefetch' href='//fonts.googleapis.com' />
<link rel="alternate" type="application/rss+xml" title="Pauls Blog &raquo; Feed" href="https://sterl.org/feed/" />
<link rel="alternate" type="application/rss+xml" title="Pauls Blog &raquo; Kommentar-Feed" href="https://sterl.org/comments/feed/" />
<link rel="alternate" type="application/rss+xml" title="Pauls Blog &raquo; React &#038; Spring Boot-Kommentar-Feed" href="https://sterl.org/2024/12/react-spring-boot/feed/" />
<script type="text/javascript">
/* <![CDATA[ */
window._wpemojiSettings = {"baseUrl":"https:\/\/s.w.org\/images\/core\/emoji\/16.0.1\/72x72\/","ext":".png","svgUrl":"https:\/\/s.w.org\/images\/core\/emoji\/16.0.1\/svg\/","svgExt":".svg","source":{"concatemoji":"https:\/\/sterl.org\/wp-includes\/js\/wp-emoji-release.min.js?ver=6.8.3"}};
/*! This file is auto-generated */
!function(s,n){var o,i,e;function c(e){try{var t={supportTests:e,timestamp:(new Date).valueOf()};sessionStorage.setItem(o,JSON.stringify(t))}catch(e){}}function p(e,t,n){e.clearRect(0,0,e.canvas.width,e.canvas.height),e.fillText(t,0,0);var t=new Uint32Array(e.getImageData(0,0,e.canvas.width,e.canvas.height).data),a=(e.clearRect(0,0,e.canvas.width,e.canvas.height),e.fillText(n,0,0),new Uint32Array(e.getImageData(0,0,e.canvas.width,e.canvas.height).data));return t.every(function(e,t){return e===a[t]})}function u(e,t){e.clearRect(0,0,e.canvas.width,e.canvas.height),e.fillText(t,0,0);for(var n=e.getImageData(16,16,1,1),a=0;a<n.data.length;a++)if(0!==n.data[a])return!1;return!0}function f(e,t,n,a){switch(t){case"flag":return n(e,"\ud83c\udff3\ufe0f\u200d\u26a7\ufe0f","\ud83c\udff3\ufe0f\u200b\u26a7\ufe0f")?!1:!n(e,"\ud83c\udde8\ud83c\uddf6","\ud83c\udde8\u200b\ud83c\uddf6")&&!n(e,"\ud83c\udff4\udb40\udc67\udb40\udc62\udb40\udc65\udb40\udc6e\udb40\udc67\udb40\udc7f","\ud83c\udff4\u200b\udb40\udc67\u200b\udb40\udc62\u200b\udb40\udc65\u200b\udb40\udc6e\u200b\udb40\udc67\u200b\udb40\udc7f");case"emoji":return!a(e,"\ud83e\udedf")}return!1}function g(e,t,n,a){var r="undefined"!=typeof WorkerGlobalScope&&self instanceof WorkerGlobalScope?new OffscreenCanvas(300,150):s.createElement("canvas"),o=r.getContext("2d",{willReadFrequently:!0}),i=(o.textBaseline="top",o.font="600 32px Arial",{});return e.forEach(function(e){i[e]=t(o,e,n,a)}),i}function t(e){var t=s.createElement("script");t.src=e,t.defer=!0,s.head.appendChild(t)}"undefined"!=typeof Promise&&(o="wpEmojiSettingsSupports",i=["flag","emoji"],n.supports={everything:!0,everythingExceptFlag:!0},e=new Promise(function(e){s.addEventListener("DOMContentLoaded",e,{once:!0})}),new Promise(function(t){var n=function(){try{var e=JSON.parse(sessionStorage.getItem(o));if("object"==typeof e&&"number"==typeof e.timestamp&&(new Date).valueOf()<e.timestamp+604800&&"object"==typeof e.supportTests)return e.supportTests}catch(e){}return null}();if(!n){if("undefined"!=typeof Worker&&"undefined"!=typeof OffscreenCanvas&&"undefined"!=typeof URL&&URL.createObjectURL&&"undefined"!=typeof Blob)try{var e="postMessage("+g.toString()+"("+[JSON.stringify(i),f.toString(),p.toString(),u.toString()].join(",")+"));",a=new Blob([e],{type:"text/javascript"}),r=new Worker(URL.createObjectURL(a),{name:"wpTestEmojiSupports"});return void(r.onmessage=function(e){c(n=e.data),r.terminate(),t(n)})}catch(e){}c(n=g(i,f,p,u))}t(n)}).then(function(e){for(var t in e)n.supports[t]=e[t],n.supports.everything=n.supports.everything&&n.supports[t],"flag"!==t&&(n.supports.everythingExceptFlag=n.supports.everythingExceptFlag&&n.supports[t]);n.supports.everythingExceptFlag=n.supports.everythingExceptFlag&&!n.supports.flag,n.DOMReady=!1,n.readyCallback=function(){n.DOMReady=!0}}).then(function(){return e}).then(function(){var e;n.supports.everything||(n.readyCallback(),(e=n.source||{}).concatemoji?t(e.concatemoji):e.wpemoji&&e.twemoji&&(t(e.twemoji),t(e.wpemoji)))}))}((window,document),window._wpemojiSettings);
/* ]]> */
</script>
<style id='wp-emoji-styles-inline-css' type='text/css'>

	img.wp-smiley, img.emoji {
		display: inline !important;
		border: none !important;
		box-shadow: none !important;
		height: 1em !important;
		width: 1em !important;
		margin: 0 0.07em !important;
		vertical-align: -0.1em !important;
		background: none !important;
		padding: 0 !important;
	}
</style>
<!-- <link rel='stylesheet' id='wp-block-library-css' href='https://sterl.org/wp-includes/css/dist/block-library/style.min.css?ver=6.8.3' type='text/css' media='all' /> -->
<link rel="stylesheet" type="text/css" href="//sterl.org/wp-content/cache/wpfc-minified/6ongxf0c/4igjh.css" media="all"/>
<style id='classic-theme-styles-inline-css' type='text/css'>
/*! This file is auto-generated */
.wp-block-button__link{color:#fff;background-color:#32373c;border-radius:9999px;box-shadow:none;text-decoration:none;padding:calc(.667em + 2px) calc(1.333em + 2px);font-size:1.125em}.wp-block-file__button{background:#32373c;color:#fff;text-decoration:none}
</style>
<style id='codemirror-blocks-code-block-style-inline-css' type='text/css'>
.code-block{margin-bottom:1.5rem;position:relative}.code-block pre.CodeMirror{background:#f2f2f2;background:linear-gradient(180deg,#f2f2f2 50%,#f9f9f9 0);background-size:56px 56px;border:1px solid #ddd;filter:progid:DXImageTransform.Microsoft.gradient(startColorstr="#f2f2f2",endColorstr="#f9f9f9",GradientType=0);line-height:28px;margin:0 0 10px;padding:0 10px;white-space:pre-wrap;white-space:-moz-pre-wrap;white-space:-pre-wrap;white-space:-o-pre-wrap;word-wrap:break-word;font-family:monospace;font-size:16px}.code-block .CodeMirror-fullscreen{bottom:0;height:auto;left:0;margin-bottom:0;padding-bottom:30px;position:fixed;right:0;top:0;z-index:5000}.code-block iframe.output-block-frame{background-color:#fff;box-shadow:0 0 10px 0 #444;display:none;margin:1em 0;transition:all .3s ease-in;width:100%}.code-block iframe.show{display:block}textarea.CodeMirror-ClipBoard{border:0;bottom:0;height:1px;opacity:0;padding:0;position:absolute;right:0;width:1px}.CodeMirror-notice{background:#000;border-radius:5px;bottom:-40px;color:#fff;font-size:12px;left:15px;padding:10px;position:fixed;transition:all .5s;z-index:5001}.CodeMirror svg.loader{background:rgba(30,30,30,.7);border-radius:3px;left:42%;max-width:64px;position:absolute;top:42%;fill:none;stroke:#222;stroke-linecap:round;stroke-width:8%}.CodeMirror use{stroke:#fff;animation:a 2s linear infinite}@keyframes a{to{stroke-dashoffset:0px}}.CodeMirror.has-panel{border-top-left-radius:0!important;border-top-right-radius:0!important}.CodeMirror-panel .max-height{margin-right:.5rem}.CodeMirror-panel .info-panel{display:flex;height:32px;justify-content:space-between;transition:all .4s ease-in}.CodeMirror-panel .info-panel .language{background:#4c607f;color:#fff;display:inline-block;font-size:16px;font-weight:500;padding:4px 32px;transition:"background" .4s ease-out}.CodeMirror-panel .info-panel .language.apl{background:#d2d2d2;color:#000}.CodeMirror-panel .info-panel .language.asp{background:#0088b6}.CodeMirror-panel .info-panel .language.aspx{background:#33a9dc}.CodeMirror-panel .info-panel .language.c{background:#005f91}.CodeMirror-panel .info-panel .language.cpp{background:#984c93}.CodeMirror-panel .info-panel .language.cobol{background:#005ca5}.CodeMirror-panel .info-panel .language.coffeescript{background:#6f4e37}.CodeMirror-panel .info-panel .language.clojure{background:#5881d8}.CodeMirror-panel .info-panel .language.css{background:#1572b6}.CodeMirror-panel .info-panel .language.csharp{background:#368832}.CodeMirror-panel .info-panel .language.diff{background:green}.CodeMirror-panel .info-panel .language.django{background:#44b78b}.CodeMirror-panel .info-panel .language.docker{background:#27b9ec}.CodeMirror-panel .info-panel .language.dylan{background-image:linear-gradient(to bottom right,#fd1a60,#f36420)}.CodeMirror-panel .info-panel .language.elm{background:#5fb4cb}.CodeMirror-panel .info-panel .language.erlang{background:#a2003e}.CodeMirror-panel .info-panel .language.fortran{background:#30b9db}.CodeMirror-panel .info-panel .language.fsharp{background:#665948}.CodeMirror-panel .info-panel .language.git{background:#fca326}.CodeMirror-panel .info-panel .language.go{background:#00acd7}.CodeMirror-panel .info-panel .language.groovy{background:#6398aa}.CodeMirror-panel .info-panel .language.haml{background:#ecdfa7;color:#3f3e29}.CodeMirror-panel .info-panel .language.haskell{background:#8f4e8b}.CodeMirror-panel .info-panel .language.html,.CodeMirror-panel .info-panel .language.htmlembedded,.CodeMirror-panel .info-panel .language.htmlmixed{background:#f1662a}.CodeMirror-panel .info-panel .language.http{background:#509e2f}.CodeMirror-panel .info-panel .language.java{background:#5382a1}.CodeMirror-panel .info-panel .language.js,.CodeMirror-panel .info-panel .language.json,.CodeMirror-panel .info-panel .language.jsx{background:#f5de19}.CodeMirror-panel .info-panel .language.jsonld{background:#0ca69c}.CodeMirror-panel .info-panel .language.jsp{background:#e56f14}.CodeMirror-panel .info-panel .language.kotlin{background-image:linear-gradient(to bottom right,#cb55c0,#f28e0e)}.CodeMirror-panel .info-panel .language.less{background:#193255}.CodeMirror-panel .info-panel .language.lisp{background:#c40804}.CodeMirror-panel .info-panel .language.livescript{background:#317eac}.CodeMirror-panel .info-panel .language.lua{background:navy}.CodeMirror-panel .info-panel .language.markdown{background:#755838}.CodeMirror-panel .info-panel .language.mariadb{background:#c49a6c;color:#002b64}.CodeMirror-panel .info-panel .language.mssql,.CodeMirror-panel .info-panel .language.mysql{background:#5d87a1}.CodeMirror-panel .info-panel .language.nginx{background:#019639}.CodeMirror-panel .info-panel .language.objectivec{background:#c2c2c2}.CodeMirror-panel .info-panel .language.perl{background:#3a3c5b}.CodeMirror-panel .info-panel .language.php{background:#6280b6}.CodeMirror-panel .info-panel .language.plsql{background:red}.CodeMirror-panel .info-panel .language.powershell{background:#5290fd}.CodeMirror-panel .info-panel .language.protobuf{background:#ff5c77}.CodeMirror-panel .info-panel .language.pug{background:#efcca3;color:#442823}.CodeMirror-panel .info-panel .language.puppet{background:#ffae1a}.CodeMirror-panel .info-panel .language.python{background:#ffdc4e;color:#366e9d}.CodeMirror-panel .info-panel .language.q{background:#1e78b3}.CodeMirror-panel .info-panel .language.r{background:#acadb2;color:#185ead}.CodeMirror-panel .info-panel .language.ruby{background:#e71622}.CodeMirror-panel .info-panel .language.rust{background:#a04f12}.CodeMirror-panel .info-panel .language.scala{background:#fe0000}.CodeMirror-panel .info-panel .language.stylus{background:#ff2952}.CodeMirror-panel .info-panel .language.sass,.CodeMirror-panel .info-panel .language.scss{background:#cd6799}.CodeMirror-panel .info-panel .language.shell{background:#d9b400}.CodeMirror-panel .info-panel .language.sql{background:#ffda44}.CodeMirror-panel .info-panel .language.sqlite{background:#003b57}.CodeMirror-panel .info-panel .language.squirrel{background:#a05a2c}.CodeMirror-panel .info-panel .language.stylus{background:#c2c2c2}.CodeMirror-panel .info-panel .language.swift{background:#fd2822}.CodeMirror-panel .info-panel .language.tcl{background:#c3b15f}.CodeMirror-panel .info-panel .language.text{background:#829ec2}.CodeMirror-panel .info-panel .language.textile{background:#ffe7ac;color:#000}.CodeMirror-panel .info-panel .language.toml{background:#7f7f7f}.CodeMirror-panel .info-panel .language.ttcn{background:#00adee}.CodeMirror-panel .info-panel .language.twig{background:#78dc50}.CodeMirror-panel .info-panel .language.typescript{background:#007acc}.CodeMirror-panel .info-panel .language.vb{background:#00519a}.CodeMirror-panel .info-panel .language.velocity{background:#262692}.CodeMirror-panel .info-panel .language.verilog{background:#1a348f}.CodeMirror-panel .info-panel .language.vhdl{background:#0d9b35}.CodeMirror-panel .info-panel .language.vue{background:#41b883}.CodeMirror-panel .info-panel .language.xml,.CodeMirror-panel .info-panel .language.xquery{background:#f1662a}.CodeMirror-panel .info-panel .language.yaml{background:#fbc02d}.CodeMirror-panel .control-panel{display:flex;padding:5px 3px}.CodeMirror-panel .control-panel>span{cursor:pointer;display:flex;margin:0 5px 0 0;transition:all .3s}.CodeMirror-panel .control-panel>span:hover{color:#fff}.CodeMirror-panel .control-panel .run-code{border:2px solid;border-radius:20px;box-sizing:border-box;display:inline-block;height:20px;position:relative;transform:scale(1);width:20px}.CodeMirror-panel .control-panel .run-code:after,.CodeMirror-panel .control-panel .run-code:before{box-sizing:border-box;content:"";display:block;position:absolute;top:3px}.CodeMirror-panel .control-panel .run-code:after{border-bottom:5px solid transparent;border-left:9px solid;border-top:5px solid transparent;left:5px;width:0}.CodeMirror-panel .control-panel .maximize,.CodeMirror-panel .control-panel .restore{border:2px solid;border-radius:2px;box-sizing:border-box;display:inline-block;height:20px;position:relative;transform:scale(1);width:20px}.CodeMirror-panel .control-panel .maximize:after{height:12px;top:2px;width:12px}.CodeMirror-panel .control-panel .maximize:after,.CodeMirror-panel .control-panel .restore:after{background:currentColor;box-sizing:border-box;content:"";display:inline-block;left:2px;position:absolute}.CodeMirror-panel .control-panel .restore:after{bottom:3px;height:3px;width:9px}.CodeMirror-panel .control-panel .copy{border:2px solid;border-bottom:0;border-radius:2px;border-right:0;box-sizing:border-box;display:inline-block;height:18px;position:relative;transform:scale(1);width:14px}.CodeMirror-panel .control-panel .copy:after{border:2px solid;border-radius:0 0 2px 0;box-sizing:border-box;content:"";display:inline-block;height:17px;left:1px;position:absolute;top:1px;width:13px}.CodeMirror-panel .control-panel .tool{cursor:help;position:relative}.CodeMirror-panel .control-panel .tool:after,.CodeMirror-panel .control-panel .tool:before{left:50%;opacity:0;position:absolute;z-index:-100}.CodeMirror-panel .control-panel .tool:focus:after,.CodeMirror-panel .control-panel .tool:focus:before,.CodeMirror-panel .control-panel .tool:hover:after,.CodeMirror-panel .control-panel .tool:hover:before{opacity:1;transform:scale(1) translateY(0);z-index:100}.CodeMirror-panel .control-panel .tool:before{border-color:transparent transparent #3e474f;border-style:solid;border-width:8px 12px 12px 8px;content:"";left:0;top:100%;transform:scale(.6) translateY(-20%);transition:all .85s cubic-bezier(.84,-.18,.31,1.26),opacity .85s .5s}.CodeMirror-panel .control-panel .tool:after{background:#3e474f;border-radius:5px;color:#edeff0;content:attr(data-tip);font-size:14px;left:-90px;padding:5px;text-align:center;top:180%;transition:all .85s cubic-bezier(.84,-.18,.31,1.26) .2s;width:120px}

</style>
<style id='global-styles-inline-css' type='text/css'>
:root{--wp--preset--aspect-ratio--square: 1;--wp--preset--aspect-ratio--4-3: 4/3;--wp--preset--aspect-ratio--3-4: 3/4;--wp--preset--aspect-ratio--3-2: 3/2;--wp--preset--aspect-ratio--2-3: 2/3;--wp--preset--aspect-ratio--16-9: 16/9;--wp--preset--aspect-ratio--9-16: 9/16;--wp--preset--color--black: #000000;--wp--preset--color--cyan-bluish-gray: #abb8c3;--wp--preset--color--white: #ffffff;--wp--preset--color--pale-pink: #f78da7;--wp--preset--color--vivid-red: #cf2e2e;--wp--preset--color--luminous-vivid-orange: #ff6900;--wp--preset--color--luminous-vivid-amber: #fcb900;--wp--preset--color--light-green-cyan: #7bdcb5;--wp--preset--color--vivid-green-cyan: #00d084;--wp--preset--color--pale-cyan-blue: #8ed1fc;--wp--preset--color--vivid-cyan-blue: #0693e3;--wp--preset--color--vivid-purple: #9b51e0;--wp--preset--gradient--vivid-cyan-blue-to-vivid-purple: linear-gradient(135deg,rgba(6,147,227,1) 0%,rgb(155,81,224) 100%);--wp--preset--gradient--light-green-cyan-to-vivid-green-cyan: linear-gradient(135deg,rgb(122,220,180) 0%,rgb(0,208,130) 100%);--wp--preset--gradient--luminous-vivid-amber-to-luminous-vivid-orange: linear-gradient(135deg,rgba(252,185,0,1) 0%,rgba(255,105,0,1) 100%);--wp--preset--gradient--luminous-vivid-orange-to-vivid-red: linear-gradient(135deg,rgba(255,105,0,1) 0%,rgb(207,46,46) 100%);--wp--preset--gradient--very-light-gray-to-cyan-bluish-gray: linear-gradient(135deg,rgb(238,238,238) 0%,rgb(169,184,195) 100%);--wp--preset--gradient--cool-to-warm-spectrum: linear-gradient(135deg,rgb(74,234,220) 0%,rgb(151,120,209) 20%,rgb(207,42,186) 40%,rgb(238,44,130) 60%,rgb(251,105,98) 80%,rgb(254,248,76) 100%);--wp--preset--gradient--blush-light-purple: linear-gradient(135deg,rgb(255,206,236) 0%,rgb(152,150,240) 100%);--wp--preset--gradient--blush-bordeaux: linear-gradient(135deg,rgb(254,205,165) 0%,rgb(254,45,45) 50%,rgb(107,0,62) 100%);--wp--preset--gradient--luminous-dusk: linear-gradient(135deg,rgb(255,203,112) 0%,rgb(199,81,192) 50%,rgb(65,88,208) 100%);--wp--preset--gradient--pale-ocean: linear-gradient(135deg,rgb(255,245,203) 0%,rgb(182,227,212) 50%,rgb(51,167,181) 100%);--wp--preset--gradient--electric-grass: linear-gradient(135deg,rgb(202,248,128) 0%,rgb(113,206,126) 100%);--wp--preset--gradient--midnight: linear-gradient(135deg,rgb(2,3,129) 0%,rgb(40,116,252) 100%);--wp--preset--font-size--small: 13px;--wp--preset--font-size--medium: 20px;--wp--preset--font-size--large: 36px;--wp--preset--font-size--x-large: 42px;--wp--preset--spacing--20: 0.44rem;--wp--preset--spacing--30: 0.67rem;--wp--preset--spacing--40: 1rem;--wp--preset--spacing--50: 1.5rem;--wp--preset--spacing--60: 2.25rem;--wp--preset--spacing--70: 3.38rem;--wp--preset--spacing--80: 5.06rem;--wp--preset--shadow--natural: 6px 6px 9px rgba(0, 0, 0, 0.2);--wp--preset--shadow--deep: 12px 12px 50px rgba(0, 0, 0, 0.4);--wp--preset--shadow--sharp: 6px 6px 0px rgba(0, 0, 0, 0.2);--wp--preset--shadow--outlined: 6px 6px 0px -3px rgba(255, 255, 255, 1), 6px 6px rgba(0, 0, 0, 1);--wp--preset--shadow--crisp: 6px 6px 0px rgba(0, 0, 0, 1);}:where(.is-layout-flex){gap: 0.5em;}:where(.is-layout-grid){gap: 0.5em;}body .is-layout-flex{display: flex;}.is-layout-flex{flex-wrap: wrap;align-items: center;}.is-layout-flex > :is(*, div){margin: 0;}body .is-layout-grid{display: grid;}.is-layout-grid > :is(*, div){margin: 0;}:where(.wp-block-columns.is-layout-flex){gap: 2em;}:where(.wp-block-columns.is-layout-grid){gap: 2em;}:where(.wp-block-post-template.is-layout-flex){gap: 1.25em;}:where(.wp-block-post-template.is-layout-grid){gap: 1.25em;}.has-black-color{color: var(--wp--preset--color--black) !important;}.has-cyan-bluish-gray-color{color: var(--wp--preset--color--cyan-bluish-gray) !important;}.has-white-color{color: var(--wp--preset--color--white) !important;}.has-pale-pink-color{color: var(--wp--preset--color--pale-pink) !important;}.has-vivid-red-color{color: var(--wp--preset--color--vivid-red) !important;}.has-luminous-vivid-orange-color{color: var(--wp--preset--color--luminous-vivid-orange) !important;}.has-luminous-vivid-amber-color{color: var(--wp--preset--color--luminous-vivid-amber) !important;}.has-light-green-cyan-color{color: var(--wp--preset--color--light-green-cyan) !important;}.has-vivid-green-cyan-color{color: var(--wp--preset--color--vivid-green-cyan) !important;}.has-pale-cyan-blue-color{color: var(--wp--preset--color--pale-cyan-blue) !important;}.has-vivid-cyan-blue-color{color: var(--wp--preset--color--vivid-cyan-blue) !important;}.has-vivid-purple-color{color: var(--wp--preset--color--vivid-purple) !important;}.has-black-background-color{background-color: var(--wp--preset--color--black) !important;}.has-cyan-bluish-gray-background-color{background-color: var(--wp--preset--color--cyan-bluish-gray) !important;}.has-white-background-color{background-color: var(--wp--preset--color--white) !important;}.has-pale-pink-background-color{background-color: var(--wp--preset--color--pale-pink) !important;}.has-vivid-red-background-color{background-color: var(--wp--preset--color--vivid-red) !important;}.has-luminous-vivid-orange-background-color{background-color: var(--wp--preset--color--luminous-vivid-orange) !important;}.has-luminous-vivid-amber-background-color{background-color: var(--wp--preset--color--luminous-vivid-amber) !important;}.has-light-green-cyan-background-color{background-color: var(--wp--preset--color--light-green-cyan) !important;}.has-vivid-green-cyan-background-color{background-color: var(--wp--preset--color--vivid-green-cyan) !important;}.has-pale-cyan-blue-background-color{background-color: var(--wp--preset--color--pale-cyan-blue) !important;}.has-vivid-cyan-blue-background-color{background-color: var(--wp--preset--color--vivid-cyan-blue) !important;}.has-vivid-purple-background-color{background-color: var(--wp--preset--color--vivid-purple) !important;}.has-black-border-color{border-color: var(--wp--preset--color--black) !important;}.has-cyan-bluish-gray-border-color{border-color: var(--wp--preset--color--cyan-bluish-gray) !important;}.has-white-border-color{border-color: var(--wp--preset--color--white) !important;}.has-pale-pink-border-color{border-color: var(--wp--preset--color--pale-pink) !important;}.has-vivid-red-border-color{border-color: var(--wp--preset--color--vivid-red) !important;}.has-luminous-vivid-orange-border-color{border-color: var(--wp--preset--color--luminous-vivid-orange) !important;}.has-luminous-vivid-amber-border-color{border-color: var(--wp--preset--color--luminous-vivid-amber) !important;}.has-light-green-cyan-border-color{border-color: var(--wp--preset--color--light-green-cyan) !important;}.has-vivid-green-cyan-border-color{border-color: var(--wp--preset--color--vivid-green-cyan) !important;}.has-pale-cyan-blue-border-color{border-color: var(--wp--preset--color--pale-cyan-blue) !important;}.has-vivid-cyan-blue-border-color{border-color: var(--wp--preset--color--vivid-cyan-blue) !important;}.has-vivid-purple-border-color{border-color: var(--wp--preset--color--vivid-purple) !important;}.has-vivid-cyan-blue-to-vivid-purple-gradient-background{background: var(--wp--preset--gradient--vivid-cyan-blue-to-vivid-purple) !important;}.has-light-green-cyan-to-vivid-green-cyan-gradient-background{background: var(--wp--preset--gradient--light-green-cyan-to-vivid-green-cyan) !important;}.has-luminous-vivid-amber-to-luminous-vivid-orange-gradient-background{background: var(--wp--preset--gradient--luminous-vivid-amber-to-luminous-vivid-orange) !important;}.has-luminous-vivid-orange-to-vivid-red-gradient-background{background: var(--wp--preset--gradient--luminous-vivid-orange-to-vivid-red) !important;}.has-very-light-gray-to-cyan-bluish-gray-gradient-background{background: var(--wp--preset--gradient--very-light-gray-to-cyan-bluish-gray) !important;}.has-cool-to-warm-spectrum-gradient-background{background: var(--wp--preset--gradient--cool-to-warm-spectrum) !important;}.has-blush-light-purple-gradient-background{background: var(--wp--preset--gradient--blush-light-purple) !important;}.has-blush-bordeaux-gradient-background{background: var(--wp--preset--gradient--blush-bordeaux) !important;}.has-luminous-dusk-gradient-background{background: var(--wp--preset--gradient--luminous-dusk) !important;}.has-pale-ocean-gradient-background{background: var(--wp--preset--gradient--pale-ocean) !important;}.has-electric-grass-gradient-background{background: var(--wp--preset--gradient--electric-grass) !important;}.has-midnight-gradient-background{background: var(--wp--preset--gradient--midnight) !important;}.has-small-font-size{font-size: var(--wp--preset--font-size--small) !important;}.has-medium-font-size{font-size: var(--wp--preset--font-size--medium) !important;}.has-large-font-size{font-size: var(--wp--preset--font-size--large) !important;}.has-x-large-font-size{font-size: var(--wp--preset--font-size--x-large) !important;}
:where(.wp-block-post-template.is-layout-flex){gap: 1.25em;}:where(.wp-block-post-template.is-layout-grid){gap: 1.25em;}
:where(.wp-block-columns.is-layout-flex){gap: 2em;}:where(.wp-block-columns.is-layout-grid){gap: 2em;}
:root :where(.wp-block-pullquote){font-size: 1.5em;line-height: 1.6;}
</style>
<!-- <link rel='stylesheet' id='cptch_stylesheet-css' href='https://sterl.org/wp-content/plugins/captcha/css/front_end_style.css?ver=4.3.0' type='text/css' media='all' /> -->
<!-- <link rel='stylesheet' id='dashicons-css' href='https://sterl.org/wp-includes/css/dashicons.min.css?ver=6.8.3' type='text/css' media='all' /> -->
<!-- <link rel='stylesheet' id='cptch_mobile_style-css' href='https://sterl.org/wp-content/plugins/captcha/css/mobile_style.css?ver=4.3.0' type='text/css' media='all' /> -->
<!-- <link rel='stylesheet' id='codemirror-css' href='https://sterl.org/wp-content/plugins/wp-codemirror-block/vendor/codemirror/lib/codemirror.min.css?ver=5.40.5' type='text/css' media='all' /> -->
<link rel="stylesheet" type="text/css" href="//sterl.org/wp-content/cache/wpfc-minified/21qpemn1/67blp.css" media="all"/>
<link rel='stylesheet' id='ipt_kb-fonts-css' href='//fonts.googleapis.com/css?family=Oswald%7CRoboto%3A400%2C400italic%2C700%2C700italic&#038;ver=1.6.0' type='text/css' media='all' />
<!-- <link rel='stylesheet' id='ipt_kb-style-css' href='https://sterl.org/wp-content/themes/wp-knowledge-base/style.css?ver=1.6.0' type='text/css' media='all' /> -->
<!-- <link rel='stylesheet' id='ipt_kb-bootstrap-css' href='https://sterl.org/wp-content/themes/wp-knowledge-base/lib/bootstrap/css/bootstrap.min.css?ver=1.6.0' type='text/css' media='all' /> -->
<!-- <link rel='stylesheet' id='ipt_kb-bootstrap-theme-css' href='https://sterl.org/wp-content/themes/wp-knowledge-base/lib/bootstrap/css/bootstrap-theme.min.css?ver=1.6.0' type='text/css' media='all' /> -->
<!-- <link rel='stylesheet' id='ipt_kb-icomoon-css' href='https://sterl.org/wp-content/themes/wp-knowledge-base/lib/icomoon/style.css?ver=1.6.0' type='text/css' media='all' /> -->
<link rel="stylesheet" type="text/css" href="//sterl.org/wp-content/cache/wpfc-minified/knw1ce98/4igjh.css" media="all"/>
<style id='akismet-widget-style-inline-css' type='text/css'>

			.a-stats {
				--akismet-color-mid-green: #357b49;
				--akismet-color-white: #fff;
				--akismet-color-light-grey: #f6f7f7;

				max-width: 350px;
				width: auto;
			}

			.a-stats * {
				all: unset;
				box-sizing: border-box;
			}

			.a-stats strong {
				font-weight: 600;
			}

			.a-stats a.a-stats__link,
			.a-stats a.a-stats__link:visited,
			.a-stats a.a-stats__link:active {
				background: var(--akismet-color-mid-green);
				border: none;
				box-shadow: none;
				border-radius: 8px;
				color: var(--akismet-color-white);
				cursor: pointer;
				display: block;
				font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen-Sans', 'Ubuntu', 'Cantarell', 'Helvetica Neue', sans-serif;
				font-weight: 500;
				padding: 12px;
				text-align: center;
				text-decoration: none;
				transition: all 0.2s ease;
			}

			/* Extra specificity to deal with TwentyTwentyOne focus style */
			.widget .a-stats a.a-stats__link:focus {
				background: var(--akismet-color-mid-green);
				color: var(--akismet-color-white);
				text-decoration: none;
			}

			.a-stats a.a-stats__link:hover {
				filter: brightness(110%);
				box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06), 0 0 2px rgba(0, 0, 0, 0.16);
			}

			.a-stats .count {
				color: var(--akismet-color-white);
				display: block;
				font-size: 1.5em;
				line-height: 1.4;
				padding: 0 13px;
				white-space: nowrap;
			}
		
</style>
<script src='//sterl.org/wp-content/cache/wpfc-minified/f5tb8z6i/4igjh.js' type="text/javascript"></script>
<!-- <script type="text/javascript" src="https://sterl.org/wp-includes/js/jquery/jquery.min.js?ver=3.7.1" id="jquery-core-js"></script> -->
<!-- <script type="text/javascript" src="https://sterl.org/wp-includes/js/jquery/jquery-migrate.min.js?ver=3.4.1" id="jquery-migrate-js"></script> -->
<!-- <script type="text/javascript" src="https://sterl.org/wp-content/themes/wp-knowledge-base/lib/bootstrap/js/bootstrap.min.js?ver=1.6.0" id="ipt_kb-bootstrap-js"></script> -->
<!-- <script type="text/javascript" src="https://sterl.org/wp-content/themes/wp-knowledge-base/lib/bootstrap/js/jquery.ipt-kb-bootstrap.js?ver=1.6.0" id="ipt_kb-bootstrap-jq-js"></script> -->
<!-- <script type="text/javascript" src="https://sterl.org/wp-content/themes/wp-knowledge-base/lib/sticky-kit/jquery.sticky-kit.min.js?ver=1.6.0" id="ipt-kb-sticky-kit-js"></script> -->
<script type="text/javascript" id="ipt-kb-js-js-extra">
/* <![CDATA[ */
var iptKBJS = {"ajaxurl":"https:\/\/sterl.org\/wp-admin\/admin-ajax.php","ajax_error":"Oops, some problem to connect. Try again?"};
/* ]]> */
</script>
<script src='//sterl.org/wp-content/cache/wpfc-minified/g5f5yhs/4igjh.js' type="text/javascript"></script>
<!-- <script type="text/javascript" src="https://sterl.org/wp-content/themes/wp-knowledge-base/js/ipt-kb.js?ver=1.6.0" id="ipt-kb-js-js"></script> -->
<link rel="EditURI" type="application/rsd+xml" title="RSD" href="https://sterl.org/xmlrpc.php?rsd" />
<meta name="generator" content="WordPress 6.8.3" />
<link rel="canonical" href="https://sterl.org/2024/12/react-spring-boot/" />
<link rel='shortlink' href='https://sterl.org/?p=1095' />
<link rel="alternate" title="oEmbed (JSON)" type="application/json+oembed" href="https://sterl.org/wp-json/oembed/1.0/embed?url=https%3A%2F%2Fsterl.org%2F2024%2F12%2Freact-spring-boot%2F" />
<link rel="alternate" title="oEmbed (XML)" type="text/xml+oembed" href="https://sterl.org/wp-json/oembed/1.0/embed?url=https%3A%2F%2Fsterl.org%2F2024%2F12%2Freact-spring-boot%2F&#038;format=xml" />
<style type="text/css">.recentcomments a{display:inline !important;padding:0 !important;margin:0 !important;}</style><style id="sccss">/* Enter Your Custom CSS Here */
code, kbd, .code {
  padding: 2px 4px;
  font-size: 90%;
  color: #fff;
  background-color: #333;
  border-radius: 3px;
  -webkit-box-shadow: inset 0 -1px 0 rgba(0,0,0,.25);
  box-shadow: inset 0 -1px 0 rgba(0,0,0,.25);
  font-family: Menlo,Monaco,Consolas,"Courier New",monospace;
}

.bs-callout {
  padding: 20px;
  margin: 20px 0;
  border: 1px solid #eee;
  border-left-width: 5px;
  border-radius: 3px;
}
.bs-callout-info {
  border-left-color: #1b809e;
}
.bs-callout-info h4, .bs-callout-info h3, .bs-callout-info h2, .bs-callout-info h5 {
  color: #1b809e;
}

.cm-s-eclipse span.cm-meta {
	color: #646464 !important;
}

.cm-s-eclipse span.cm-type + span.cm-variable {
	color: #00f !important;
}

.badge {
		display: inline-block;
    padding: .25em .4em;
    font-weight: 700;
    line-height: 1;
    text-align: center;
    white-space: nowrap;
    vertical-align: baseline;
    border-radius: .25rem;
}
.badge-success {
    color: #fff;
    background-color: #28a745;
}
.badge-warning {
    color: #212529;
    background-color: #ffc107;
}
.badge-danger,
.badge-error {
    color: #fff;
    background-color: #dc3545;
}</style></head>

<body class="wp-singular post-template-default single single-post postid-1095 single-format-standard wp-theme-wp-knowledge-base">
<div id="page" class="hfeed site">
		<header id="masthead" class="site-header" role="banner">
		<nav class="navbar navbar-default navbar-static-top main-navigation" role="navigation" id="site_navigation">
			<div class="container">
				<!-- Brand and toggle get grouped for better mobile display -->
				<div class="navbar-header">
					<button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-ex1-collapse">
						<span class="sr-only">Toggle navigation</span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
					</button>

										<a class="site-anchor" href="https://sterl.org/">
						<img src="https://sterl.org/wp-content/themes/wp-knowledge-base/logo.png" width="200" height="50" alt="">
					</a>
					
				</div>
				<!-- Collect the nav links, forms, and other content for toggling -->
				<div class="collapse navbar-collapse navbar-ex1-collapse">
					<ul id="menu-about-me" class="nav navbar-nav"><li id="menu-item-861" class="menu-item menu-item-type-custom menu-item-object-custom menu-item-861"><a title="YouTube" href="https://www.youtube.com/@IT-Paul">YouTube</a></li>
<li id="menu-item-121" class="menu-item menu-item-type-custom menu-item-object-custom menu-item-121"><a title="About Me" href="https://www.linkedin.com/pub/paul-sterl/ba/2b2/181">About Me</a></li>
</ul>				</div>
			</div>
		</nav>
	</header><!-- #masthead -->

	<div id="content" class="site-content container">

	<ol id="breadcrumbs" class="breadcrumb" xmlns:v="http://rdf.data-vocabulary.org/#">
		<li typeof="v:Breadcrumb">
		<a rel="v:url" property="v:title" href="https://sterl.org/">Home</a>
	</li>
	
									<li typeof="v:Breadcrumb">
			<a rel="v:url" property="v:title" href="https://sterl.org/category/java/">Java</a>
		</li>
		<li class="active" typeof="v:Breadcrumb">
			<span property="v:title">React &#038; Spring Boot</span>
		</li>
		</ol>

	<div id="primary" class="content-area col-md-8">
		<main id="main" class="site-main" role="main">

		
			
<article id="post-1095" class="post-1095 post type-post status-publish format-standard hentry category-java category-javascript category-react category-typescript tag-java tag-react tag-reactjs tag-spring tag-spring-boot tag-vite">
	<header class="entry-header page-header">
		<h1 class="entry-title">React &#038; Spring Boot</h1>

		<div class="entry-meta text-muted">
			<span class="posted-on"><i class="glyphicon glyphicon-calendar"></i> Posted on <a href="https://sterl.org/2024/12/react-spring-boot/" rel="bookmark"><time class="entry-date published" datetime="2024-12-11T19:36:41+01:00">11. Dezember 2024</time><time class="updated" datetime="2025-08-12T12:45:50+02:00">12. August 2025</time></a></span><span class="byline"> by <span class="author vcard"><i class="glyphicon glyphicon-user"></i> <a class="url fn n" href="https://sterl.org/author/sterlp/">Paul Sterl</a></span></span>		</div><!-- .entry-meta -->
	</header><!-- .entry-header -->

	<div class="entry-content">
		
<h2 id="ipt_kb_toc_1095_0" class="wp-block-heading">Problem</h2>



<p>We want to combine Spring Boot with a simple React frontend. Use the React development features but also be able to use the Spring IDE. In the end, everything should be built using Maven and nicely packed into a JAR, just like any other Microservice. Let’s get started.</p>



<h2 id="ipt_kb_toc_1095_1" class="wp-block-heading">Multi-Module project</h2>



<p>First we create a spring project here <code>backend</code> folder and a react project in the <code>frontend</code> folder. The root pom should help us to build both projects in one run:</p>


<div class="wp-block-image">
<figure class="aligncenter size-full"><a href="https://sterl.org/wp-content/uploads/2024/12/image.png"><img fetchpriority="high" decoding="async" width="294" height="211" src="https://sterl.org/wp-content/uploads/2024/12/image.png" alt="" class="wp-image-1096"/></a></figure></div>


<h3 id="ipt_kb_toc_1095_2" class="wp-block-heading">Root pom.xml</h3>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;xml&quot;,&quot;mime&quot;:&quot;application/xml&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;XML&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;xml&quot;}">&lt;project xmlns=&quot;http://maven.apache.org/POM/4.0.0&quot;
    xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot;
    xsi:schemaLocation=&quot;http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd&quot;&gt;
    &lt;modelVersion&gt;4.0.0&lt;/modelVersion&gt;

    &lt;groupId&gt;org.foo.bar&lt;/groupId&gt;
    &lt;artifactId&gt;project-root&lt;/artifactId&gt;
    &lt;version&gt;0.1.0-SNAPSHOT&lt;/version&gt;
    &lt;packaging&gt;pom&lt;/packaging&gt;

    &lt;properties&gt;
        &lt;java.version&gt;21&lt;/java.version&gt;
        &lt;maven.compiler.source&gt;${java.version}&lt;/maven.compiler.source&gt;
        &lt;maven.compiler.target&gt;${java.version}&lt;/maven.compiler.target&gt;
        &lt;project.build.sourceEncoding&gt;UTF-8&lt;/project.build.sourceEncoding&gt;
        &lt;project.reporting.outputEncoding&gt;UTF-8&lt;/project.reporting.outputEncoding&gt;
    &lt;/properties&gt;

    &lt;modules&gt;
        &lt;module&gt;frontend&lt;/module&gt;
        &lt;module&gt;backend&lt;/module&gt;
    &lt;/modules&gt;

  	&lt;!-- feel free to use spring as root instead of this --&gt;
    &lt;dependencyManagement&gt;
        &lt;dependencies&gt;
            &lt;dependency&gt;
                &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
                &lt;artifactId&gt;spring-boot-dependencies&lt;/artifactId&gt;
                &lt;version&gt;3.3.6&lt;/version&gt;
                &lt;type&gt;pom&lt;/type&gt;
                &lt;scope&gt;import&lt;/scope&gt;
            &lt;/dependency&gt;
        &lt;/dependencies&gt;
    &lt;/dependencyManagement&gt;
&lt;/project&gt;</pre></div>



<h3 id="ipt_kb_toc_1095_3" class="wp-block-heading">Backend pom.xml</h3>



<p>This is straight forward the generated pom from spring with just the dependency to the <code>frontend </code>project.</p>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;xml&quot;,&quot;mime&quot;:&quot;application/xml&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;XML&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;xml&quot;}">&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
&lt;project xmlns=&quot;http://maven.apache.org/POM/4.0.0&quot;
    xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot;
    xsi:schemaLocation=&quot;http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd&quot;&gt;
    &lt;modelVersion&gt;4.0.0&lt;/modelVersion&gt;
    &lt;parent&gt;
        &lt;groupId&gt;org.foo.bar&lt;/groupId&gt;
        &lt;artifactId&gt;project-root&lt;/artifactId&gt;
        &lt;version&gt;0.1.0-SNAPSHOT&lt;/version&gt;
        &lt;relativePath&gt;../pom.xml&lt;/relativePath&gt;
    &lt;/parent&gt;

    &lt;artifactId&gt;project-backend&lt;/artifactId&gt;

    &lt;dependencies&gt;
      	&lt;!-- thats the important part --&gt;
        &lt;dependency&gt;
            &lt;groupId&gt;org.foo.bar&lt;/groupId&gt;
            &lt;artifactId&gt;project-frontend&lt;/artifactId&gt;
            &lt;version&gt;${project.version}&lt;/version&gt;
        &lt;/dependency&gt;
      	&lt;!-- thats the important part end --&gt;
      
        &lt;dependency&gt;
            &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
            &lt;artifactId&gt;spring-boot-starter-web&lt;/artifactId&gt;
        &lt;/dependency&gt;  
        &lt;dependency&gt;
            &lt;groupId&gt;org.projectlombok&lt;/groupId&gt;
            &lt;artifactId&gt;lombok&lt;/artifactId&gt;
            &lt;optional&gt;true&lt;/optional&gt;
        &lt;/dependency&gt;
        &lt;dependency&gt;
            &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
            &lt;artifactId&gt;spring-boot-starter-test&lt;/artifactId&gt;
            &lt;scope&gt;test&lt;/scope&gt;
        &lt;/dependency&gt;
    &lt;/dependencies&gt;
    &lt;build&gt;
        &lt;plugins&gt;
            &lt;plugin&gt;
                &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
                &lt;artifactId&gt;spring-boot-maven-plugin&lt;/artifactId&gt;
            &lt;/plugin&gt;
        &lt;/plugins&gt;
    &lt;/build&gt;

&lt;/project&gt;</pre></div>



<h3 id="ipt_kb_toc_1095_4" class="wp-block-heading">Frontend pom.xml</h3>



<p>Now we have todo more stuff:</p>



<ol class="wp-block-list">
<li>Ensure the <code>dist</code> result is added to the JAR</li>



<li>Generate during the <code>generate-resources</code> phase also the content for the <code>dist</code> folder</li>



<li>Ensure that during a <code>mvn clean</code> all gets deleted</li>
</ol>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;xml&quot;,&quot;mime&quot;:&quot;application/xml&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;XML&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;xml&quot;}">&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;
&lt;project xmlns=&quot;http://maven.apache.org/POM/4.0.0&quot; xmlns:xsi=&quot;http://www.w3.org/2001/XMLSchema-instance&quot; xsi:schemaLocation=&quot;http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd&quot;&gt;
    &lt;modelVersion&gt;4.0.0&lt;/modelVersion&gt;
    &lt;parent&gt;
        &lt;groupId&gt;org.foo.bar&lt;/groupId&gt;
        &lt;artifactId&gt;project-root&lt;/artifactId&gt;
        &lt;version&gt;0.1.0-SNAPSHOT&lt;/version&gt;
        &lt;relativePath&gt;../pom.xml&lt;/relativePath&gt;
    &lt;/parent&gt;

    &lt;artifactId&gt;project-frontend&lt;/artifactId&gt;
    
    &lt;dependencies&gt;
        &lt;dependency&gt;
            &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
            &lt;artifactId&gt;spring-boot-starter-web&lt;/artifactId&gt;
            &lt;scope&gt;provided&lt;/scope&gt;
        &lt;/dependency&gt;
    &lt;/dependencies&gt;
    
    &lt;build&gt;
        &lt;!-- Ensure the dist result is added to the JAR --&gt;
        &lt;resources&gt;
            &lt;resource&gt;
                &lt;directory&gt;dist&lt;/directory&gt;
            &lt;/resource&gt;
        &lt;/resources&gt;

        &lt;plugins&gt;
          	&lt;!--
                Generate during the generate-resources phase 
                also the content for the dist folder 
            --&gt;
            &lt;plugin&gt;
                &lt;groupId&gt;org.codehaus.mojo&lt;/groupId&gt;
                &lt;artifactId&gt;exec-maven-plugin&lt;/artifactId&gt;
                &lt;version&gt;3.5.0&lt;/version&gt;
                &lt;executions&gt;
                    &lt;execution&gt;
                        &lt;id&gt;npm install&lt;/id&gt;
                        &lt;goals&gt;
                            &lt;goal&gt;exec&lt;/goal&gt;
                        &lt;/goals&gt;
                        &lt;phase&gt;generate-resources&lt;/phase&gt;
                        &lt;configuration&gt;
                            &lt;executable&gt;npm&lt;/executable&gt;
                            &lt;arguments&gt;
                                &lt;argument&gt;install&lt;/argument&gt;
                                &lt;argument&gt;--prefer-offline&lt;/argument&gt;
                                &lt;argument&gt;--no-audit&lt;/argument&gt;
                                &lt;argument&gt;--progress=false&lt;/argument&gt;
                            &lt;/arguments&gt;
                        &lt;/configuration&gt;
                    &lt;/execution&gt;

                    &lt;execution&gt;
                        &lt;id&gt;npm build&lt;/id&gt;
                        &lt;goals&gt;
                            &lt;goal&gt;exec&lt;/goal&gt;
                        &lt;/goals&gt;
                        &lt;phase&gt;generate-resources&lt;/phase&gt;
                        &lt;configuration&gt;
                            &lt;executable&gt;npm&lt;/executable&gt;
                            &lt;arguments&gt;
                                &lt;argument&gt;run&lt;/argument&gt;
                                &lt;argument&gt;build&lt;/argument&gt;
                            &lt;/arguments&gt;
                        &lt;/configuration&gt;
                    &lt;/execution&gt;
                &lt;/executions&gt;
            &lt;/plugin&gt;
            &lt;!-- Ensure that during a mvn clean all gets deleted --&gt;
            &lt;plugin&gt;
                &lt;groupId&gt;org.apache.maven.plugins&lt;/groupId&gt;
                &lt;artifactId&gt;maven-clean-plugin&lt;/artifactId&gt;
                &lt;version&gt;3.4.0&lt;/version&gt;
                &lt;configuration&gt;
                    &lt;filesets&gt;
                        &lt;fileset&gt;
                            &lt;directory&gt;node_modules&lt;/directory&gt;
                        &lt;/fileset&gt;
                        &lt;fileset&gt;
                            &lt;directory&gt;dist&lt;/directory&gt;
                        &lt;/fileset&gt;
                    &lt;/filesets&gt;
                &lt;/configuration&gt;
            &lt;/plugin&gt;
        &lt;/plugins&gt;
    &lt;/build&gt;

&lt;/project&gt;</pre></div>



<h3 id="ipt_kb_toc_1095_5" class="wp-block-heading">Ensure the result in the dist folder is compatible to Spring</h3>



<p>Spring will pick up automatically any static resources which are placed in the <code>static</code> folder. Here is an example for <a rel="noreferrer noopener" href="https://vite.dev/guide/" target="_blank">vite</a> to do so in the <code>vite.config.js</code>:</p>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;javascript&quot;,&quot;mime&quot;:&quot;application/json&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;JSON&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;json&quot;}">export default defineConfig({
  plugins: [react()],
  build: {
    outDir: './dist/static',
    emptyOutDir: true, // also necessary
  }
})</pre></div>



<h2 id="ipt_kb_toc_1095_6" class="wp-block-heading">Going beyond</h2>



<p>Sometime it is useful to host the UI in a sub path lets say <code>my-ui</code>, so the url would be <mark style="background-color:rgba(0, 0, 0, 0)" class="has-inline-color has-vivid-cyan-blue-color"><strong>http://localhost:8080/my-ui</strong></mark></p>



<p>For that we have to adjust the <code>vite.config.js</code>:</p>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;javascript&quot;,&quot;mime&quot;:&quot;application/json&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;JSON&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;json&quot;}">export default defineConfig({
  plugins: [react()],
  base: 'my-ui',
  build: {
    outDir: './dist/static/my-ui',
    emptyOutDir: true,
  }
})</pre></div>



<p>Now it would also be helpful if we don&#8217;t have to directly type the <code>index.html</code> but tell Spring always to re-route.</p>



<p>For that we have to add a Spring Configuration Class:</p>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;clike&quot;,&quot;mime&quot;:&quot;text/x-java&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;Java&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;java&quot;}">package org.foo.bar;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyFrontedConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController(&quot;/my-ui&quot;).setViewName(&quot;/my-ui/index.html&quot;);
        registry.addRedirectViewController(&quot;/my-ui/&quot;, &quot;/my-ui&quot;);
    }
}
</pre></div>



<h2 id="ipt_kb_toc_1095_7" class="wp-block-heading">Add cache control to static react assets</h2>



<p>In the end int would also be nice that the generated static resources are cached by the client e.g. for a year. We will extend the <code>MyFrontedConfig</code> class with:</p>



<div class="wp-block-codemirror-blocks-code-block code-block"><pre class="CodeMirror" data-setting="{&quot;showPanel&quot;:true,&quot;languageLabel&quot;:&quot;language&quot;,&quot;fullScreenButton&quot;:true,&quot;copyButton&quot;:true,&quot;mode&quot;:&quot;clike&quot;,&quot;mime&quot;:&quot;text/x-java&quot;,&quot;theme&quot;:&quot;material&quot;,&quot;lineNumbers&quot;:false,&quot;styleActiveLine&quot;:false,&quot;lineWrapping&quot;:false,&quot;readOnly&quot;:true,&quot;fileName&quot;:&quot;&quot;,&quot;language&quot;:&quot;Java&quot;,&quot;maxHeight&quot;:&quot;400px&quot;,&quot;modeName&quot;:&quot;java&quot;}">    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(&quot;/my-ui/assets/**&quot;) 
                .addResourceLocations(&quot;classpath:/static/my-ui/assets/&quot;) 
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));
    }</pre></div>
			</div><!-- .entry-content -->

	<footer class="entry-meta text-muted well well-sm">
		This entry was posted in <i class="glyphicon glyphicon-folder-open"></i>&nbsp;&nbsp;<a href="https://sterl.org/category/java/" rel="category tag">Java</a>, <a href="https://sterl.org/category/javascript/" rel="category tag">JavaScript</a>, <a href="https://sterl.org/category/react/" rel="category tag">React</a>, <a href="https://sterl.org/category/typescript/" rel="category tag">TypeScript</a> and tagged <i class="glyphicon glyphicon-tags"></i>&nbsp;&nbsp;<a href="https://sterl.org/tag/java/" rel="tag">Java</a>, <a href="https://sterl.org/tag/react/" rel="tag">React</a>, <a href="https://sterl.org/tag/reactjs/" rel="tag">ReactJs</a>, <a href="https://sterl.org/tag/spring/" rel="tag">Spring</a>, <a href="https://sterl.org/tag/spring-boot/" rel="tag">Spring Boot</a>, <a href="https://sterl.org/tag/vite/" rel="tag">vite</a>. <br />Bookmark the <i class="glyphicon glyphicon-bookmark"></i>&nbsp;&nbsp;<a href="https://sterl.org/2024/12/react-spring-boot/" rel="bookmark">permalink</a>.
			</footer><!-- .entry-meta -->
</article><!-- #post-## -->

			<p class="hidden-xs">
	<span class="ipt_kb_like_article_info pull-right text-info"><i class="glyphicon glyphicon-fire"></i>&nbsp;&nbsp;0 people found this article useful</span>
	<button type="button" data-loading-text="Please wait" class="btn btn-success ipt_kb_like_article" data-postid="1095" data-nonce="734198f16d">
		<i class="glyphicon glyphicon-thumbs-up"></i> <span class="text">This article was helpful</span>
	</button>
</p>
<p class="visible-xs">
	<button type="button" data-loading-text="Please wait" class="btn btn-success btn-block ipt_kb_like_article" data-postid="1095" data-nonce="734198f16d">
		<i class="glyphicon glyphicon-thumbs-up"></i> <span class="text">This article was helpful</span>
	</button>
</p>
<p class="ipt_kb_like_article_info text-info text-center visible-xs"><i class="glyphicon glyphicon-fire"></i>&nbsp;&nbsp;0 people found this article useful</p>
	
				<ul role="navigation" id="nav-below" class="post-navigation pager">
		<li class="screen-reader-text">Post navigation</li>
	
		<li class="nav-previous previous"><a href="https://sterl.org/2024/05/java-jobs-and-trigger-frameworks/" rel="prev"><span class="meta-nav"><span class="glyphicon glyphicon-arrow-left"></span></span> Java Jobs and Trigger Frameworks</a></li>		<li class="nav-next next"><a href="https://sterl.org/2025/12/java-simple-bound-memory-store/" rel="next">A Bounded, Thread-Safe In-Memory Store for Java <span class="meta-nav"><span class="glyphicon glyphicon-arrow-right"></span></span></a></li>
	
	</ul><!-- #nav-below -->
	
			<div class="well author-meta">
	<div class="author-avatar text-center pull-left">
		<a href="https://sterl.org/author/sterlp/"><img alt='' src='https://secure.gravatar.com/avatar/7a23a55fb3170a035496653356935690fb701f00984f889ca4098bac65cd57f7?s=96&#038;d=mm&#038;r=g' srcset='https://secure.gravatar.com/avatar/7a23a55fb3170a035496653356935690fb701f00984f889ca4098bac65cd57f7?s=192&#038;d=mm&#038;r=g 2x' class='avatar avatar-96 photo' height='96' width='96' decoding='async'/></a>
	</div>
	<h4 class="author-meta-title">
		Paul Sterl has written <a href="https://sterl.org/author/sterlp/">59</a> articles	</h4>
	<div class="author-meta-bio">
			</div>
	<div class="author-meta-buttons text-right">
		<div class="btn-group">
																																																	</div>
	</div>
	<div class="clearfix"></div>
</div>
	
			
	<div id="comments" class="comments-area">

	
	
	
											<div id="respond" class="comment-respond">
				<span class="pull-right"><a class="btn btn-danger btn-sm" rel="nofollow" id="cancel-comment-reply-link" href="/2024/12/react-spring-boot/#respond" style="display:none;">Cancel reply</a></span>
				<h3 id="reply-title" class="comment-reply-title">Leave a Reply</h3>
									<form action="https://sterl.org/wp-comments-post.php" method="post" id="commentform" class="comment-form form-horizontal" novalidate>
																			<p class="comment-notes alert alert-info">Your email address will not be published. Required fields are marked <span class="required">*</span></p>														<div class="col-sm-5">
								<div class="form-group comment-form-author"><label class="sr-only control-label" for="author">Name <span class="required">*</span></label> <div class="input-group"><span class="input-group-addon"><span class="glyphicon ipt-icon-user"></span></span><input placeholder="Name *" class="form-control" id="author" name="author" type="text" value="" size="30" aria-required='true' /></div></div>
<div class="form-group comment-form-email"><label class="sr-only control-label" for="email">Email <span class="required">*</span></label> <div class="input-group"><span class="input-group-addon"><span class="glyphicon ipt-icon-mail"></span></span><input placeholder="Email *" class="form-control" id="email" name="email" type="email" value="" size="30" aria-required='true' /></div></div>
<div class="form-group comment-form-url"><label class="sr-only control-label" for="url">Website</label> <div class="input-group"><span class="input-group-addon"><span class="glyphicon ipt-icon-link"></span></span><input placeholder="Website" class="form-control" id="url" name="url" type="url" value="" size="30" /></div></div>
							</div>
							<div class="col-sm-7">
								<div class="comment-form-comment form-group"><label class="sr-only control-label" for="comment">Comment</label><textarea placeholder="Comment" class="form-control" id="comment" name="comment" rows="6" aria-required="true"></textarea></div>							</div>
							<div class="clearfix"></div>
							<div class="col-sm-12">
															</div>
							<div class="clearfix"></div>
						
						<p class="form-allowed-tags well well-sm">You may use these <abbr title="HyperText Markup Language">HTML</abbr> tags and attributes:  <code>&lt;a href=&quot;&quot; title=&quot;&quot;&gt; &lt;abbr title=&quot;&quot;&gt; &lt;acronym title=&quot;&quot;&gt; &lt;b&gt; &lt;blockquote cite=&quot;&quot;&gt; &lt;cite&gt; &lt;code&gt; &lt;del datetime=&quot;&quot;&gt; &lt;em&gt; &lt;i&gt; &lt;q cite=&quot;&quot;&gt; &lt;s&gt; &lt;strike&gt; &lt;strong&gt; </code></p>						<p class="form-submit text-right">
							<button name="submit" class="btn btn-lg btn-primary" type="submit" id="submit">Post Comment</button>
							<input type='hidden' name='comment_post_ID' value='1095' id='comment_post_ID' />
<input type='hidden' name='comment_parent' id='comment_parent' value='0' />
						</p>
						<p style="display: none;"><input type="hidden" id="akismet_comment_nonce" name="akismet_comment_nonce" value="4fbc482406" /></p><p style="display: none !important;" class="akismet-fields-container" data-prefix="ak_"><label>&#916;<textarea name="ak_hp_textarea" cols="45" rows="8" maxlength="100"></textarea></label><input type="hidden" id="ak_js_1" name="ak_js" value="103"/><script>document.getElementById( "ak_js_1" ).setAttribute( "value", ( new Date() ).getTime() );</script></p>					</form>
							</div><!-- #respond -->
						
</div><!-- #comments -->

		
		</main><!-- #main -->
	</div><!-- #primary -->

	<div id="secondary" class="widget-area col-md-4" role="complementary">
						<aside id="search-2" class="widget widget_search panel panel-default"><div class="panel-heading"><h3 class="widget-title panel-title"> </h3></div><div class="panel-body"><form role="search" method="get" class="search-form" action="https://sterl.org/">
			<div class="form-group">
		<div class="input-group input-group-lg">
			<input type="search" class="search-field form-control" placeholder="Search Knowledgebase&hellip;" value="" name="s" />
			<span class="input-group-btn"><button type="submit" class="btn btn-default"><span class="ipt-icon-search"></span></button></span>
		</div>
	</div>
</form>
</div></aside>
		<aside id="recent-posts-2" class="widget widget_recent_entries panel panel-default">
		<div class="panel-heading"><h3 class="widget-title panel-title">Neueste Beiträge</h3></div><div class="panel-body">
		<ul>
											<li>
					<a href="https://sterl.org/2025/12/java-simple-bound-memory-store/">A Bounded, Thread-Safe In-Memory Store for Java</a>
									</li>
											<li>
					<a href="https://sterl.org/2025/08/rest-soap-api-client/">REST SOAP API Client</a>
									</li>
											<li>
					<a href="https://sterl.org/2025/04/spring-persistent-tasks/">Spring Persistent Tasks</a>
									</li>
											<li>
					<a href="https://sterl.org/2025/01/propagation-requires_new-never-transaktionsmanagement-mit-spring-boot-jpa-hibernate/">Propagation REQUIRES_NEW &amp; NEVER | Transaktionsmanagement mit Spring Boot, JPA &amp; Hibernate</a>
									</li>
											<li>
					<a href="https://sterl.org/2024/12/react-spring-boot/" aria-current="page">React &#038; Spring Boot</a>
									</li>
					</ul>

		</div></aside><aside id="recent-comments-2" class="widget widget_recent_comments panel panel-default"><div class="panel-heading"><h3 class="widget-title panel-title">Neueste Kommentare</h3></div><div class="panel-body"><ul id="recentcomments"><li class="recentcomments"><span class="comment-author-link">Musfirah</span> bei <a href="https://sterl.org/2023/03/azure-devops-function-app-deployment-no-package-found/#comment-7290">Azure DevOps Function App Deployment &#8222;No package found&#8220;</a></li><li class="recentcomments"><span class="comment-author-link">Musfirah</span> bei <a href="https://sterl.org/2023/03/azure-devops-function-app-deployment-no-package-found/#comment-7289">Azure DevOps Function App Deployment &#8222;No package found&#8220;</a></li><li class="recentcomments"><span class="comment-author-link"><a href="https://sterl.org/2023/10/microservices-event-command-data-bus-event-driven/" class="url" rel="ugc">Microservices Event- | Command- | Data-Bus | Event Driven | Pauls Blog</a></span> bei <a href="https://sterl.org/2023/10/useful-links-for-event-driven-architectures/#comment-2644">Useful links for Event Driven Architectures</a></li><li class="recentcomments"><span class="comment-author-link"><a href="https://sterl.org/2022/07/quartz-triggers-jobs-groups-and-more/" class="url" rel="ugc">Quartz triggers, jobs, groups and more | Pauls Blog</a></span> bei <a href="https://sterl.org/2022/07/quartz-integration-in-spring/#comment-2041">Quartz integration in Spring</a></li><li class="recentcomments"><span class="comment-author-link"><a href="https://sterl.org/2022/07/quartz-triggers-jobs-groups-and-more/" class="url" rel="ugc">Quartz triggers, jobs, groups and more | Pauls Blog</a></span> bei <a href="https://sterl.org/2019/09/quartz-scheduler-integration-in-java-jee/#comment-2040">Quartz integration in JEE</a></li></ul></div></aside><aside id="archives-2" class="widget widget_archive panel panel-default"><div class="panel-heading"><h3 class="widget-title panel-title">Archiv</h3></div><div class="panel-body">
			<ul>
					<li><a href='https://sterl.org/2025/12/'>Dezember 2025</a></li>
	<li><a href='https://sterl.org/2025/08/'>August 2025</a></li>
	<li><a href='https://sterl.org/2025/04/'>April 2025</a></li>
	<li><a href='https://sterl.org/2025/01/'>Januar 2025</a></li>
	<li><a href='https://sterl.org/2024/12/'>Dezember 2024</a></li>
	<li><a href='https://sterl.org/2024/09/'>September 2024</a></li>
	<li><a href='https://sterl.org/2024/08/'>August 2024</a></li>
	<li><a href='https://sterl.org/2024/05/'>Mai 2024</a></li>
	<li><a href='https://sterl.org/2024/03/'>März 2024</a></li>
	<li><a href='https://sterl.org/2024/02/'>Februar 2024</a></li>
	<li><a href='https://sterl.org/2024/01/'>Januar 2024</a></li>
	<li><a href='https://sterl.org/2023/12/'>Dezember 2023</a></li>
	<li><a href='https://sterl.org/2023/10/'>Oktober 2023</a></li>
	<li><a href='https://sterl.org/2023/08/'>August 2023</a></li>
	<li><a href='https://sterl.org/2023/03/'>März 2023</a></li>
	<li><a href='https://sterl.org/2023/01/'>Januar 2023</a></li>
	<li><a href='https://sterl.org/2022/07/'>Juli 2022</a></li>
	<li><a href='https://sterl.org/2022/05/'>Mai 2022</a></li>
	<li><a href='https://sterl.org/2021/08/'>August 2021</a></li>
	<li><a href='https://sterl.org/2021/05/'>Mai 2021</a></li>
	<li><a href='https://sterl.org/2021/01/'>Januar 2021</a></li>
	<li><a href='https://sterl.org/2020/12/'>Dezember 2020</a></li>
	<li><a href='https://sterl.org/2020/06/'>Juni 2020</a></li>
	<li><a href='https://sterl.org/2020/05/'>Mai 2020</a></li>
	<li><a href='https://sterl.org/2020/04/'>April 2020</a></li>
	<li><a href='https://sterl.org/2020/02/'>Februar 2020</a></li>
	<li><a href='https://sterl.org/2019/09/'>September 2019</a></li>
	<li><a href='https://sterl.org/2019/06/'>Juni 2019</a></li>
	<li><a href='https://sterl.org/2019/02/'>Februar 2019</a></li>
	<li><a href='https://sterl.org/2017/10/'>Oktober 2017</a></li>
	<li><a href='https://sterl.org/2017/09/'>September 2017</a></li>
	<li><a href='https://sterl.org/2017/08/'>August 2017</a></li>
	<li><a href='https://sterl.org/2017/06/'>Juni 2017</a></li>
	<li><a href='https://sterl.org/2016/10/'>Oktober 2016</a></li>
	<li><a href='https://sterl.org/2016/07/'>Juli 2016</a></li>
	<li><a href='https://sterl.org/2016/06/'>Juni 2016</a></li>
	<li><a href='https://sterl.org/2016/03/'>März 2016</a></li>
	<li><a href='https://sterl.org/2016/02/'>Februar 2016</a></li>
	<li><a href='https://sterl.org/2015/09/'>September 2015</a></li>
	<li><a href='https://sterl.org/2015/07/'>Juli 2015</a></li>
	<li><a href='https://sterl.org/2015/06/'>Juni 2015</a></li>
			</ul>

			</div></aside><aside id="categories-2" class="widget widget_categories panel panel-default"><div class="panel-heading"><h3 class="widget-title panel-title">Kategorien</h3></div><div class="panel-body">
			<ul>
					<li class="cat-item cat-item-25"><a href="https://sterl.org/category/android/">Android</a>
</li>
	<li class="cat-item cat-item-48"><a href="https://sterl.org/category/angular/">Angular</a>
</li>
	<li class="cat-item cat-item-118"><a href="https://sterl.org/category/java/aop/">AOP</a>
</li>
	<li class="cat-item cat-item-145"><a href="https://sterl.org/category/cloud/azure/">Azure</a>
</li>
	<li class="cat-item cat-item-140"><a href="https://sterl.org/category/cloud/ci-cd-devops/">CI/CD DevOps</a>
</li>
	<li class="cat-item cat-item-139"><a href="https://sterl.org/category/cloud/">Cloud</a>
</li>
	<li class="cat-item cat-item-1"><a href="https://sterl.org/category/java/">Java</a>
</li>
	<li class="cat-item cat-item-20"><a href="https://sterl.org/category/javascript/">JavaScript</a>
</li>
	<li class="cat-item cat-item-87"><a href="https://sterl.org/category/java/java-enterprise-edition/">JEE</a>
</li>
	<li class="cat-item cat-item-61"><a href="https://sterl.org/category/java/jpa/">JPA</a>
</li>
	<li class="cat-item cat-item-43"><a href="https://sterl.org/category/java/jsr-validation/">JSR Validation</a>
</li>
	<li class="cat-item cat-item-44"><a href="https://sterl.org/category/mongodb/">MongoDB</a>
</li>
	<li class="cat-item cat-item-4"><a href="https://sterl.org/category/pattern/">Pattern &amp; Best Practice</a>
</li>
	<li class="cat-item cat-item-22"><a href="https://sterl.org/category/performance/">Performance</a>
</li>
	<li class="cat-item cat-item-131"><a href="https://sterl.org/category/tutorials/quartz/">Quartz</a>
</li>
	<li class="cat-item cat-item-240"><a href="https://sterl.org/category/react/">React</a>
</li>
	<li class="cat-item cat-item-12"><a href="https://sterl.org/category/java/spring-boot/">Spring Boot</a>
</li>
	<li class="cat-item cat-item-47"><a href="https://sterl.org/category/testing/">Testing</a>
</li>
	<li class="cat-item cat-item-74"><a href="https://sterl.org/category/tools/">Tools</a>
</li>
	<li class="cat-item cat-item-21"><a href="https://sterl.org/category/tutorials/">Tutorials</a>
</li>
	<li class="cat-item cat-item-163"><a href="https://sterl.org/category/typescript/">TypeScript</a>
</li>
	<li class="cat-item cat-item-172"><a href="https://sterl.org/category/youtube/">YouTube</a>
</li>
			</ul>

			</div></aside><aside id="meta-2" class="widget widget_meta panel panel-default"><div class="panel-heading"><h3 class="widget-title panel-title">Meta</h3></div><div class="panel-body">
		<ul>
						<li><a href="https://sterl.org/wp-login.php">Anmelden</a></li>
			<li><a href="https://sterl.org/feed/">Feed der Einträge</a></li>
			<li><a href="https://sterl.org/comments/feed/">Kommentar-Feed</a></li>

			<li><a href="https://de.wordpress.org/">WordPress.org</a></li>
		</ul>

		</div></aside>			</div><!-- #secondary -->

	</div><!-- #content -->
	<footer id="colophon" class="site-footer" role="contentinfo">
	<div class="container">
		<div class="row">
			<div class="col-md-8" id="footer-widget-1">
							</div>
			<div class="col-md-4" id="footer-widget-2">
				<aside id="block-5" class="widget widget_block widget_text">
<p><a href="https://www.youtube.com/@IT-Paul">https://www.youtube.com/@IT-Paul</a></p>
</aside><aside id="block-4" class="widget widget_block"></aside>			</div>
		</div>
	</div>
		<div class="site-info">
			<p class="copyright text-muted">
				<a href="http://wordpress.org/" rel="generator">Proudly powered by WordPress</a>
				<span class="sep"> | </span>
				Theme: WP Knowledge Base by <a href="http://ipanelthemes.com/" rel="designer">iPanelThemes.com</a>.			</p>
					</div><!-- .site-info -->
		<script type="speculationrules">
{"prefetch":[{"source":"document","where":{"and":[{"href_matches":"\/*"},{"not":{"href_matches":["\/wp-*.php","\/wp-admin\/*","\/wp-content\/uploads\/*","\/wp-content\/*","\/wp-content\/plugins\/*","\/wp-content\/themes\/wp-knowledge-base\/*","\/*\\?(.+)"]}},{"not":{"selector_matches":"a[rel~=\"nofollow\"]"}},{"not":{"selector_matches":".no-prefetch, .no-prefetch a"}}]},"eagerness":"conservative"}]}
</script>
<script type="text/javascript" src="https://sterl.org/wp-content/plugins/wp-codemirror-block/vendor/codemirror/lib/codemirror.min.js?ver=5.40.5" id="codemirror-js"></script>
<script type="text/javascript" id="codemirror-autoload-js-before">
/* <![CDATA[ */
var wpcm = {
    "plugin_url": "https:\/\/sterl.org\/wp-content\/plugins\/wp-codemirror-block",
    "assets": [],
    "panelOptions": {
        "showPanel": true,
        "languageLabel": "language",
        "fullScreenButton": true,
        "copyButton": true
    },
    "editorOptions": [],
    "view": "public"
}
/* ]]> */
</script>
<script type="text/javascript" src="https://sterl.org/wp-content/plugins/wp-codemirror-block/build/autoload.js?ver=18ecfe40212e78f8c234" id="codemirror-autoload-js"></script>
<script type="text/javascript" src="https://sterl.org/wp-content/plugins/wp-codemirror-block/build/view.js?ver=273056f75bf73b6f2d72" id="codemirror-view-js"></script>
<script type="text/javascript" src="https://sterl.org/wp-content/themes/wp-knowledge-base/js/skip-link-focus-fix.js?ver=1.6.0" id="ipt_kb-skip-link-focus-fix-js"></script>
<script type="text/javascript" src="https://sterl.org/wp-includes/js/comment-reply.min.js?ver=6.8.3" id="comment-reply-js" async="async" data-wp-strategy="async"></script>
<script type="text/javascript" src="https://sterl.org/wp-content/plugins/wp-scrollspy-menu/assets/js/scrollspy_wp.js?ver=6.8.3" id="scrollspy_wp-js"></script>
<script defer type="text/javascript" src="https://sterl.org/wp-content/plugins/akismet/_inc/akismet-frontend.js?ver=1745351532" id="akismet-frontend-js"></script>
	</footer><!-- #colophon -->
</div><!-- #page -->

</body>
</html><!-- WP Fastest Cache file was created in 0.084 seconds, on 30. December 2025 @ 18:52 --><!-- via php -->
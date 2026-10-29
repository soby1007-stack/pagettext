(function () {
  var MODE = '__MODE__';
  function clean(t) {
    return (t || '').replace(/\u00a0/g, ' ').replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
  }
  function label(el) {
    if (!el) return 'none';
    var s = el.tagName.toLowerCase();
    if (el.id) s += '#' + el.id;
    if (el.className && typeof el.className === 'string') {
      s += '.' + el.className.trim().split(/\s+/).slice(0, 2).join('.');
    }
    return s;
  }
  var result = { mode: MODE, title: document.title || '', url: location.href };
  try {
    var body = document.body;
    var full = clean(body ? body.innerText : '');
    result.fullLen = full.length;
    var text = full, src = 'body';
    if (MODE === 'main' && body) {
      var node = null, picked = 'none';
      var semantic = document.querySelectorAll('article, main, [role=main]');
      var best = null, bestLen = 0;
      for (var i = 0; i < semantic.length; i++) {
        var l = (semantic[i].innerText || '').length;
        if (l > bestLen) { best = semantic[i]; bestLen = l; }
      }
      if (best && bestLen >= 300) { node = best; picked = 'semantic'; }
      if (!node) {
        var els = document.querySelectorAll('div, section, td');
        var n = Math.min(els.length, 2500);
        var scored = [], maxScore = 0;
        for (var j = 0; j < n; j++) {
          var el = els[j];
          var len = (el.innerText || '').length;
          if (len < 200) continue;
          var linkLen = 0, as = el.getElementsByTagName('a');
          for (var k = 0; k < as.length; k++) linkLen += (as[k].innerText || '').length;
          var score = len - 2 * linkLen;
          scored.push({ el: el, len: len, score: score });
          if (score > maxScore) maxScore = score;
        }
        result.candidates = scored.length;
        var chosen = null;
        for (var m = 0; m < scored.length; m++) {
          var c = scored[m];
          if (c.score >= maxScore * 0.8 && (!chosen || c.len < chosen.len)) chosen = c;
        }
        if (chosen) { node = chosen.el; picked = 'density'; }
      }
      if (node) {
        var t = clean(node.innerText);
        if (t.length >= 100) { text = t; src = picked + ':' + label(node); }
        else { src = 'body(fallback: picked node too short)'; }
      } else {
        src = 'body(fallback: no candidate)';
      }
    }
    result.text = text;
    result.src = src;
    result.len = text.length;
    result.scrollHeight = document.documentElement.scrollHeight;
  } catch (e) {
    result.error = String(e && e.stack ? e.stack : e);
    result.text = '';
  }
  return JSON.stringify(result);
})();

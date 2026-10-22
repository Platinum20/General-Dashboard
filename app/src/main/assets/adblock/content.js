/*
 * Blocco pubblicità nella pagina, sul modello di uBlock Origin.
 * Gira prima degli script del sito, in ogni riquadro della pagina. Chiede all'app (GDBridge)
 * i filtri del sito e li applica: scriptlet, elementi da nascondere, filtri generici e procedurali.
 */
(function () {
  'use strict';
  if (window.__gdAdBlock) return;
  try {
    Object.defineProperty(window, '__gdAdBlock', { value: true });
  } catch (e) {
    return;
  }
  var bridge = window.GDBridge;
  if (!bridge || typeof bridge.page !== 'function') return;
  if (location.protocol !== 'https:' && location.protocol !== 'http:') return;

  // Copie delle funzioni native: il sito può sostituirle dopo.
  var jsonParse = JSON.parse;
  var jsonStringify = JSON.stringify;
  var defineProperty = Object.defineProperty;
  var getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
  var nativeSetTimeout = window.setTimeout;
  var MutationObs = window.MutationObserver;

  var data;
  try {
    data = jsonParse(bridge.page(location.href));
  } catch (e) {
    return;
  }
  if (!data) return;

  /* ---------------------------------------------------------------- utilità */

  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn, { once: true });
    } else {
      fn();
    }
  }

  /** "/re/flags" diventa un'espressione regolare, il resto un testo da cercare. */
  function toRegex(pattern, defaultFlags) {
    if (pattern === undefined || pattern === null || pattern === '') return /^/;
    var m = /^\/(.+)\/([dgimsuy]*)$/.exec(pattern);
    if (m) {
      try {
        return new RegExp(m[1], m[2]);
      } catch (e) {
        return null;
      }
    }
    return new RegExp(pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), defaultFlags || '');
  }

  /** Testo che corrisponde (o, con "!" davanti, che non corrisponde) al modello. */
  function needleMatcher(needle) {
    if (!needle) return function () { return true; };
    var negate = needle.charAt(0) === '!';
    var re = toRegex(negate ? needle.slice(1) : needle);
    if (!re) return function () { return false; };
    return function (text) { return re.test(String(text)) !== negate; };
  }

  /** "url:/x/ method:POST", "/x/", "player?": condizioni sulle richieste di rete. */
  function propsMatcher(raw) {
    if (!raw) return function () { return true; };
    var conditions = [];
    raw.split(/\s+/).forEach(function (part) {
      if (!part) return;
      var m = /^(url|method):(.+)$/.exec(part);
      var key = m ? m[1] : 'url';
      var re = toRegex(m ? m[2] : part, key === 'method' ? 'i' : '');
      if (re) conditions.push([key, re]);
    });
    return function (details) {
      for (var i = 0; i < conditions.length; i++) {
        var value = details[conditions[i][0]];
        if (value === undefined || !conditions[i][1].test(String(value))) return false;
      }
      return true;
    };
  }

  /** Argomenti opzionali "nome, valore, nome, valore". */
  function extraArgs(list) {
    var out = {};
    for (var i = 0; i + 1 < list.length; i += 2) out[list[i]] = list[i + 1];
    return out;
  }

  var noop = function () {};
  var INVALID = {};

  /** I valori ammessi da set-constant. */
  function constantValue(raw, trusted) {
    switch (raw) {
      case 'undefined': return undefined;
      case 'false': return false;
      case 'true': return true;
      case 'null': return null;
      case "''": case '""': case 'emptyStr': case '': return '';
      case 'noopFunc': return function () {};
      case 'trueFunc': return function () { return true; };
      case 'falseFunc': return function () { return false; };
      case 'throwFunc': return function () { throw new Error(); };
      case 'emptyArr': case '[]': return [];
      case 'emptyObj': case '{}': return {};
      case 'noopPromiseResolve': return function () { return Promise.resolve(); };
      case 'noopPromiseReject': return function () { return Promise.reject(); };
      case 'yes': case 'no': case 'on': case 'off': case 'ok': case 'accept': case 'reject': return raw;
      case 'NaN': return NaN;
      case 'Infinity': return Infinity;
      case '-Infinity': return -Infinity;
    }
    if (/^-?\d+$/.test(raw)) {
      var n = parseInt(raw, 10);
      if (trusted || Math.abs(n) <= 0x7FFF) return n;
    }
    if (trusted) {
      try { return jsonParse(raw); } catch (e) { return raw; }
    }
    return INVALID;
  }

  /**
   * Segue la catena "a.b.c" anche se gli oggetti non esistono ancora: quando il sito li crea,
   * [onFinal] viene chiamata su quello che contiene l'ultima proprietà.
   */
  // Proprietà già intercettate da noi: più scriptlet sulla stessa catena si aggiungono, non si sostituiscono.
  var traps = new WeakMap();

  function trapChain(owner, chain, onFinal) {
    var dot = chain.indexOf('.');
    if (dot === -1) {
      onFinal(owner, chain);
      return;
    }
    var prop = chain.slice(0, dot);
    var rest = chain.slice(dot + 1);
    var current;
    try { current = owner[prop]; } catch (e) { return; }
    if (current instanceof Object || (typeof current === 'object' && current !== null)) {
      trapChain(current, rest, onFinal);
      return;
    }
    var byProp = traps.get(owner);
    if (!byProp) {
      byProp = Object.create(null);
      traps.set(owner, byProp);
    }
    var waiting = byProp[prop];
    if (waiting) {
      waiting.push([rest, onFinal]);
      return;
    }
    var desc = getOwnPropertyDescriptor(owner, prop);
    if (desc && !desc.configurable) return;
    waiting = byProp[prop] = [[rest, onFinal]];
    var value = current;
    try {
      defineProperty(owner, prop, {
        configurable: true,
        enumerable: true,
        get: function () { return value; },
        set: function (v) {
          value = v;
          if (v instanceof Object) waiting.forEach(function (w) { trapChain(v, w[0], w[1]); });
        }
      });
    } catch (e) {}
  }

  /* ---------------------------------------------------------- percorsi JSON */

  function hasPath(obj, segs, i) {
    if (i === segs.length) return true;
    if (obj === null || typeof obj !== 'object') return false;
    var s = segs[i];
    if (s === '[]' || s === '[-]' || s === '*') {
      for (var k in obj) if (hasPath(obj[k], segs, i + 1)) return true;
      return false;
    }
    if (i === segs.length - 1) return Object.prototype.hasOwnProperty.call(obj, s);
    return hasPath(obj[s], segs, i + 1);
  }

  /** Toglie quello che indica il percorso; true se ha cambiato qualcosa. */
  function prunePath(obj, segs, i) {
    if (obj === null || typeof obj !== 'object') return false;
    var s = segs[i];
    var last = i === segs.length - 1;
    var changed = false;
    if (s === '[-]') {
      if (!Array.isArray(obj)) return false;
      for (var j = obj.length - 1; j >= 0; j--) {
        if (hasPath(obj[j], segs, i + 1)) {
          obj.splice(j, 1);
          changed = true;
        }
      }
      return changed;
    }
    if (s === '[]' || s === '*') {
      for (var k in obj) {
        if (last) {
          if (s === '*') { delete obj[k]; changed = true; }
        } else if (prunePath(obj[k], segs, i + 1)) {
          changed = true;
        }
      }
      return changed;
    }
    if (last) {
      if (Object.prototype.hasOwnProperty.call(obj, s)) {
        delete obj[s];
        return true;
      }
      return false;
    }
    return prunePath(obj[s], segs, i + 1);
  }

  function makePruner(rawPrune, rawNeedles) {
    var prune = (rawPrune || '').split(/\s+/).filter(Boolean).map(function (p) { return p.split('.'); });
    var needles = (rawNeedles || '').split(/\s+/).filter(Boolean).map(function (p) { return p.split('.'); });
    if (prune.length === 0) return null;
    return function (obj) {
      if (obj === null || typeof obj !== 'object') return false;
      for (var i = 0; i < needles.length; i++) if (!hasPath(obj, needles[i], 0)) return false;
      var changed = false;
      for (var j = 0; j < prune.length; j++) if (prunePath(obj, prune[j], 0)) changed = true;
      return changed;
    };
  }

  /* --------------------------------------------------- risposte di rete */

  // Trasformazioni del testo delle risposte di fetch e XMLHttpRequest.
  var fetchTransforms = [];
  var xhrTransforms = [];

  function requestDetails(input, init) {
    var url = '';
    var method = 'GET';
    try {
      if (typeof input === 'string') url = input;
      else if (input instanceof Request) { url = input.url; method = input.method; }
      else url = String(input);
      if (init && init.method) method = init.method;
      url = new URL(url, location.href).href;
    } catch (e) {}
    return { url: url, method: String(method).toUpperCase() };
  }

  function installFetchHook() {
    if (installFetchHook.done || typeof window.fetch !== 'function') return;
    installFetchHook.done = true;
    window.fetch = new Proxy(window.fetch, {
      apply: function (target, thisArg, args) {
        var details = requestDetails(args[0], args[1]);
        var active = fetchTransforms.filter(function (t) { return t.match(details); });
        var block = active.filter(function (t) { return t.block; })[0];
        if (block) return Promise.resolve(new Response(block.body || '', { status: 200, statusText: 'OK' }));
        var promise = Reflect.apply(target, thisArg, args);
        if (active.length === 0) return promise;
        return promise.then(function (response) {
          return response.clone().text().then(function (text) {
            var out = text;
            active.forEach(function (t) { out = t.apply(out); });
            if (out === text) return response;
            var copy = new Response(out, { status: response.status, statusText: response.statusText, headers: response.headers });
            try {
              defineProperty(copy, 'url', { value: response.url });
              defineProperty(copy, 'type', { value: response.type });
              defineProperty(copy, 'redirected', { value: response.redirected });
            } catch (e) {}
            return copy;
          }).catch(function () { return response; });
        });
      }
    });
  }

  function installXhrHook() {
    if (installXhrHook.done || typeof XMLHttpRequest === 'undefined') return;
    installXhrHook.done = true;
    var proto = XMLHttpRequest.prototype;
    var open = proto.open;
    var send = proto.send;
    var textDesc = getOwnPropertyDescriptor(proto, 'responseText');
    var responseDesc = getOwnPropertyDescriptor(proto, 'response');
    proto.open = function (method, url) {
      var details = requestDetails(String(url), { method: method });
      this.__gdTransforms = xhrTransforms.filter(function (t) { return t.match(details); });
      this.__gdResult = undefined;
      this.__gdUrl = details.url;
      return open.apply(this, arguments);
    };
    proto.send = function () {
      var list = this.__gdTransforms;
      var block = list && list.filter(function (t) { return t.block; })[0];
      if (!block) return send.apply(this, arguments);
      // Richiesta annullata: sembra finita bene, con una risposta vuota.
      var xhr = this;
      var body = block.body || '';
      try {
        defineProperty(xhr, 'readyState', { value: 4, configurable: true });
        defineProperty(xhr, 'status', { value: 200, configurable: true });
        defineProperty(xhr, 'statusText', { value: 'OK', configurable: true });
        defineProperty(xhr, 'responseText', { value: body, configurable: true });
        defineProperty(xhr, 'response', { value: body, configurable: true });
        defineProperty(xhr, 'responseURL', { value: xhr.__gdUrl || '', configurable: true });
      } catch (e) {}
      nativeSetTimeout(function () {
        ['readystatechange', 'load', 'loadend'].forEach(function (type) {
          try { xhr.dispatchEvent(new Event(type)); } catch (e) {}
        });
      }, 1);
    };
    function transformed(xhr) {
      var list = xhr.__gdTransforms;
      if (!list || list.length === 0 || xhr.readyState !== 4) return undefined;
      if (xhr.__gdResult !== undefined) return xhr.__gdResult;
      var type = xhr.responseType;
      var original;
      if (type === '' || type === 'text') original = textDesc.get.call(xhr);
      else if (type === 'json') {
        var obj = responseDesc.get.call(xhr);
        list.forEach(function (t) { if (t.applyObject) t.applyObject(obj); });
        xhr.__gdResult = obj;
        return obj;
      } else return undefined;
      var out = original;
      list.forEach(function (t) { out = t.apply(out); });
      xhr.__gdResult = out;
      return out;
    }
    if (textDesc && textDesc.get) {
      defineProperty(proto, 'responseText', {
        configurable: true, enumerable: true,
        get: function () { var t = transformed(this); return t !== undefined ? t : textDesc.get.call(this); }
      });
    }
    if (responseDesc && responseDesc.get) {
      defineProperty(proto, 'response', {
        configurable: true, enumerable: true,
        get: function () { var t = transformed(this); return t !== undefined ? t : responseDesc.get.call(this); }
      });
    }
  }

  function jsonTextPruner(pruner) {
    return function (text) {
      if (!text || (text.charAt(0) !== '{' && text.charAt(0) !== '[')) return text;
      try {
        var obj = jsonParse(text);
        return pruner(obj) ? jsonStringify(obj) : text;
      } catch (e) {
        return text;
      }
    };
  }

  function textReplacer(pattern, replacement) {
    var re = toRegex(pattern);
    if (!re) return null;
    if (!re.global && !/^\/.+\/[a-z]*$/.test(pattern)) re = new RegExp(re.source, 'g');
    return function (text) { return typeof text === 'string' ? text.replace(re, replacement || '') : text; };
  }

  /* -------------------------------------------------------------- scriptlet */

  var scriptlets = {};
  function alias(names, fn) { names.forEach(function (n) { scriptlets[n] = fn; }); }

  alias(['set-constant', 'set', 'trusted-set-constant', 'trusted-set'], function (args, name) {
    var trusted = name.indexOf('trusted-') === 0;
    var chain = args[0];
    if (!chain) return;
    var value = constantValue(args[1] === undefined ? 'undefined' : args[1], trusted);
    if (value === INVALID) return;
    trapChain(window, chain, function (owner, prop) {
      try {
        defineProperty(owner, prop, {
          configurable: true,
          get: function () { return value; },
          set: function () {}
        });
      } catch (e) {}
    });
  });

  var magic = String.fromCharCode(Date.now() % 26 + 97) + Math.floor(Math.random() * 982451653 + 982451653).toString(36);
  window.addEventListener('error', function (ev) {
    if (typeof ev.message === 'string' && ev.message.indexOf(magic) !== -1) ev.preventDefault();
  }, true);

  alias(['abort-on-property-read', 'aopr'], function (args) {
    if (!args[0]) return;
    trapChain(window, args[0], function (owner, prop) {
      try {
        defineProperty(owner, prop, {
          configurable: true,
          get: function () { throw new ReferenceError(magic); },
          set: function () {}
        });
      } catch (e) {}
    });
  });

  alias(['abort-on-property-write', 'aopw'], function (args) {
    if (!args[0]) return;
    trapChain(window, args[0], function (owner, prop) {
      try {
        defineProperty(owner, prop, {
          configurable: true,
          get: function () { return undefined; },
          set: function () { throw new ReferenceError(magic); }
        });
      } catch (e) {}
    });
  });

  alias(['abort-current-script', 'acs', 'abort-current-inline-script', 'acis'], function (args) {
    var chain = args[0];
    if (!chain) return;
    var matches = needleMatcher(args[1]);
    var thisScript = document.currentScript;
    trapChain(window, chain, function (owner, prop) {
      var desc = getOwnPropertyDescriptor(owner, prop);
      var value = owner[prop];
      function check() {
        var s = document.currentScript;
        if (!(s instanceof HTMLScriptElement) || s === thisScript) return;
        var text = s.src || s.textContent;
        if (!matches(text)) return;
        throw new ReferenceError(magic);
      }
      try {
        defineProperty(owner, prop, {
          configurable: true,
          get: function () {
            check();
            return desc && desc.get ? desc.get.call(this) : value;
          },
          set: function (v) {
            check();
            if (desc && desc.set) desc.set.call(this, v); else value = v;
          }
        });
      } catch (e) {}
    });
  });

  alias(['json-prune'], function (args) {
    var pruner = makePruner(args[0], args[1]);
    if (!pruner) return;
    JSON.parse = new Proxy(JSON.parse, {
      apply: function (target, thisArg, a) {
        var result = Reflect.apply(target, thisArg, a);
        try { pruner(result); } catch (e) {}
        return result;
      }
    });
    if (window.Response) {
      Response.prototype.json = new Proxy(Response.prototype.json, {
        apply: function (target, thisArg, a) {
          return Reflect.apply(target, thisArg, a).then(function (obj) {
            try { pruner(obj); } catch (e) {}
            return obj;
          });
        }
      });
    }
  });

  alias(['json-prune-fetch-response'], function (args) {
    var pruner = makePruner(args[0], args[1]);
    if (!pruner) return;
    var extra = extraArgs(args.slice(2));
    fetchTransforms.push({ match: propsMatcher(extra.propsToMatch), apply: jsonTextPruner(pruner) });
    installFetchHook();
  });

  alias(['json-prune-xhr-response'], function (args) {
    var pruner = makePruner(args[0], args[1]);
    if (!pruner) return;
    var extra = extraArgs(args.slice(2));
    xhrTransforms.push({ match: propsMatcher(extra.propsToMatch), apply: jsonTextPruner(pruner), applyObject: pruner });
    installXhrHook();
  });

  alias(['trusted-replace-fetch-response', 'trusted-rpfr'], function (args) {
    var replace = textReplacer(args[0], args[1]);
    if (!replace) return;
    fetchTransforms.push({ match: propsMatcher(args[2]), apply: replace });
    installFetchHook();
  });

  alias(['trusted-replace-xhr-response'], function (args) {
    var replace = textReplacer(args[0], args[1]);
    if (!replace) return;
    xhrTransforms.push({ match: propsMatcher(args[2]), apply: replace });
    installXhrHook();
  });

  alias(['no-fetch-if', 'prevent-fetch'], function (args) {
    if (!args[0]) return;
    var body = args[1] === 'emptyObj' ? '{}' : args[1] === 'emptyArr' ? '[]' : '';
    fetchTransforms.push({ match: propsMatcher(args[0]), block: true, body: body });
    installFetchHook();
  });

  alias(['no-xhr-if', 'prevent-xhr'], function (args) {
    if (!args[0]) return;
    xhrTransforms.push({ match: propsMatcher(args[0]), block: true, body: '' });
    installXhrHook();
  });

  function timerDefuser(name, timerName) {
    alias(name, function (args) {
      var matches = needleMatcher(args[0]);
      var delayRaw = args[1] || '';
      var delayNegate = delayRaw.charAt(0) === '!';
      var delay = delayRaw ? parseInt(delayNegate ? delayRaw.slice(1) : delayRaw, 10) : NaN;
      window[timerName] = new Proxy(window[timerName], {
        apply: function (target, thisArg, a) {
          var fn = a[0];
          var text = typeof fn === 'function' ? Function.prototype.toString.call(fn) : String(fn);
          var delayOk = isNaN(delay) || ((a[1] === delay) !== delayNegate);
          if (matches(text) && delayOk) a[0] = noop;
          return Reflect.apply(target, thisArg, a);
        }
      });
    });
  }
  timerDefuser(['no-setTimeout-if', 'nostif', 'setTimeout-defuser', 'prevent-setTimeout'], 'setTimeout');
  timerDefuser(['no-setInterval-if', 'nosiif', 'setInterval-defuser', 'prevent-setInterval'], 'setInterval');

  function timerBooster(name, timerName) {
    alias(name, function (args) {
      var matches = needleMatcher(args[0]);
      var delay = args[1] === '*' ? null : parseInt(args[1] || '1000', 10);
      var boost = parseFloat(args[2] || '0.05');
      if (!isFinite(boost) || boost < 0.001 || boost > 50) boost = 0.05;
      window[timerName] = new Proxy(window[timerName], {
        apply: function (target, thisArg, a) {
          var fn = a[0];
          var text = typeof fn === 'function' ? Function.prototype.toString.call(fn) : String(fn);
          if ((delay === null || a[1] === delay) && matches(text)) a[1] = (a[1] || 0) * boost;
          return Reflect.apply(target, thisArg, a);
        }
      });
    });
  }
  timerBooster(['nano-setTimeout-booster', 'nano-stb', 'adjust-setTimeout'], 'setTimeout');
  timerBooster(['nano-setInterval-booster', 'nano-sib', 'adjust-setInterval'], 'setInterval');

  alias(['prevent-addEventListener', 'aeld', 'addEventListener-defuser'], function (args) {
    var typeMatches = needleMatcher(args[0]);
    var handlerMatches = needleMatcher(args[1]);
    EventTarget.prototype.addEventListener = new Proxy(EventTarget.prototype.addEventListener, {
      apply: function (target, thisArg, a) {
        var handler = a[1];
        var text = '';
        try {
          text = typeof handler === 'function' ? Function.prototype.toString.call(handler) : String(handler);
        } catch (e) {}
        if (typeMatches(String(a[0])) && handlerMatches(text)) return undefined;
        return Reflect.apply(target, thisArg, a);
      }
    });
  });

  alias(['no-window-open-if', 'nowoif', 'window.open-defuser', 'prevent-window-open'], function (args) {
    var matches = needleMatcher(args[0]);
    window.open = new Proxy(window.open, {
      apply: function (target, thisArg, a) {
        if (!matches(String(a[0] || ''))) return Reflect.apply(target, thisArg, a);
        return null;
      }
    });
  });

  alias(['noeval-if', 'prevent-eval-if', 'noeval'], function (args) {
    var matches = needleMatcher(args[0]);
    window.eval = new Proxy(window.eval, {
      apply: function (target, thisArg, a) {
        if (matches(String(a[0]))) return undefined;
        return Reflect.apply(target, thisArg, a);
      }
    });
  });

  alias(['window.name-defuser'], function () {
    if (window === window.top) window.name = '';
  });

  function storageSetter(storageName) {
    return function (args) {
      var key = args[0];
      if (!key) return;
      var raw = args[1];
      var value = raw === '$remove$' ? null : constantValue(raw, false);
      if (value === INVALID || typeof value === 'function') return;
      try {
        var storage = window[storageName];
        if (raw === '$remove$') storage.removeItem(key);
        else storage.setItem(key, value === undefined ? 'undefined' : String(value === null ? 'null' : value));
      } catch (e) {}
    };
  }
  alias(['set-local-storage-item'], storageSetter('localStorage'));
  alias(['set-session-storage-item'], storageSetter('sessionStorage'));

  alias(['set-cookie'], function (args) {
    var name = args[0];
    var value = constantValue(args[1] || '', false);
    if (!name || value === INVALID || typeof value === 'function' || typeof value === 'object') return;
    try {
      document.cookie = encodeURIComponent(name) + '=' + encodeURIComponent(String(value)) + '; path=' + (args[2] === 'none' ? '' : '/');
    } catch (e) {}
  });

  // Modifiche al DOM ripetute man mano che la pagina cambia.
  var domTasks = [];
  function addDomTask(fn) {
    domTasks.push(fn);
    startDomTasks();
  }
  function startDomTasks() {
    if (startDomTasks.started) return;
    startDomTasks.started = true;
    var timer = null;
    var run = function () {
      timer = null;
      domTasks.forEach(function (fn) { try { fn(); } catch (e) {} });
    };
    onReady(function () {
      run();
      new MutationObs(function () {
        if (timer === null) timer = nativeSetTimeout(run, 100);
      }).observe(document.documentElement, { childList: true, subtree: true });
    });
  }

  alias(['remove-attr', 'ra'], function (args) {
    var attrs = (args[0] || '').split(/\s*\|\s*/).filter(Boolean);
    if (attrs.length === 0) return;
    var selector = args[1] || attrs.map(function (a) { return '[' + a + ']'; }).join(',');
    addDomTask(function () {
      document.querySelectorAll(selector).forEach(function (el) {
        attrs.forEach(function (a) { el.removeAttribute(a); });
      });
    });
  });

  alias(['remove-class', 'rc'], function (args) {
    var classes = (args[0] || '').split(/\s*\|\s*/).filter(Boolean);
    if (classes.length === 0) return;
    var selector = args[1] || classes.map(function (c) { return '.' + CSS.escape(c); }).join(',');
    addDomTask(function () {
      document.querySelectorAll(selector).forEach(function (el) {
        classes.forEach(function (c) { el.classList.remove(c); });
      });
    });
  });

  alias(['set-attr'], function (args) {
    var selector = args[0];
    var attr = args[1];
    var value = args[2] || '';
    if (!selector || !attr || /^on/i.test(attr)) return;
    if (!/^(?:''|""|true|false|-?\d+)$/.test(value) && value !== '') return;
    value = value === "''" || value === '""' ? '' : value;
    addDomTask(function () {
      document.querySelectorAll(selector).forEach(function (el) { el.setAttribute(attr, value); });
    });
  });

  /** Interviene sui nodi (di solito <script>) prima che il browser li esegua. */
  function watchNodes(nodeName, onNode) {
    var name = nodeName.toUpperCase();
    var check = function (node) {
      if (node.nodeName === name) onNode(node);
    };
    new MutationObs(function (records) {
      records.forEach(function (r) { r.addedNodes.forEach(check); });
    }).observe(document, { childList: true, subtree: true });
    document.querySelectorAll(nodeName).forEach(check);
    onReady(function () {
      nativeSetTimeout(function () { document.querySelectorAll(nodeName).forEach(check); }, 0);
    });
  }

  alias(['remove-node-text', 'rmnt'], function (args) {
    if (!args[0]) return;
    var matches = needleMatcher(args[1]);
    watchNodes(args[0], function (node) {
      if (node.textContent && matches(node.textContent)) node.textContent = '';
    });
  });

  alias(['trusted-replace-node-text', 'trusted-rpnt', 'replace-node-text', 'rpnt'], function (args) {
    if (!args[0] || !args[1]) return;
    var re = toRegex(args[1]);
    if (!re) return;
    var extra = extraArgs(args.slice(3));
    var condition = extra.condition ? toRegex(extra.condition) : null;
    var count = extra.sedCount ? parseInt(extra.sedCount, 10) : 0;
    var done = 0;
    watchNodes(args[0], function (node) {
      if (count && done >= count) return;
      var text = node.textContent;
      if (!text || !re.test(text)) return;
      if (condition && !condition.test(text)) return;
      re.lastIndex = 0;
      node.textContent = text.replace(re, args[2] || '');
      done++;
    });
  });

  // Ultima difesa dell'app su YouTube: lo spot che parte comunque si salta o si porta alla fine, senza audio.
  alias(['gd-youtube-skip'], function () {
    var mutedByUs = false;
    var tick = function () {
      var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-skip-ad-button, button[class*="skip-ad"]');
      if (skip) skip.click();
      var ad = document.querySelector('.ad-showing, .ad-interrupting');
      var video = document.querySelector('video');
      if (video) {
        if (ad) {
          if (!video.muted) { video.muted = true; mutedByUs = true; }
          if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.1) {
            video.currentTime = video.duration;
          }
        } else if (mutedByUs) {
          video.muted = false;
          mutedByUs = false;
        }
      }
      nativeSetTimeout(tick, 300);
    };
    nativeSetTimeout(tick, 300);
  });

  (data.js || []).forEach(function (entry) {
    var fn = scriptlets[entry[0]];
    if (!fn) return;
    try {
      fn(entry.slice(1), entry[0]);
    } catch (e) {}
  });

  /* ------------------------------------------------------------- cosmetico */

  var style = null;
  var pendingRules = [];

  function ensureStyle() {
    if (style && style.isConnected) return style;
    var root = document.head || document.documentElement;
    if (!root) return null;
    if (!style) {
      style = document.createElement('style');
      style.setAttribute('data-gd', '');
    }
    root.appendChild(style);
    return style;
  }

  /** Aggiunge le regole una per una: un selettore che il browser non capisce non annulla gli altri. */
  function addRules(rules) {
    if (rules.length === 0) return;
    pendingRules = pendingRules.concat(rules);
    flushRules();
  }

  function flushRules() {
    var el = ensureStyle();
    if (!el || !el.sheet) {
      nativeSetTimeout(flushRules, 20);
      return;
    }
    var sheet = el.sheet;
    var rules = pendingRules;
    pendingRules = [];
    for (var i = 0; i < rules.length; i += 50) {
      var chunk = rules.slice(i, i + 50);
      var hide = chunk.filter(function (r) { return r.css === undefined; }).map(function (r) { return r.sel; });
      try {
        if (hide.length) sheet.insertRule(hide.join(',\n') + '{display:none!important}', sheet.cssRules.length);
      } catch (e) {
        hide.forEach(function (sel) {
          try { sheet.insertRule(sel + '{display:none!important}', sheet.cssRules.length); } catch (e2) {}
        });
      }
      chunk.forEach(function (r) {
        if (r.css === undefined) return;
        try { sheet.insertRule(r.sel + '{' + r.css + '}', sheet.cssRules.length); } catch (e) {}
      });
    }
  }

  function hideRules(selectors) {
    return selectors.map(function (s) { return { sel: s }; });
  }

  addRules(hideRules(data.hide || []).concat(
    (data.styles || []).map(function (p) { return { sel: p[0], css: p[1] }; }),
    hideRules(data.high || [])
  ));
  // Se il sito ricostruisce <head> il foglio di stile va rimesso.
  onReady(function () {
    ensureStyle();
    nativeSetTimeout(ensureStyle, 1000);
  });

  // Filtri generici: si chiedono all'app solo per le classi e gli id che compaiono davvero.
  if (data.generic && typeof bridge.generic === 'function') {
    var seen = Object.create(null);
    var queue = [];
    var timer = null;
    var add = function (key) {
      if (seen[key]) return;
      seen[key] = true;
      queue.push(key);
    };
    var collect = function (el) {
      if (el.nodeType !== 1) return;
      if (el.id) add('#' + el.id);
      var cls = el.getAttribute('class');
      if (cls) cls.split(/\s+/).forEach(function (c) { if (c) add('.' + c); });
    };
    var scan = function (root) {
      collect(root);
      if (root.querySelectorAll) root.querySelectorAll('[id],[class]').forEach(collect);
    };
    var flush = function () {
      timer = null;
      if (queue.length === 0) return;
      var keys = queue;
      queue = [];
      try {
        var selectors = jsonParse(bridge.generic(location.hostname, keys.join('\n')));
        if (selectors.length) addRules(hideRules(selectors));
      } catch (e) {}
    };
    var schedule = function () {
      if (timer === null) timer = nativeSetTimeout(flush, 50);
    };
    new MutationObs(function (records) {
      records.forEach(function (r) {
        if (r.type === 'attributes') collect(r.target);
        else r.addedNodes.forEach(function (n) { if (n.nodeType === 1) scan(n); });
      });
      if (queue.length) schedule();
    }).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['id', 'class'] });
    onReady(function () {
      scan(document.documentElement);
      flush();
    });
  }

  /* ---------------------------------------------------- filtri procedurali */

  var proceduralOps = {
    'has-text': 1, '-abp-contains': 1, 'contains': 1, 'upward': 1, 'nth-ancestor': 1, 'xpath': 1,
    'matches-css': 1, 'matches-css-before': 1, 'matches-css-after': 1, 'min-text-length': 1,
    '-abp-has': 1, 'if': 1, 'if-not': 1, 'matches-path': 1, 'remove': 1, 'style': 1, 'watch-attr': 1,
    'has': 1, 'not': 1
  };

  /** Divide "div.post:has-text(Sponsor):upward(2)" in selettore CSS e operazioni. */
  function parseProcedural(text) {
    var ops = [];
    var base = '';
    var i = 0;
    var depth = 0;
    var quote = '';
    var css = '';
    while (i < text.length) {
      var c = text.charAt(i);
      if (quote) {
        css += c;
        if (c === '\\') { css += text.charAt(i + 1); i += 2; continue; }
        if (c === quote) quote = '';
        i++;
        continue;
      }
      if (c === '"' || c === "'") { quote = c; css += c; i++; continue; }
      if (c === '[' || c === '(') depth++;
      if (c === ']' || c === ')') depth--;
      if (c === ':' && depth === 0) {
        var m = /^:([-a-z]+)\(/.exec(text.slice(i));
        if (m && proceduralOps[m[1]]) {
          var start = i + m[0].length;
          var level = 1;
          var j = start;
          var q = '';
          while (j < text.length && level > 0) {
            var cj = text.charAt(j);
            if (q) { if (cj === '\\') j++; else if (cj === q) q = ''; }
            else if (cj === '"' || cj === "'") q = cj;
            else if (cj === '(') level++;
            else if (cj === ')') level--;
            j++;
          }
          if (level !== 0) return null;
          var arg = text.slice(start, j - 1);
          var name = m[1];
          // :has e :not restano CSS se dentro non c'è niente di procedurale.
          if ((name === 'has' || name === 'not') && !/:(?:-abp-|has-text|contains|upward|xpath|matches-|min-text|if)/.test(arg)) {
            css += text.slice(i, j);
            i = j;
            continue;
          }
          if (ops.length === 0) base = css;
          else if (css.trim()) ops.push(['css', css]);
          css = '';
          ops.push([name, arg]);
          i = j;
          continue;
        }
      }
      css += c;
      i++;
    }
    if (ops.length === 0) return { base: css, ops: [] };
    if (css.trim()) ops.push(['css', css]);
    return { base: base, ops: ops };
  }

  function textMatcher(arg) {
    var re = toRegex(arg);
    return function (s) { return re ? re.test(s) : false; };
  }

  function cssPropMatcher(arg) {
    var m = /^\s*([-a-z]+)\s*:\s*(.+?)\s*$/.exec(arg);
    if (!m) return null;
    var prop = m[1];
    var re = toRegex(m[2].charAt(0) === '/' ? m[2] : '/^' + m[2].replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$/');
    return function (el, pseudo) {
      var v = getComputedStyle(el, pseudo || null).getPropertyValue(prop);
      return re ? re.test(v) : false;
    };
  }

  function compileProcedural(text) {
    var parsed = parseProcedural(text);
    if (!parsed) return null;
    var action = 'hide';
    var styleDecl = null;
    var steps = [];
    for (var k = 0; k < parsed.ops.length; k++) {
      var name = parsed.ops[k][0];
      var arg = parsed.ops[k][1];
      var step = null;
      switch (name) {
        case 'css': step = (function (sel) {
          // Il resto del selettore dopo un'operazione: " > div", " .classe", "+ p", "~ p" o ".classe".
          var rel = sel.trim();
          var first = rel.charAt(0);
          var sibling = first === '+' || first === '~';
          var rest = sibling ? rel.slice(1).trim() : rel;
          var descendant = first === '>' || /^\s/.test(sel);
          return function (els) {
            var out = [];
            var push = function (x) { if (out.indexOf(x) === -1) out.push(x); };
            els.forEach(function (el) {
              try {
                if (sibling) {
                  for (var n = el.nextElementSibling; n; n = n.nextElementSibling) {
                    if (n.matches(rest)) push(n);
                    if (first === '+') break;
                  }
                } else if (descendant) {
                  el.querySelectorAll(':scope ' + rel).forEach(push);
                } else if (el.matches(rel)) push(el);
              } catch (e) {}
            });
            return out;
          };
        })(arg); break;
        case 'has-text': case '-abp-contains': case 'contains': step = (function (m) {
          return function (els) { return els.filter(function (el) { return m(el.textContent || ''); }); };
        })(textMatcher(arg)); break;
        case 'min-text-length': step = (function (n) {
          return function (els) { return els.filter(function (el) { return (el.textContent || '').length >= n; }); };
        })(parseInt(arg, 10) || 0); break;
        case 'upward': case 'nth-ancestor': step = (function (a) {
          var n = /^\d+$/.test(a) ? parseInt(a, 10) : 0;
          return function (els) {
            var out = [];
            els.forEach(function (el) {
              var p = el;
              if (n > 0) {
                for (var x = 0; x < n && p; x++) p = p.parentElement;
              } else {
                try { p = el.parentElement && el.parentElement.closest(a); } catch (e) { p = null; }
              }
              if (p && out.indexOf(p) === -1) out.push(p);
            });
            return out;
          };
        })(arg); break;
        case 'xpath': step = (function (expr) {
          return function (els) {
            var out = [];
            els.forEach(function (el) {
              try {
                var r = document.evaluate(expr, el, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                for (var x = 0; x < r.snapshotLength; x++) {
                  var node = r.snapshotItem(x);
                  if (node.nodeType === 1 && out.indexOf(node) === -1) out.push(node);
                }
              } catch (e) {}
            });
            return out;
          };
        })(arg); break;
        case 'matches-css': case 'matches-css-before': case 'matches-css-after': step = (function (m, pseudo) {
          if (!m) return null;
          return function (els) { return els.filter(function (el) { return m(el, pseudo); }); };
        })(cssPropMatcher(arg), name === 'matches-css' ? null : name === 'matches-css-before' ? '::before' : '::after'); break;
        case 'has': case '-abp-has': case 'if': case 'not': case 'if-not': step = (function (sub, negate) {
          if (!sub) return null;
          return function (els) {
            return els.filter(function (el) { return (runProcedural(sub, el).length > 0) !== negate; });
          };
        })(compileProcedural(/^\s*[>+~]/.test(arg) ? ':scope ' + arg : arg), name === 'not' || name === 'if-not'); break;
        case 'matches-path': step = (function (m) {
          return function (els) { return m(location.pathname + location.search) ? els : []; };
        })(textMatcher(arg)); break;
        case 'watch-attr': step = function (els) { return els; }; break;
        case 'remove': action = 'remove'; step = function (els) { return els; }; break;
        case 'style': action = 'style'; styleDecl = arg; step = function (els) { return els; }; break;
      }
      if (!step) return null;
      steps.push(step);
    }
    return { base: parsed.base.trim(), steps: steps, action: action, style: styleDecl };
  }

  function runProcedural(compiled, root) {
    var els;
    try {
      els = compiled.base ? Array.prototype.slice.call((root || document).querySelectorAll(compiled.base)) : [root || document.documentElement];
    } catch (e) {
      return [];
    }
    for (var i = 0; i < compiled.steps.length && els.length; i++) els = compiled.steps[i](els);
    return els;
  }

  var procedural = (data.proc || []).map(compileProcedural).filter(Boolean);
  if (procedural.length) {
    var procTimer = null;
    var applyProcedural = function () {
      procTimer = null;
      procedural.forEach(function (p) {
        runProcedural(p, document).forEach(function (el) {
          if (p.action === 'remove') el.remove();
          else if (p.action === 'style') el.setAttribute('style', (el.getAttribute('style') || '') + ';' + p.style);
          else el.style.setProperty('display', 'none', 'important');
        });
      });
    };
    onReady(function () {
      applyProcedural();
      new MutationObs(function () {
        if (procTimer === null) procTimer = nativeSetTimeout(applyProcedural, 150);
      }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
    });
  }

})();

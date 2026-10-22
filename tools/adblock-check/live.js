// Prova del blocco pubblicità su pagine vere, con Chromium (lo stesso motore della WebView di Android).
// Ogni pagina si apre due volte: senza blocco e con il blocco come nell'app (stesso motore dei filtri,
// tramite il server locale di Server.kt, e stesso content.js). Scrive report.md e le foto in <uscita>.
//
// Uso: node live.js http://127.0.0.1:8790 <uscita>
const fs = require('fs');
const path = require('path');
const { chromium, devices } = require('playwright');

const server = process.argv[2] || 'http://127.0.0.1:8790';
const out = process.argv[3] || 'live-out';
const contentJs = fs.readFileSync(path.join(__dirname, '../../app/src/main/assets/adblock/content.js'), 'utf8');
fs.mkdirSync(out, { recursive: true });

const FEEDS = {
  ANSA: 'https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml',
  Fanpage: 'https://www.fanpage.it/feed/',
  Open: 'https://www.open.online/feed/',
  RaiNews: 'https://www.rainews.it/rss/tutti',
  AGI: 'https://www.agi.it/rss',
  Internazionale: 'https://www.internazionale.it/sitemaps/rss.xml',
};

async function firstArticle(feed) {
  try {
    const res = await fetch(feed, { headers: { 'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36' } });
    const xml = await res.text();
    const item = xml.split(/<item[\s>]/)[1] || '';
    const m = /<link>\s*(?:<!\[CDATA\[)?\s*([^<\]\s]+)/.exec(item);
    return m ? m[1].trim() : null;
  } catch (e) {
    return null;
  }
}

// Come AdBlocker.typeFrom nell'app: la WebView non dice il tipo della richiesta.
function typeFrom(accept, url) {
  let p = '';
  try { p = new URL(url).pathname.toLowerCase(); } catch (e) {}
  const last = p.split('/').pop();
  const ext = last.includes('.') ? last.split('.').pop() : '';
  if (accept.startsWith('text/html')) return 'SUBDOCUMENT';
  if (accept.startsWith('text/css')) return 'STYLESHEET';
  if (accept.startsWith('image/')) return 'IMAGE';
  if (ext === 'js' || ext === 'mjs') return 'SCRIPT';
  if (ext === 'css') return 'STYLESHEET';
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'ico', 'avif', 'bmp'].includes(ext)) return 'IMAGE';
  if (['woff', 'woff2', 'ttf', 'otf', 'eot'].includes(ext)) return 'FONT';
  if (['mp4', 'webm', 'm3u8', 'mp3', 'm4a', 'aac', 'ogg', 'mpd', 'm4s', 'ts'].includes(ext)) return 'MEDIA';
  if (accept.includes('application/json')) return 'XHR';
  return 'UNKNOWN';
}

async function ask(pathAndQuery, body) {
  const res = await fetch(server + pathAndQuery, body === undefined ? {} : { method: 'POST', body });
  return res.text();
}

// Il ponte dell'app (GDBridge) imitato con richieste sincrone intercettate da Playwright.
const bridgeScript = `
  (function () {
    function call(path, body) {
      var x = new XMLHttpRequest();
      x.open(body === undefined ? 'GET' : 'POST', 'https://gd-bridge.invalid' + path, false);
      x.send(body === undefined ? null : body);
      return x.responseText;
    }
    window.GDBridge = {
      page: function (url) { return call('/page?url=' + encodeURIComponent(url)); },
      generic: function (host, keys) { return call('/generic?host=' + encodeURIComponent(host), keys); }
    };
  })();
`;

async function visit(browser, site, blocking) {
  const context = await browser.newContext({ ...devices['Pixel 7'], locale: 'it-IT', bypassCSP: true });
  const page = await context.newPage();
  const stats = { requests: 0, blocked: 0, wouldBlock: 0, blockedHosts: {}, errors: [] };
  page.on('pageerror', (e) => stats.errors.push(String(e).slice(0, 200)));

  await context.route('**/*', async (route) => {
    const req = route.request();
    const url = req.url();
    if (url.startsWith('https://gd-bridge.invalid')) {
      const u = new URL(url);
      const body = await ask(u.pathname + u.search, req.method() === 'POST' ? req.postData() || '' : undefined);
      return route.fulfill({ status: 200, contentType: 'application/json', body });
    }
    stats.requests++;
    const isMain = req.isNavigationRequest() && req.frame() === page.mainFrame();
    if (isMain || !url.startsWith('http')) return route.continue();
    const pageUrl = page.mainFrame().url();
    const type = typeFrom((req.headers()['accept'] || ''), url);
    const verdict = await ask(`/match?url=${encodeURIComponent(url)}&page=${encodeURIComponent(pageUrl)}&type=${type}`);
    if (verdict !== 'BLOCK') return route.continue();
    stats.wouldBlock++;
    const host = new URL(url).hostname;
    stats.blockedHosts[host] = (stats.blockedHosts[host] || 0) + 1;
    if (!blocking) return route.continue();
    stats.blocked++;
    return route.fulfill({ status: 200, body: '', contentType: type === 'SCRIPT' ? 'application/javascript' : type === 'STYLESHEET' ? 'text/css' : 'text/plain' });
  });
  if (blocking) {
    await context.addInitScript(bridgeScript);
    await context.addInitScript(contentJs);
  }

  let ok = true;
  try {
    await page.goto(site.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
    // Consenso ai cookie (Google, CMP dei giornali): si accetta per vedere la pagina com'è davvero.
    await page.waitForTimeout(3000);
    for (const label of ['Accetta tutto', 'Accetta', 'Accept all', 'ACCETTA', 'Accetto', 'OK']) {
      const b = page.getByRole('button', { name: label, exact: false }).first();
      if (await b.isVisible().catch(() => false)) { await b.click().catch(() => {}); break; }
    }
    await page.waitForTimeout(site.youtube ? 15000 : 8000);
  } catch (e) {
    ok = false;
    stats.errors.push('caricamento: ' + String(e).slice(0, 120));
  }

  const info = await page.evaluate((isYouTube) => {
    const visible = (el) => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 20 && r.height > 20 && s.display !== 'none' && s.visibility !== 'hidden'; };
    const adFrames = [...document.querySelectorAll('iframe')].filter((f) => visible(f) && /doubleclick|googlesyndication|adnxs|taboola|outbrain|teads|criteo|amazon-adsystem|smartadserver|seedtag/i.test(f.src || f.id || f.name || ''));
    const adBoxes = [...document.querySelectorAll('[id*="adv" i],[class*="adv-" i],[id^="div-gpt-ad"],[class*="banner" i],ins.adsbygoogle,[id*="taboola" i],[id*="outbrain" i]')].filter(visible);
    const r = { title: document.title, textLength: (document.body && document.body.innerText || '').length, adFrames: adFrames.length, adBoxes: adBoxes.length };
    if (isYouTube) {
      const pr = window.ytInitialPlayerResponse;
      r.playerResponse = !!pr;
      r.adPlacements = !!(pr && pr.adPlacements);
      r.playerAds = !!(pr && pr.playerAds);
      r.adShowing = !!document.querySelector('.ad-showing, .ad-interrupting');
      const v = document.querySelector('video');
      r.video = v ? { time: Math.round(v.currentTime), duration: Math.round(v.duration || 0), paused: v.paused } : null;
      r.adRenderers = [...document.querySelectorAll('ad-slot-renderer, ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, ytd-ad-slot-renderer')].filter(visible).length;
    }
    return r;
  }, !!site.youtube).catch((e) => ({ error: String(e) }));

  const shot = `${site.name.replace(/\W+/g, '_')}-${blocking ? 'blocco' : 'senza'}.png`;
  await page.screenshot({ path: path.join(out, shot) }).catch(() => {});
  await context.close();
  return { ok, stats, info, shot };
}

(async () => {
  const sites = [
    { name: 'YouTube home', url: 'https://m.youtube.com/', youtube: true },
    { name: 'YouTube video', url: 'https://m.youtube.com/watch?v=kJQP7kiw5Fk', youtube: true },
    { name: 'YouTube video 2', url: 'https://m.youtube.com/watch?v=JGwWNGJdvx8', youtube: true },
  ];
  if (process.env.SITES) sites.splice(0, sites.length, ...JSON.parse(process.env.SITES));
  else for (const [name, feed] of Object.entries(FEEDS)) {
    sites.push({ name: `${name} home`, url: new URL(feed).origin + '/' });
    const article = await firstArticle(feed);
    if (article) sites.push({ name: `${name} articolo`, url: article });
  }

  const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] });
  const rows = [];
  for (const site of sites) {
    const before = await visit(browser, site, false);
    const after = await visit(browser, site, true);
    rows.push({ site, before, after });
    console.log(site.name, JSON.stringify({ before: before.info, after: after.info, blocked: after.stats.blocked, requests: [before.stats.requests, after.stats.requests] }));
  }
  await browser.close();

  let md = '# Verifica dal vivo del blocco pubblicità\n\n';
  md += '| Pagina | Richieste senza → con blocco | Bloccate | Riquadri pubblicitari visibili (senza → con) | Testo (caratteri, senza → con) | Errori JS con blocco |\n|---|---|---|---|---|---|\n';
  for (const { site, before, after } of rows) {
    md += `| [${site.name}](${site.url}) | ${before.stats.requests} → ${after.stats.requests} | ${after.stats.blocked} | ` +
      `${(before.info.adFrames || 0) + (before.info.adBoxes || 0)} → ${(after.info.adFrames || 0) + (after.info.adBoxes || 0)} | ` +
      `${before.info.textLength || 0} → ${after.info.textLength || 0} | ${after.stats.errors.length} |\n`;
  }
  md += '\n## YouTube\n\n';
  for (const { site, before, after } of rows.filter((r) => r.site.youtube)) {
    md += `- **${site.name}**: senza blocco ${JSON.stringify(before.info)}; con blocco ${JSON.stringify(after.info)}\n`;
  }
  md += '\n## Domini bloccati più frequenti\n\n';
  const hosts = {};
  rows.forEach(({ after }) => Object.entries(after.stats.blockedHosts).forEach(([h, n]) => { hosts[h] = (hosts[h] || 0) + n; }));
  Object.entries(hosts).sort((a, b) => b[1] - a[1]).slice(0, 40).forEach(([h, n]) => { md += `- ${h}: ${n}\n`; });
  md += '\n## Errori JavaScript con il blocco\n\n';
  rows.forEach(({ site, after }) => after.stats.errors.slice(0, 5).forEach((e) => { md += `- ${site.name}: \`${e.replace(/`/g, "'")}\`\n`; }));
  md += '\n## Foto\n\n';
  rows.forEach(({ site, before, after }) => { md += `### ${site.name}\n\n| Senza blocco | Con blocco |\n|---|---|\n| ![](${before.shot}) | ![](${after.shot}) |\n\n`; });
  fs.writeFileSync(path.join(out, 'report.md'), md);
  fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(rows, null, 1));
  console.log('Fatto:', path.join(out, 'report.md'));
})();

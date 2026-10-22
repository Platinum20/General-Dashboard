// Apre la pagina di accesso di Spotify come la WebView dell'app e scrive cosa si vede.
const { chromium } = require('playwright');
const MOBILE = 'Mozilla/5.0 (Linux; Android 15; SM-S921B Build/AP3A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.7258.94 Mobile Safari/537.36';
const WV = 'Mozilla/5.0 (Linux; Android 15; SM-S921B Build/AP3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/139.0.7258.94 Mobile Safari/537.36';
const urls = {
  login: 'https://accounts.spotify.com/it/login?continue=' + encodeURIComponent('https://open.spotify.com/'),
  authorize: 'https://accounts.spotify.com/authorize?client_id=802a1bc467c041538598820b98e20f63&response_type=code&redirect_uri=' +
    encodeURIComponent('com.platinum20.dashboard://spotify-callback') + '&code_challenge_method=S256&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&scope=streaming',
};
const variants = [
  ['mobile', MOBILE, {}],
  ['mobile+xrw', MOBILE, { 'X-Requested-With': 'com.platinum20.dashboard' }],
  ['wv+xrw', WV, { 'X-Requested-With': 'com.platinum20.dashboard' }],
];
(async () => {
  const browser = await chromium.launch();
  for (const [page, url] of Object.entries(urls)) for (const [name, ua, headers] of variants) {
    const ctx = await browser.newContext({ userAgent: ua, viewport: { width: 411, height: 860 }, isMobile: true, extraHTTPHeaders: headers });
    const p = await ctx.newPage();
    const log = [];
    p.on('pageerror', e => log.push('pageerror ' + e.message.slice(0, 200)));
    p.on('console', m => m.type() === 'error' && log.push('console ' + m.text().slice(0, 200)));
    p.on('response', r => r.status() >= 400 && log.push(r.status() + ' ' + r.url().slice(0, 140)));
    p.on('requestfailed', r => log.push('failed ' + r.url().slice(0, 140) + ' ' + r.failure()?.errorText));
    try { await p.goto(url, { waitUntil: 'networkidle', timeout: 45000 }); } catch (e) { log.push('goto ' + e.message.split('\n')[0]); }
    await p.waitForTimeout(3000);
    const text = (await p.innerText('body').catch(() => '')).replace(/\s+/g, ' ').slice(0, 300);
    console.log(`\n=== ${page} / ${name} → ${p.url().slice(0, 100)}\n${text || '(pagina vuota)'}\n${log.slice(0, 15).join('\n')}`);
    await p.screenshot({ path: `spotify-${page}-${name}.png` });
    await ctx.close();
  }
  await browser.close();
})();

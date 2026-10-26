# Verifica dal vivo del blocco pubblicità

| Pagina | Richieste senza → con blocco | Bloccate | Riquadri pubblicitari visibili (senza → con) | Testo (caratteri, senza → con) | Errori JS con blocco |
|---|---|---|---|---|---|
| [YouTube home](https://m.youtube.com/) | 60 → 66 | 7 | 0 → 0 | 126 → 126 | 0 |
| [YouTube video](https://m.youtube.com/watch?v=kJQP7kiw5Fk) | 73 → 76 | 6 | 0 → 0 | 1722 → 1592 | 0 |
| [YouTube video 2](https://m.youtube.com/watch?v=JGwWNGJdvx8) | 73 → 75 | 6 | 0 → 0 | 1687 → 1488 | 0 |
| [ANSA home](https://www.ansa.it/) | 124 → 84 | 9 | 1 → 1 | 26335 → 26335 | 0 |
| [ANSA articolo](https://www.ansa.it/sito/notizie/topnews/2026/10/26/borsa-milano-chiude-in-rialzo-dell129_9a40b5a1-978a-4272-bd33-a136a1f44bb4.html) | 2641 → 53 | 7 | 1 → 1 | 6689 → 6689 | 0 |
| [Fanpage home](https://www.fanpage.it/) | 1 → 1 | 0 | 0 → 0 | 192 → 192 | 0 |
| [Fanpage articolo](https://www.fanpage.it/sport/motori/forse-dovremmo-usare-il-linguaggio-dei-segni-verstappen-sarcastico-nei-confronti-della-red-bull/) | 1 → 1 | 0 | 0 → 0 | 301 → 305 | 0 |
| [Open home](https://www.open.online/) | 372 → 262 | 13 | 16 → 0 | 20827 → 20819 | 1 |
| [Open articolo](https://www.open.online/2026/10/26/papa-leone-viaggio-sudamerica-medici/) | 200 → 64 | 13 | 20 → 3 | 6473 → 5549 | 2 |
| [RaiNews home](https://www.rainews.it/) | 1 → 1 | 0 | 0 → 0 | 196 → 196 | 0 |
| [RaiNews articolo](https://www.rainews.it/articoli/2026/10/mantovano-sui-cortei-molti-giovani-prima-di-darsi-a-devastazioni-assumono-droghe-3757156b-7bcb-49dc-ba52-baba88c096cc.html) | 1 → 1 | 0 | 0 → 0 | 335 → 335 | 0 |
| [AGI home](https://www.agi.it/) | 176 → 113 | 5 | 3 → 0 | 15144 → 14526 | 0 |
| [AGI articolo](https://www.agi.it/salute/news/2026-10-26/tumore-endometrio-avanzato-trofuse-39719394/) | 196 → 98 | 6 | 12 → 0 | 10453 → 8464 | 1 |
| [Internazionale home](https://www.internazionale.it/) | 191 → 152 | 9 | 16 → 1 | 11180 → 11136 | 0 |
| [Internazionale articolo](https://www.internazionale.it/notizie/junko-terao/2026/10/26/australia-copyright) | 153 → 102 | 9 | 9 → 3 | 1366 → 991 | 0 |

## YouTube

- **YouTube home**: senza blocco {"title":"YouTube","textLength":126,"adFrames":0,"adBoxes":0,"playerResponse":false,"adPlacements":false,"playerAds":false,"adShowing":false,"video":{"time":0,"duration":0,"paused":true},"adRenderers":0}; con blocco {"title":"YouTube","textLength":126,"adFrames":0,"adBoxes":0,"playerResponse":false,"adPlacements":false,"playerAds":false,"adShowing":false,"video":{"time":0,"duration":0,"paused":true},"adRenderers":0}
- **YouTube video**: senza blocco {"title":"YouTube","textLength":1722,"adFrames":0,"adBoxes":0,"playerResponse":true,"adPlacements":false,"playerAds":false,"adShowing":false,"video":{"time":0,"duration":0,"paused":true},"adRenderers":0}; con blocco {"title":"Luis Fonsi - Despacito ft. Daddy Yankee - YouTube","textLength":1592,"adFrames":0,"adBoxes":0,"playerResponse":true,"adPlacements":false,"playerAds":false,"adShowing":false,"video":{"time":0,"duration":0,"paused":true},"adRenderers":0}
- **YouTube video 2**: senza blocco {"title":"Ed Sheeran - Shape of You (Official Music Video) - YouTube","textLength":1687,"adFrames":0,"adBoxes":0,"playerResponse":true,"adPlacements":false,"playerAds":false,"adShowing":false,"video":{"time":0,"duration":0,"paused":true},"adRenderers":0}; con blocco {"title":"Ed Sheeran - Shape of You (Official Music Video) - YouTube","textLength":1488,"adFrames":0,"adBoxes":0,"playerResponse":true,"adPlacements":false,"playerAds":false,"adShowing":false,"video":{"time":0,"duration":0,"paused":true},"adRenderers":0}

## Domini bloccati più frequenti

- m.youtube.com: 11
- securepubads.g.doubleclick.net: 8
- www.googletagmanager.com: 8
- googleads.g.doubleclick.net: 6
- sb.scorecardresearch.com: 6
- static.doubleclick.net: 4
- cdn-gl.imrworldwide.com: 4
- cdn.taboola.com: 4
- ads.talkscreativity.com: 2
- www.youtube.com: 2
- fundingchoicesmessages.google.com: 2
- stats.open.online: 2
- ua.realtimely.io: 2
- www.googletagservices.com: 2
- visit-server.inmobi-choice.io: 2
- link.rubiconproject.com: 2
- ads.pubmatic.com: 2
- api.cmp.inmobi.com: 2
- assets.evolutionadv.it: 2
- secure-it.imrworldwide.com: 2
- static.internazionale.it: 2
- plausible.io: 2
- www.google-analytics.com: 2
- cdn.cxense.com: 2
- www.clarity.ms: 2
- connect.facebook.net: 2
- t.seedtag.com: 1
- www.ansa.it: 1
- privacyportal-eu.onetrust.com: 1

## Errori JavaScript con il blocco

- Open home: `Uncaught TypeError: evopbjs.setConfig is not a function`
- Open articolo: `Uncaught TypeError: evopbjs.setConfig is not a function`
- Open articolo: `Uncaught ReferenceError: checkBannerFrame2 is not defined`
- AGI articolo: `Uncaught SyntaxError: Unexpected end of JSON input`

## Foto

### YouTube home

| Senza blocco | Con blocco |
|---|---|
| ![](YouTube_home-senza.png) | ![](YouTube_home-blocco.png) |

### YouTube video

| Senza blocco | Con blocco |
|---|---|
| ![](YouTube_video-senza.png) | ![](YouTube_video-blocco.png) |

### YouTube video 2

| Senza blocco | Con blocco |
|---|---|
| ![](YouTube_video_2-senza.png) | ![](YouTube_video_2-blocco.png) |

### ANSA home

| Senza blocco | Con blocco |
|---|---|
| ![](ANSA_home-senza.png) | ![](ANSA_home-blocco.png) |

### ANSA articolo

| Senza blocco | Con blocco |
|---|---|
| ![](ANSA_articolo-senza.png) | ![](ANSA_articolo-blocco.png) |

### Fanpage home

| Senza blocco | Con blocco |
|---|---|
| ![](Fanpage_home-senza.png) | ![](Fanpage_home-blocco.png) |

### Fanpage articolo

| Senza blocco | Con blocco |
|---|---|
| ![](Fanpage_articolo-senza.png) | ![](Fanpage_articolo-blocco.png) |

### Open home

| Senza blocco | Con blocco |
|---|---|
| ![](Open_home-senza.png) | ![](Open_home-blocco.png) |

### Open articolo

| Senza blocco | Con blocco |
|---|---|
| ![](Open_articolo-senza.png) | ![](Open_articolo-blocco.png) |

### RaiNews home

| Senza blocco | Con blocco |
|---|---|
| ![](RaiNews_home-senza.png) | ![](RaiNews_home-blocco.png) |

### RaiNews articolo

| Senza blocco | Con blocco |
|---|---|
| ![](RaiNews_articolo-senza.png) | ![](RaiNews_articolo-blocco.png) |

### AGI home

| Senza blocco | Con blocco |
|---|---|
| ![](AGI_home-senza.png) | ![](AGI_home-blocco.png) |

### AGI articolo

| Senza blocco | Con blocco |
|---|---|
| ![](AGI_articolo-senza.png) | ![](AGI_articolo-blocco.png) |

### Internazionale home

| Senza blocco | Con blocco |
|---|---|
| ![](Internazionale_home-senza.png) | ![](Internazionale_home-blocco.png) |

### Internazionale articolo

| Senza blocco | Con blocco |
|---|---|
| ![](Internazionale_articolo-senza.png) | ![](Internazionale_articolo-blocco.png) |


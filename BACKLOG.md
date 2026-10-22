# Backlog di Canvas

Tenuto dal "regista del prodotto" (ogni ora) e implementato dal "giro di sviluppo" (ai :51).
Il giro prende le voci dall'alto di **In cima**; quando una voce esce in una versione la sposta in **Fatto** con il numero di versione.

Ogni voce: sezione · dimensione (S = una sessione corta, M = una sessione piena, L = più sessioni).
Regole fisse: niente paywall né fonti a pagamento, niente server o costi, testi e commenti in italiano, stile condiviso dell'app (TopLevelBar, Theme compatto, bouncyPress, vibrazioni; dopo il nuovo design, PR #56, CanvasCard / SectionHeader / Section e niente FAB per schermata: non proporre altro su tema, navigazione e layout finché quella PR è aperta).
Ogni voce dice anche quali **test** scrivere: nessuna voce è fatta senza.

_Ultimo aggiornamento: 2026-10-22 ~17:55 UTC (UI prima di tutto), su main 1.91.0, foto: branch `screenshots`, cartelle `claude_project-thread-brclxp/45` e `main/46` (giro di main 1.91.0 finito alle 17:5x: conferma tutto quanto sotto). 1.92.0 in lavorazione su `brclxp` (vetro liquido più leggero, + di vetro): non toccare barra e vetro._

---

## Problemi trovati dal verificatore

_(Le scrive il "verificatore": vanno fatte per prime. Ordinate per gravità. Ultima verifica: 1.91.0, giro fotografico `main/46`, 2026-10-22.)_

### V1. Il giro fotografico non vede le novità più grandi · giro fotografico · gravità media
- **Versione:** 1.72.0 e 1.81.0 (anche 1.80.0)
- **Aggiornamento 1.91.0 (`main/46`):** `44_localita` e `26_nota` ora ci sono; **`43_compila_da_solo` invece manca ancora**: nel tour `visit("Compila da solo")` viene chiamato dopo `scroll(down = true)`, la riga è fuori schermo e non composta nella lista, `tap` restituisce false e la foto viene saltata in silenzio. Spostare la visita prima dello scorrimento (o usare `performScrollToNode`).
- **Cosa non va:** nessuna foto di Calendario (agenda di 60 giorni, "Vai a un giorno", scheda evento, "Nuovo evento"/"Modifica evento"), della pagina Impostazioni › Compila da solo, della card "Trovati per te" in Oggi con una proposta, della pagina Licenze e della pagina web interna (es. tracciamento pacco). Sono le parti più grandi delle ultime versioni e oggi nessuno le guarda dopo una release.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt`
- **Come riprodurre:** `main/29/tour/` non contiene nessuna di queste schermate.
- **Cosa fare:** aggiungere al giro fotografico: `07_calendario` (con permesso calendario concesso in Robolectric via `ShadowApplication.grantPermissions` e 3-4 eventi finti nel provider, oppure con dati iniettati), `08_evento` e `09_nuovo_evento`; `43_compila_da_solo`; `05b_trovati` con una proposta finta in `AutoFill` (pacco + spesa); `44_licenze`. Anche al buio.
- **Test che lo impedisce:** le schermate stesse nel giro; un controllo nel tour che fallisce se una schermata attesa non è stata salvata.

### V8. Il giro fotografico non vede il nuovo player di YouTube né Spotify che suona · giro fotografico · gravità media
- **Versione:** 1.82.0-1.86.0 (giro `main/41`)
- **Aggiornamento 1.90.0 (`main/46`):** il player e il feed di YouTube ora sono fotografati da `YouTubeTour` (`youtube_iscrizioni`, `youtube_player`: IN DIRETTA, Short, capitoli, qualità, velocità, si vedono bene). Restano da fare: stato di errore del player con "Riprova", `30b_media_in_corso`, riga urgente in Adesso, e le due foto YouTube anche al buio.
- **Cosa non va:** la parte più grande di queste versioni non ha nessuna foto: schermata del player YouTube (`YouTubeWatchScreen`: video, titolo, barra col minutaggio, menu qualità, capitoli, commenti, "Prossimi dai canali che segui", stato di errore con "Riprova"), scheda Home e filtro "Solo dirette" del feed, bollino IN DIRETTA, il blocco "brano in riproduzione" di Media col colore del tema (1.86), una riga urgente in arancio dentro "Adesso" (1.86). `31_youtube` e `32_spotify` mostrano solo "Collega il tuo...", `30_media` è senza brano in corso.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt`
- **Come riprodurre:** in `main/41/tour/` non c'è nessuna di queste schermate.
- **Cosa fare:** aggiungere `34_youtube_player` aprendo `Routes.youtubeWatch(...)` con uno `YtNow` finto (stream con 3 capitoli, 3 qualità, durata) iniettato in `YtPlayer` (rendere `_now` impostabile da test, `@VisibleForTesting`), più `34b_youtube_errore` con `error` valorizzato; `35_youtube_feed` con video finti (uno IN DIRETTA, uno Short) e la scheda Home; `30b_media_in_corso` con un `Track` finto in `NowPlaying`; in `01_oggi` un pacco in ritardo o un promemoria scaduto per avere una riga urgente. Tutto anche al buio.
- **Test che lo impedisce:** le schermate stesse nel giro, con il controllo "schermata attesa mancante" di V1.

### V9. Le novità di YouTube e Spotify non hanno test veri · test · gravità media
- **Versione:** 1.82.0-1.84.0
- **Cosa non va:** l'unico test nuovo è `YouTubeNativeCheck`, che nel giro normale viene saltato (`assumeTrue`, "ignorato" nel report di `main/41`) e gira solo nel workflow `ytcheck.yml` quando si fa push su `claude/**` toccando il player. Non c'è nessun test per la logica pura nuova: `YtResolver.videoId` (watch?v=, youtu.be, shorts/, embed/, live/, link con `&t=`), `YtResolver.userAgentFor` (c=ANDROID, c=IOS, nessun c=), `SpotifyPlayer.spotifyUri` (`/intl-it/track/...`, `?si=...`, `spotify:album:...`, host non Spotify, `/user/...` → null), `Routes.mediaPlayer`/`youtubeWatch` (codifica dell'url). Inoltre, quando YouTube cambia qualcosa, il player nativo si rompe senza che nessuno se ne accorga finché Antonio non lo prova.
- **Dove:** `media/youtube/player/YtResolver.kt`, `media/spotify/SpotifyPlayer.kt`, `MainActivity.kt` (`Routes`), `.github/workflows/ytcheck.yml`.
- **Come riprodurre:** report `main/41/report`: 139 test, 2 ignorati (uno è `YouTubeNativeCheck.flussi`); nessuna classe di test per YtResolver o SpotifyPlayer.
- **Cosa fare:** `YtLinksTest` e `SpotifyLinksTest` (Robolectric per `Uri`) con i casi sopra; aggiungere a `ytcheck.yml` un `schedule` (es. una volta al giorno, gratis sul repo pubblico) e anche il push su `main`, così un'estrazione rotta si vede nel giro del verificatore.
- **Test che lo impedisce:** i due file di test; `ytcheck` programmato.

### V10. Spotify nell'app: riproduttore non più pronto e messaggi grezzi · Spotify, link di Spotify · gravità media
- **Versione:** 1.84.0
- **Cosa non va:** (1) `SpotifyPlayer` ascolta `ready` ma non `not_ready`: se il riproduttore nascosto perde il collegamento (rete cambiata, telefono in standby a lungo) `ready` resta completato con il vecchio `device_id` e il tocco su un brano fa `PUT me/player/play?device_id=...` su un dispositivo che non esiste più; l'errore mostrato è il messaggio grezzo dell'API (`it.message`, spesso in inglese, es. "Device not found"). (2) Spotify dichiara che il Web Playback SDK non è supportato sui browser per telefono: va provato su un telefono vero che parta davvero dentro la WebView (Widevine, `RESOURCE_PROTECTED_MEDIA_ID`). Se non parte, il messaggio "Questo telefono non riesce ad avviare il riproduttore" deve dire cosa fare.
- **Dove:** `media/spotify/SpotifyPlayer.kt` (`Bridge`, pagina `PAGE`, `play`).
- **Come riprodurre:** con Spotify Premium collegato, far partire un brano, togliere la rete per un minuto, rimetterla e toccare un altro brano.
- **Cosa fare:** listener `not_ready` → `deviceId = null` e nuovo `CompletableDeferred`; su 404 dal `playOn` riavviare la pagina (`MediaWebViews.release(SPOTIFY)` + `start`) e riprovare una volta; tradurre gli errori dell'API con una funzione `spotifyMessage(code, message)` in italiano. Antonio: provare sul telefono e dire se si sente.
- **Test che lo impedisce:** test di `spotifyMessage` (401, 403 Premium, 404 dispositivo, 429, rete assente).

### V13. Le novità della 1.88 non si vedono nel giro fotografico · giro fotografico · gravità media
- **Versione:** 1.88.0 (giro `main/46`)
- **Cosa non va:** nessuna foto mostra le funzioni principali della 1.88: una nota con lista di spunta ("3 di 7 fatti" nella lista delle Note, caselle e "Aggiungi voce" nell'editor: `26_nota` mostra una nota di solo testo), la ricerca in tutte le Note con risultati, le spese fisse ("Fisse · variabili" in cima al mese, sezione "Spese fisse · … al mese"), un'abitudine settimanale ("2 su 3 questa settimana"), la riga "2 cose da attivare" aperta, l'anteprima di un evento dal "+", il dialogo "Ripristina da backup" con il riepilogo. I dati finti del tour non hanno né liste, né fisse, né abitudini settimanali, quindi un errore di layout lì non si vedrebbe.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt` (dati finti e visite).
- **Come riprodurre:** in `main/46/tour/` nessuna di queste schermate; `24_spese` e `23_abitudini` hanno solo spese variabili e abitudini giornaliere.
- **Cosa fare:** nei dati finti una nota "Spesa" con `[x] latte`, `[ ] pane`, `[ ] uova`; una spesa fissa ("ogni mese il 5 affitto 650") e una abitudine "Palestra 3 volte a settimana"; nuove foto `27_cerca` (lente, cerca "latte"), `28_lista` (editor della nota lista), `07_da_attivare` (riga aperta). Tutto anche al buio.
- **Test che lo impedisce:** le schermate stesse, con il controllo "schermata attesa mancante" di V1.

### V14. Spese fisse aggiunte solo all'avvio dell'app · Note › Spese · gravità media
- **Versione:** 1.88.0
- **Cosa non va:** `NotesStore.applyFixedExpenses()` viene chiamata solo in `NotesStore.init`, cioè quando il processo parte. Android tiene l'app in memoria anche per giorni: se l'app resta aperta (o in sottofondo) a cavallo del giorno dell'addebito, l'affitto del 5 non compare nelle spese finché il processo non viene chiuso, e il budget al giorno è sbagliato. Anche `replaceAll` (ripristino da backup) non le applica: dopo un ripristino le fisse scadute da quando è stato fatto il backup restano fuori fino al prossimo avvio.
- **Dove:** `notes/NotesStore.kt` (`init`, `replaceAll`, `applyFixedExpenses`).
- **Come riprodurre:** il 4 sera aggiungere "ogni mese il 5 netflix 12,99", lasciare l'app in sottofondo, il 5 riaprirla dalla barra delle app recenti: in Spese non c'è Netflix.
- **Cosa fare:** chiamare `applyFixedExpenses()` anche a ogni ritorno in primo piano (`ON_RESUME` in `MainActivity` o `ProcessLifecycleOwner`), nel `RefreshWorker`/`MorningWorker` e dopo `replaceAll`.
- **Test che lo impedisce:** test in `NotesStoreTest`: dopo `replaceAll` con una fissa scaduta la spesa c'è; test che `applyFixedExpenses(today)` chiamata due volte lo stesso giorno non la duplica.

### V15. Backup: esportazione su Drive e notifica del mattino dopo il ripristino · Impostazioni › Dati · gravità bassa
- **Versione:** 1.88.0
- **Cosa non va:** (1) `Backup.export` apre il file con `openOutputStream(uri, "wt")`: alcuni fornitori di documenti (Google Drive in particolare) rifiutano la modalità "wt" e l'esportazione finisce con "non riuscita" senza dire perché. (2) Dopo "Ripristina da backup" le impostazioni della notifica del mattino cambiano ma `MorningWorker.schedule` non viene richiamato: la notifica parte (o smette) solo dal prossimo avvio dell'app. Anche il widget non si aggiorna. (3) "Rimanda" di notte: l'etichetta si calcola quando la notifica viene mostrata (`TaskReminderWorker.kt:70`), l'orario quando si tocca (riga 131). Notifica alle 4:50 con "Stamattina 9:00", toccata alle 5:10: rimanda a domani 9:00, non a stamattina.
- **Dove:** `backup/Backup.kt` (`export`, `restore`), `ui/screens/BackupUi.kt`; per (3) `notes/TaskReminderWorker.kt`, `notes/Snooze.kt`.
- **Come riprodurre:** (1) Esporta backup → scegliere una cartella di Google Drive; (2) ripristinare un backup con la notifica del mattino accesa su un telefono dove era spenta, non riaprire l'app: domattina niente notifica.
- **Cosa fare:** provare "wt" e se fallisce ripiegare su "w"; in `restore` richiamare `MorningWorker.schedule(context)` e aggiornare il widget; un messaggio d'errore che dica "Questa cartella non accetta il file, prova Download".
- **Test che lo impedisce:** test Robolectric di `restore` che controlla il lavoro programmato in `WorkManagerTestInitHelper`. Per (3): mettere nell'intent dell'azione l'istante in cui la notifica è stata mostrata e calcolare `until` da quello; `SnoozeTest` con etichetta e orario dallo stesso istante (4:50 mostrata, 5:10 toccata → stamattina 9:00).

### V16. Editor di una nota: il titolo non si distingue dal testo · Note › nota · gravità bassa
- **Versione:** 1.91.0 (foto `26_nota`, `scuro_26_nota`)
- **Cosa non va:** nell'editor la prima riga (il titolo, "Libri da leggere") ha la stessa misura e lo stesso peso del testo sotto; nella lista delle Note invece il titolo è in grassetto. Sembra un blocco di testo unico e la pagina sotto è vuota.
- **Dove:** `ui/screens/NotesScreen.kt` / `NotesExtras.kt` (editor della nota).
- **Cosa fare:** prima riga in `titleLarge`/grassetto (campo titolo separato o stile sulla prima riga), come nella lista.
- **Test che lo impedisce:** foto del giro `26_nota`.

### V2. "06_aggiungi" non fotografa il "+" · giro fotografico · gravità bassa
- **Versione:** 1.81.0 (giro `main/29`)
- **Cosa non va:** `06_aggiungi.png` è identica byte per byte a `05_oggi_fondo.png` (stesso md5): il tocco su "Aggiungi" non apre la cattura veloce (o la apre in un'altra Activity che Robolectric non fotografa), quindi il pannello del "+" non è mai stato controllato.
- **Dove:** `ScreenshotTour.kt` righe ~105-108; "Aggiungi" è anche la contentDescription dei + di `HomeCards.kt:365`, `NotesScreen.kt:309` e del ✓ in `AutoFillUi.kt:163`, quindi `tap("Aggiungi")` può prendere il nodo sbagliato.
- **Come riprodurre:** `md5sum main/29/tour/05_oggi_fondo.png main/29/tour/06_aggiungi.png` (ancora uguali in `main/41` e in `main/46`).
- **Cosa fare:** dare al + della barra una contentDescription unica (es. "Aggiungi al volo") e toccare quella; se la cattura è un'Activity a parte, fotografarla con `Robolectric.buildActivity(QuickCaptureActivity::class.java)`.
- **Test che lo impedisce:** nel tour, dopo il tocco, controllare che esista il nodo con il campo della cattura (es. testo "Promemoria") prima di salvare la foto.

### V6. Il punto del giorno al buio resta su "Raccolgo le notizie di oggi…" · giro fotografico · gravità bassa
- **Versione:** 1.81.0 (anche in `main/26`, ancora così in `main/46`)
- **Cosa non va:** in `scuro_01_oggi` e `scuro_05_oggi_fondo` la card Il punto del giorno è ancora in caricamento, mentre in chiaro mostra i temi; manca anche `scuro_03_punto`. Il giro al buio quindi non controlla mai il punto del giorno (titoli dei temi della 1.74.0) al buio.
- **Dove:** `ScreenshotTour.kt`, parte `+night` (il `settle` iniziale non aspetta `NewsRecap`; la visita "Tutti" non trova il link mentre carica).
- **Cosa fare:** nel giro al buio aspettare che il recap sia pronto come in chiaro, poi fotografare anche `scuro_03_punto`.
- **Test che lo impedisce:** il controllo "schermata attesa mancante" di V1.

### V7. Vetro della barra in basso: da controllare sul telefono · tutte le schermate · gravità bassa
- **Versione:** 1.80.0, più evidente dalla 1.85.0 (velo più leggero, niente ombra)
- **Aggiornamento 1.91.0:** con il vetro liquido (Android 13+, `LiquidGlass.kt`) la barra sfoca solo di 6 dp e la lente si disegna solo col disegno accelerato: nel giro fotografico (Robolectric, disegno software) non si vede mai, quindi rifrazione e leggibilità vanno guardate sul telefono. Se lo shader non si compila resta la sfocatura.
- **Cosa non va:** nel giro fotografico (dove la sfocatura non viene disegnata) il testo sotto la barra flottante si vede attraverso la tinta al 55-62% e si sovrappone alle icone (News `10_news`, Oggi `01_oggi`). Su Android 12+ la sfocatura vera dovrebbe coprirlo, ma se la sfocatura non parte (risparmio energetico, GPU, `hazeSource` mancante in qualche schermata) resta un testo leggibile male sotto le icone. Il `fallbackTint` al 96% vale solo sotto Android 12.
- **Dove:** `ui/screens/Common.kt` ~righe 336-350, `MainActivity.kt` ~righe 496-520.
- **Cosa fare:** Antonio lo guarda sul telefono; se si vede male, alzare la tinta al 75-80% o aggiungere un leggero bordo/ombra sotto la barra.
- **Test che lo impedisce:** nessuno automatico possibile per la sfocatura; tenere le foto `01_oggi`/`10_news` come confronto.

### V11. "Adesso": l'arancio urgente col tema chiaro si legge poco e con Material You non è arancio · Oggi › Adesso · gravità bassa
- **Versione:** 1.86.0
- **Cosa non va:** col tema chiaro la riga urgente usa `colorScheme.tertiary` sul fondo `primaryContainer`: con Canvas è #D9572B su #E3E8FF, contrasto circa 4,0:1 (sotto 4,5:1 per testo piccolo). Con Material You `tertiary` viene dallo sfondo del telefono e può essere verde, viola o rosa: il changelog dice "le cose urgenti restano in arancio" ma non è garantito.
- **Dove:** `ui/screens/HomeScreen.kt`, `BriefRow` (`UrgentOnDark` / `tertiary`).
- **Come riprodurre:** tema Canvas chiaro, un promemoria scaduto: la riga in "Adesso" è arancio chiaro su azzurro; tema Material You con sfondo verde: la riga urgente è verde.
- **Cosa fare:** un arancio fisso più scuro col chiaro (es. #B8431A, contrasto ≥ 4,5:1 su #E3E8FF) oppure `colorScheme.error`, che resta "allarme" anche con Material You; tenere anche l'icona piena per non affidarsi solo al colore.
- **Test che lo impedisce:** test di una funzione `contrast(fg, bg)` che verifica ≥ 4,5 per il colore urgente su `primaryContainer` in tutti e tre i temi chiari e scuri.

### V12. Player YouTube: piccoli difetti dal codice · YouTube › player · gravità bassa
- **Versione:** 1.83.0-1.84.0
- **Cosa non va:** (1) `YtPlayer.player(context)` crea il listener con il primo `context` ricevuto (spesso l'Activity): quell'Activity resta in memoria per sempre e `next(context)` a fine video usa un'Activity forse già distrutta. (2) Se i commenti non si caricano (rete, errore di NewPipe) si vede "Nessun commento (o commenti disattivati)" invece di un errore con "Riprova" (`commentsFor` fa `getOrDefault(emptyList())`). (3) Tornando sulla schermata di un video messo in pausa, `LaunchedEffect(url)` chiama `YtPlayer.play` che fa ripartire il video da solo.
- **Dove:** `media/youtube/player/YtPlayer.kt` (`player`, `play`), `ui/screens/YouTubeWatchScreen.kt` (righe ~81 e ~291).
- **Come riprodurre:** (2) modalità aereo, aprire un video già in cache e toccare Commenti; (3) mettere in pausa, aprire Media e poi la bolla/il mini player.
- **Cosa fare:** usare sempre `context.applicationContext` dentro `YtPlayer`; distinguere errore e lista vuota nei commenti; in `play` non chiamare `play()` se lo stesso video è già caricato e in pausa per scelta dell'utente.
- **Test che lo impedisce:** test di una funzione pura per lo stato dei commenti (caricamento, errore, vuoti, lista) e del caso "stesso video già caricato".


### V17. Se il file delle Note non si legge, la prima modifica lo cancella · Note (tutti i dati) · gravità bassa
- **Versione:** già prima, più pesante dalla 1.88.0 (nel file ora ci sono anche spese fisse, liste, abitudini settimanali)
- **Cosa non va:** `NotesStore.init` fa `runCatching { parse(file.readText()) }.getOrDefault(NotesData())`: se `notes.json` è rovinato, o scritto da una versione più nuova con un valore che fa fallire `parse`, l'app parte con le Note vuote senza dirlo e il primo promemoria aggiunto riscrive il file, perdendo tutto.
- **Dove:** `notes/NotesStore.kt` riga ~201 (`init`).
- **Come riprodurre:** in un test scrivere `{"tasks": [` (troncato) in `files/notes.json`, chiamare `init` (dati vuoti), aggiungere un promemoria: il vecchio contenuto è sparito.
- **Cosa fare:** se `parse` fallisce su un file che esiste e non è vuoto, copiarlo in `notes.json.bad-<data>` prima di continuare e mostrare una volta "Non sono riuscito a leggere le note: ne ho salvato una copia, puoi ripristinare un backup".
- **Test che lo impedisce:** in `NotesStoreTest` un file rovinato: dopo `init` e un'aggiunta esiste il file `.bad` con il contenuto originale.
---

## In cima

_Giro fotografico della 1.91.0 (`claude_project-thread-brclxp/45`, 2026-10-22). La 1.90 e la 1.91 hanno rifatto YouTube, titoli grandi e schede delle Note, ma nessuna delle voci qui sotto è ancora uscita: nelle foto si vedono ancora tutte (News "niente di nuovo", X in Da fare, mezze pagine vuote in Media, Video, Date e Pacchi)._

_**Antonio (2026-10-22 13:29): "Focalizzati su UI adesso prima del resto".** Le prime 8 voci sono interfaccia (cose che si vedono nelle foto), le funzioni nuove vengono dopo. La revisione UI di `brclxp` è uscita nella 1.91.0: si può lavorare su tutte le schermate._

### 1. News: intestazione, foto mancanti e righe vuote · News · S
**Perché:** ancora nella 1.91 (foto 10_news) l'intestazione dice "Niente di nuovo dall'ultima visita" sopra notizie di 8 minuti fa; la prima notizia grande ha un riquadro grigio vuoto alto un terzo di schermo al posto della foto (quando la foto manca o non si carica), e le miniature vuote restano quadrati grigi. In più "Fanpage non risponde, riprovo al prossimo aggiornamento" è una riga grezza in cima a tutto.
**Cosa fare:** in `NewsHistory`/`NewsScreen.kt` al primo avvio o con 0 nuove ma articoli più recenti dell'ultima visita scrivere "Aggiornate 8 min fa"; la card grande senza immagine (o con immagine in errore, `AsyncImage` `onError`) diventa una card solo testo con titolo grande, e la miniatura che non si carica sparisce invece di lasciare il quadrato; la fonte che non risponde va in un chip discreto ("1 fonte non risponde") accanto ai filtri, non in una riga in cima. In `NewsRepository` scartare le voci senza articolo (titoli generici come "Dalla redazione di …", link alla home o a una sezione). Togliere da "Dopo" la voce gemella.
**Fatto quando:** nella foto l'intestazione non dice "niente di nuovo" sopra notizie fresche, nessun riquadro grigio vuoto, nessuna riga d'errore in cima.
**Test:** testo dell'intestazione per prima visita / 0 nuove / N nuove; filtro delle voci non-articolo con 3-4 esempi veri; scelta del layout della card (con foto / solo testo).

### 2. Test automatici: quello che manca · robustezza · S
**Perché:** la 1.88 ha coperto i dati delle Note (NotesStore, abitudini, spese fisse, ricerca, backup). Restano senza test il widget, le news "dall'ultima visita" e il giro fotografico non si accorge quando una schermata manca (vedi V1, V2, V6: `06_aggiungi` è ancora identica a `05_oggi_fondo` in `main/41`, `scuro_03_punto` manca ancora).
**Cosa fare:** estrarre in funzione pura le righe del widget (`DashboardWidget`) e testarle; `NewsHistoryTest` (nuove/già viste, prima visita); `RepeatTest` (31 del mese, 29 febbraio, ogni settimana) se non già coperto; nel `ScreenshotTour` un elenco delle foto attese che fa fallire il giro se una manca o è identica alla precedente.
**Fatto quando:** i test nuovi girano in `build.yml`; un giro con una foto mancante o doppia fallisce.
**Test:** sono la voce stessa.

### 3. Date, Pacchi, Media e Video: niente mezze pagine vuote · Note, Media · S
**Perché:** ancora nella 1.91 Date (22_date) ha un compleanno e poi tre quarti di schermo vuoto, e lo stesso in Pacchi (25_pacchi, un pacco), Video (33_video) e Media (30_media). Una pagina mezza vuota sembra rotta o inutile.
**Cosa fare:** uno stato vuoto curato e utile in fondo alle liste corte: in Date una riga "Importa i compleanni dai contatti" + esempi tappabili ("Anniversario", "Scadenza passaporto") che aprono il dialog già compilato; in Pacchi "Arrivati di recente" (voce di Dopo) o un suggerimento "Incolla un codice di tracciamento" + invito a Compila da solo se spento; in Video la libreria del telefono (voce sotto); in Media il blocco Riprendi (voce sotto). Stile `CanvasCard` con `IconTile`, testo breve.
**Fatto quando:** nessuna di queste schermate nelle foto ha più di un terzo di schermo vuoto.
**Test:** le foto del giro; funzione che decide quando mostrare lo stato vuoto (meno di 3 voci).

### 4. Media: "Riprendi" in un tocco · Media (+ Oggi) · M
**Perché:** lo scopo di Media è "riprendi o avvia in un tocco", ma ancora nella 1.86 (`main/41`, 30_media) la schermata è solo la griglia delle quattro fonti e sotto più di metà schermo vuoto. Quando nulla suona, per riprendere il video di ieri o la radio di stamattina servono 2-3 tocchi e bisogna ricordarsi dove era. La card "In riproduzione" c'è solo finché il player è vivo.
**Cosa fare:** in cima a `MediaScreen.kt`, quando nulla suona, un blocco colorato "Riprendi" (lo stesso hero del brano in corso, `primaryContainer` dalla 1.86) con l'ultima cosa vista o ascoltata: video locale/link con posizione (`VideoLibrary.recent`, ha `positionMs`/`durationMs`) e barra di avanzamento, ultima stazione (`RadioStore.recent`), ultimo video YouTube del player nativo (`YtPlayer`/`YouTubeWatchScreen`: salvare id, titolo, canale, miniatura e secondo in un piccolo store `MediaHistory`). Sotto la griglia, "Recenti": le altre 3-4 voci in una riga ciascuna. Se c'è qualcosa lasciato a metà da meno di 24 h, una riga anche in Adesso di Oggi.
**Fatto quando:** aprendo Media senza nulla in riproduzione la prima cosa è "Riprendi: <titolo> · 12:30 di 45:00" e un tocco riparte da lì; senza cronologia il blocco non c'è.
**Test:** funzione pura che sceglie cosa riprendere dalle cronologie (video con posizione, radio, YouTube; più recente vince; video finito al 95% escluso; più vecchio di 24 h non va in Adesso).

### 5. Video: la pagina vuota diventa la libreria · Media › Video · S
**Perché:** foto 33_video: un riquadro "Scegli cosa guardare" e due pulsanti, poi due terzi di schermo vuoto. I video del telefono (registrazioni, download) sono lì e l'app non li mostra.
**Cosa fare:** in `VideoPlayerScreen.kt` sotto i pulsanti "Sul telefono": gli ultimi 12 video da `MediaStore.Video` (miniatura, durata, data) con permesso `READ_MEDIA_VIDEO` chiesto al primo tocco su "Mostra i video del telefono"; con i recenti di `VideoLibrary` in cima con la barra di avanzamento. Togliere la voce gemella da "Dopo".
**Fatto quando:** con il permesso concesso la pagina mostra la griglia dei video del telefono e un tocco li apre nel player.
**Test:** ordinamento e unione recenti + MediaStore senza doppioni (stesso URI), durata "1:02:03".

### 6. Da fare: via le X, scorri per eliminare · Note › Da fare · S
**Perché:** foto 20_note (`main/41`): ogni riga ha una X grande a destra, cinque X in colonna che pesano più dei promemoria e dove il pollice tocca per sbaglio.
**Cosa fare:** togliere la X dalle righe di `NotesScreen.kt`; `SwipeToDismissBox` verso sinistra elimina con l'Annulla che c'è già (sfondo arancio con cestino); l'eliminazione resta anche nel dialog di modifica.
**Fatto quando:** nella foto le righe hanno solo casella e testo.
**Test:** foto del giro; nessuna logica nuova.

### 7. Rifiniture viste nelle foto · Aggiornamenti, Meteo, Oggi · S
**Perché:** piccole cose che fanno sembrare l'app non finita (foto 1.91).
**Cosa fare:** Aggiornamenti (41): "Avvisami delle nuove versioni" sta sia lì sia in Impostazioni (40, "Avviso nuove versioni"): lasciarlo solo in Impostazioni. Meteo (02) e Adesso (01): il "(" accanto a "ADESSO · ROMA" e il cerchio in alto a destra del Meteo sono l'indicatore di aggiornamento fermo a metà nel giro fotografico (al buio in `scuro_01_oggi` non c'è): sul telefono controllare che sparisca a dati arrivati; se resta più di 10 s mostrare "aggiornato 5 min fa" al suo posto. Meteo: l'icona a sinistra dell'aggiornamento (spunta con stella) non si capisce cosa fa: dare un'etichetta o un'icona chiara (es. "Località"). Oggi (01_oggi, 1.91): il Punto del giorno resta su "Raccolgo le notizie di oggi…" mentre News ha già gli articoli di 8 minuti fa: se la raccolta dura, mostrare subito i 3 titoli più recenti e sostituirli quando il punto è pronto (oltre al problema del giro fotografico in V6). Da `main/46`: nel Meteo i dettagli sono 7 riquadri e "Alba · Tramonto" resta da solo con un buco accanto: farlo largo quanto la riga o aggiungere un ottavo dato già scaricato (visibilità o punto di rugiada); in Adesso la riga "Ancora da fare oggi: Bere 2 litri d'acqua, Cammi…" è tagliata a metà parola: scrivere "Ancora da fare: 2 abitudini" quando i nomi non ci stanno.
**Fatto quando:** nelle foto nessuno dei difetti.
**Test:** foto del giro.

### 8. Spotify, Video e Radio come le altre pagine; via il Client ID · Media · S
**Perché:** la 1.91 ha dato il titolo grande a tutte le pagine interne, ma Spotify (32_spotify) e Video (33_video) hanno ancora il titolo piccolo da barra vecchia. In Spotify, sotto "Accedi a Spotify", c'è ancora il campo "Client ID dell'app Spotify" con il codice esadecimale in chiaro e "Collega con il Client ID": dalla 1.90 l'accesso ufficiale funziona e quel blocco è solo rumore tecnico da sviluppatore.
**Cosa fare:** `SpotifyFeedScreen.kt`, `VideoPlayerScreen.kt`, `RadioScreen.kt`: stessa intestazione grande con freccia di Impostazioni/Meteo (con sopra la riga piccola "IL TUO SPOTIFY" come YouTube). Spostare il Client ID in Impostazioni › Media › Spotify sotto "Avanzate" (chiuso), non nella pagina di accesso.
**Fatto quando:** nelle foto 31/32/33 e Radio le intestazioni sono uguali; nella pagina di accesso di Spotify c'è solo il pulsante.
**Test:** foto del giro (aggiungere `34_radio` se manca).

### 9. Avviso pioggia · Oggi / notifiche · M
**Perché:** Canvas ha già il nowcast a 15 minuti di Open-Meteo (`Forecast.nowcast`, `Weather.kt`) e lo usa solo se apri l'app. Il momento in cui serve è prima di uscire: "tra 20 minuti piove" va detto da solo, come fanno le app meteo di riferimento.
**Cosa fare:** in Impostazioni › Meteo l'interruttore "Avvisami se sta per piovere" (spento di default). Un `RainAlertWorker` periodico (15 min, solo tra le 7 e le 23, rete qualsiasi) legge `minutely_15` della località attuale e, se nei prossimi 45 minuti la pioggia passa da 0 a ≥ 0,3 mm, manda una notifica "Pioggia tra 20 minuti a Roma, debole per 40 minuti" che apre il Meteo. Al massimo un avviso ogni 3 ore, niente avvisi se sta già piovendo. Stessa frase anche come prima riga di Adesso.
Nel Meteo, sopra "Prossime 24 ore", una striscia "Prossima ora" con le barre di `minutely_15` e la frase ("Pioggia debole dalle 18:15") quando c'è pioggia in arrivo: oggi la pagina dice solo "Pioggia oggi 13%".
**Fatto quando:** con l'interruttore acceso e pioggia prevista tra mezz'ora arriva una sola notifica; spento, nessun worker attivo; nel Meteo la striscia compare solo con pioggia nelle prossime 2 ore.
**Test:** funzione pura `RainAlert.decide(nowcast, now, lastAlert)` (inizio pioggia tra 20 min → avviso con minuti e durata; già piove → niente; avviso 1 h fa → niente; soglia 0,3 mm; fuori orario → niente).

### 10. Domani, dalle 18 · Oggi · M
_(primo passo dell'idea grande A)_
**Perché:** dalle 18 Oggi già guarda a domani nella card Da fare, ma meteo, agenda e date restano su oggi: la sera, per sapere com'è domani, bisogna aprire tre schermate.
**Cosa fare:** in `HomeScreen.kt`/`HomeCards.kt`, dalle 18 alle 3 una card "Domani" sotto Adesso: meteo di domani (icona, min/max, pioggia % e da che ora, da `daily`/`hourly` già scaricati), primo impegno e numero di impegni (`CalendarDays`), promemoria con scadenza domani, date e addebiti fissi di domani. Righe toccabili verso le rispettive schermate. Prima della card, in Adesso, la frase più importante ("Domani 8:30 dentista, pioggia dalle 10").
**Fatto quando:** alle 19 la card Domani mostra meteo, impegni e cose da fare di domani; alle 10 non c'è.
**Test:** funzione pura `Tomorrow.build(now, forecast, events, tasks, countdowns, fixed)` (orari 17:59/18:00/02:59/03:00, pioggia "dalle 10", nessun dato → card nascosta).

### 11. Date: compleanni dai contatti · Note › Date · M
_(primo passo dell'idea grande E)_
**Perché:** nella foto 22_date (`main/41`) c'è un solo compleanno e metà schermo vuoto: la scheda si riempie a mano una data alla volta e quindi nessuno la usa. I compleanni sono già nella rubrica del telefono.
**Cosa fare:** in `NotesScreen.kt` › Date, quando la lista è corta, una riga "Importa i compleanni dai contatti"; al tocco chiede `READ_CONTACTS` (solo lì), legge `ContactsContract.CommonDataKinds.Event` (TYPE_BIRTHDAY e TYPE_ANNIVERSARY), mostra un elenco con caselle (già spuntate quelle non presenti) e crea le date "ogni anno" con l'anno di nascita se c'è. Nessun doppione se riimporto.
**Fatto quando:** importo da una rubrica con 5 compleanni e in Date ne compaiono 5 con "compie N anni" dove l'anno è noto; reimportando non si duplicano.
**Test:** conversione riga contatto → data (`1990-03-12`, `--03-12` senza anno, formato non valido scartato), riconoscimento doppioni per nome + giorno.

### 12. Spese: foto dello scontrino · Note › Spese · M
_(primo passo dell'idea grande G)_
**Perché:** la spesa si segna quando si paga, e lì scrivere "63,40 spesa conad" è il passo che fa saltare l'abitudine. Lo scontrino è già in mano: una foto basta.
**Cosa fare:** nel campo delle Spese e nel "+" un'icona fotocamera: `ActivityResultContracts.TakePicture` (nessun permesso fotocamera da chiedere) e lettura del testo sul telefono con ML Kit Text Recognition (modello incluso nell'app, gratis, senza rete). Una funzione pura `Receipt.parse(righe)` trova il totale ("TOTALE", "TOTALE COMPLESSIVO", "IMPORTO PAGATO", il numero più grande vicino a "EUR"), il negozio (prima riga in maiuscolo) e la data; mostra l'anteprima "Conad · 63,40 € · Spesa" da confermare. Categoria dal nome del negozio con le stesse regole di Compila da solo. La foto non si salva (o si salva solo se l'utente lo chiede).
**Fatto quando:** fotografando uno scontrino del supermercato l'anteprima ha totale e negozio giusti e un tocco salva la spesa.
**Test:** `ReceiptTest` con 5-6 scontrini veri trascritti come testo (Conad, Esselunga, farmacia, benzinaio con "IMPORTO", ristorante con coperto e totale), importi con virgola e punto, righe sconto da ignorare.

### 13. Ascolta il punto del giorno · News / Media · M
**Perché:** il punto del giorno (`03_punto`) è il riassunto più utile dell'app, ma si legge solo guardando lo schermo. In macchina, mentre si cucina o si cammina, Canvas può leggerlo ad alta voce come un notiziario di 3 minuti, tutto sul telefono e gratis (sintesi vocale di Android, italiano offline sui telefoni Google). È "avvia in un tocco" applicato alle notizie.
**Cosa fare:** in `RecapScreen.kt` e nella card di Oggi un pulsante "Ascolta"; un `RecapReader` costruisce il copione (apertura "Il punto delle 18:00, 4 temi"; per ogni tema il titolo + la prima frase pulita dell'articolo centrale, già scelto da `NewsRecap`) e lo legge con `TextToSpeech` dentro il player condiviso (`MediaPlaybackService` / `NowPlaying`): notifica con pausa e "tema successivo", mini player col tema in corso, si ferma se parte un altro media. Senza voce italiana installata: messaggio con cosa fare, nessun crash.
**Fatto quando:** un tocco su "Ascolta" legge i temi uno dopo l'altro, "successivo" salta al tema dopo e il mini player mostra il tema in corso.
**Test:** funzione pura `RecapReader.script(recap, now)` (apertura con ora e numero di temi, una frase per tema, niente URL, sigle e virgolette ripulite, massimo 6 temi, recap vuoto → nessun copione).

### 14. Scorciatoie dall'icona e riquadro rapido · Cattura al volo · S
**Perché:** per segnare una spesa o un promemoria oggi bisogna aprire l'app e toccare il +. Keep e Todoist offrono le scorciatoie con la pressione lunga sull'icona e un riquadro nelle impostazioni rapide: due secondi invece di cinque, sull'uso più frequente di Note.
**Cosa fare:** `res/xml/shortcuts.xml` con 4 scorciatoie statiche (Promemoria, Spesa, Nota, Radio: ultima stazione) che aprono la cattura veloce già nella modalità giusta (`CaptureMode`) o la radio; un `TileService` "Canvas: aggiungi" che apre la cattura. Icone monocromatiche coerenti con la nuova icona.
**Fatto quando:** pressione lunga sull'icona → "Spesa" apre la cattura in modalità spesa con la tastiera aperta; il riquadro rapido apre la cattura.
**Test:** test Robolectric che l'intent di ogni scorciatoia porta alla `CaptureMode` giusta (extra sconosciuto → Promemoria).

---

## Dopo

- **Notifica del mattino: proporla** · Oggi · S. (era In cima) In 40_impostazioni è ancora spenta. Dopo 3 giorni d'uso, nella riga "cose da attivare" di Oggi (`InvitesUi.kt`), "Riepilogo ogni mattina alle 7:30 · Attiva"; un tocco la accende (permesso notifiche su Android 13+). Sparisce se accesa o dopo due "Non ora". Test: nel test degli inviti (giorni d'uso, già attiva, due rifiuti).

- **Abitudini: promemoria all'ora scelta** · Note › Abitudini · M. (era In cima) Nel `HabitDialog` "Ricordamelo alle…", notifica con "Fatto" e "Più tardi"; niente se già fatta. Test: prossimo avviso, settimanali, parse vecchie.
- **News: "Leggi dopo"** · News · M. (era In cima) Segnalibro in `ReaderScreen` e pressione lunga in `NewsScreen`, store `SavedArticles` con testo pulito per leggere offline, chip "Salvati". Test: store, limite 100, doppioni.
- **Cerca ovunque** · Oggi · M. La lente delle Note (1.88) cerca solo nelle Note. Una lente in Oggi che cerca insieme Note, notizie già scaricate, date, stazioni radio e video recenti, con i risultati raggruppati per sezione. Riusa la normalizzazione di `NotesSearch` (accenti, maiuscole). Test: raggruppamento, ordinamento, limite per gruppo.
- **Abitudini: il mese a colpo d'occhio** · Note › Abitudini · S. Foto `23_abitudini` (`main/46`): due card e poi metà schermo vuoto. Toccando un'abitudine, la griglia del mese con percentuale e serie migliore; con meno di 3 abitudini, esempi tappabili ("Leggere 20 minuti", "Niente telefono a letto"). Test: serie migliore e percentuale del mese, anche per le settimanali.
- **Cattura a voce** · Cattura al volo · M. Microfono nel campo del "+" (`CaptureDialog`) e nei campi di Da fare/Spese (`RecognizerIntent`, italiano, funziona offline sui telefoni Google), il testo passa per `QuickParse` (anche per gli eventi della 1.88). Fatto quando: dico "domani alle 9 dentista" e il promemoria ha giorno e ora.
- **Date: promemoria per il regalo** · Note > Date · S. Per i compleanni opzione "Avvisami 7 giorni prima" che crea il promemoria "Regalo per Marco". Si sposa con l'import dai contatti (voce 4).
- **Spese: esporta CSV del mese** · Note > Spese · S. Dal menu del mese "Esporta CSV" (data;descrizione;categoria;importo;fissa) con condivisione.
- **Pacchi: "arrivato" in un tocco e storico** · Note > Pacchi · S. Oggi il pacco si spunta e sparisce; tenere gli ultimi 10 arrivati in una sezione chiusa "Arrivati", e in Adesso "Pacco in arrivo: Cuffie" (si vede in `main/41`) con l'azione "Arrivato".
- **Calendario in Adesso: "Esci alle…"** · Oggi · M. Per il prossimo evento con un luogo, stimare il tempo di viaggio (distanza in linea d'aria dalla posizione del meteo, senza servizi a pagamento) e scrivere "Dentista alle 15 · esci verso le 14:35"; notifica facoltativa 10 minuti prima.
- **Widget piccolo "Da fare"** · Widget · M. Secondo widget 2x2 con i promemoria di oggi spuntabili direttamente (RemoteViews con collezione o checkbox API 31+), senza aprire l'app.
- **News: segui un tema** · News · M. "Segui" su un tema del punto del giorno o una parola ("sciopero", "Roma") e una notifica silenziosa quando esce un articolo nuovo che la contiene (dal `RefreshWorker`, senza server). Max 5 temi.

---

## Idee grandi

### A. La sera: "Prepara domani" · Oggi · L
**Perché:** il momento in cui si decide la giornata dopo è la sera. Ora che Canvas scrive nel calendario (1.81) e crea eventi dal "+" (1.88) la sera si può anche pianificare, non solo guardare.
**Passi:**
1. (M) Card "Domani" dalle 18: è la voce 3 di In cima.
2. (S) Da un promemoria senza ora, "Mettilo in agenda domani" che crea l'evento in un'ora libera tra gli impegni (usa `CalendarDays`).
3. (S) Per i promemoria scaduti di oggi azioni rapide "Domani" / "Fatto" direttamente nella card.
4. (S) Notifica serale facoltativa (orario scelto, come quella del mattino) con la riga più importante di domani: "Domani 8:30 dentista, pioggia dalle 10".

### B. Riepilogo della settimana · Oggi / notifiche · L
**Perché:** l'app ha già abitudini (anche settimanali), spese fisse e variabili, promemoria completati e notizie lette, ma non restituisce mai un quadro d'insieme.
**Passi:**
1. (M) Funzione pura `WeekReview` (come `Briefing`): promemoria completati, percentuale di ogni abitudine, spese della settimana per categoria contro la media delle ultime 4, prossimi impegni e date della settimana nuova. Con test.
2. (M) Schermata "La tua settimana" con queste sezioni, aperta da una card in Oggi la domenica dalle 18 al lunedì mattina.
3. (S) Notifica della domenica sera (attivabile) con una riga di sintesi: "12 cose fatte, palestra 3/3, spese -18% sulla media".

### C. Backup automatico · Impostazioni · L
(il backup a mano c'è dalla 1.88)
1. (S) Scelta di una cartella con `ACTION_OPEN_DOCUMENT_TREE` (anche una cartella di Drive sincronizzata).
2. (M) Worker settimanale che scrive lì il backup, tenendo gli ultimi 5; riga in Impostazioni › Dati "Ultimo backup: lun 9:15".
3. (S) Al primo avvio dopo una reinstallazione, se l'app è vuota, proporre "Ripristina dall'ultimo backup".

### D. Spese: andamento nel tempo · Note > Spese · L
1. (M) Grafico degli ultimi 6 mesi (barre, Canvas Compose, senza librerie pesanti) con budget come linea e fisse/variabili in due toni.
2. (S) Tocco su una categoria: l'elenco delle sue spese del mese e il confronto con il mese prima.
3. (S) Categorie modificabili e regola "impara": se correggo la categoria di "Tigotà", le prossime spese con quella parola la usano (anche quelle di Compila da solo).

### E. Il mio anno in Date · Note > Date · L
1. (M) Importa dai contatti: è la voce 4 di In cima.
2. (S) Vista "Prossimi 12 mesi" raggruppata per mese.
3. (S) Avviso 7 giorni prima per i compleanni importati (voce "regalo" di Dopo).

### F. Podcast nel player di Canvas · Media · L
**Perché:** Media ha video, YouTube, Spotify e radio, ma non i podcast, che sono l'ascolto quotidiano più "da riprendere" che ci sia. Si fanno tutti in modo nativo e gratis: ricerca con l'API pubblica di iTunes Search (nessuna chiave), episodi dai feed RSS, audio nel player condiviso (`MediaPlaybackService`). Niente siti, niente Spotify DRM.
**Passi:**
1. (M) Quinta fonte "Podcast" in Media: cerca (iTunes Search API `entity=podcast`), segui, elenco episodi dal feed RSS (titolo, data, durata, descrizione). Test: parser RSS con 2 feed veri salvati come risorsa (itunes:duration "1:02:03" e "3723").
2. (M) Riproduzione nel player condiviso con posizione ricordata per episodio, velocità 1×/1,25×/1,5×, salta ±15/30 s, notifica e bolla. Entra in "Riprendi" (voce 1).
3. (S) "Nuovi episodi" dei podcast seguiti in cima alla fonte e una riga in Adesso al mattino ("2 nuovi episodi: …"), aggiornati dal `RefreshWorker`.
4. (S) Scarica per l'ascolto offline (WorkManager, solo Wi-Fi), cancellati da soli dopo l'ascolto.

### G. Spese senza scrivere · Note › Spese · L
**Perché:** le spese sono la scheda che più dipende dalla costanza: ogni passo in meno conta.
**Passi:**
1. (M) Foto dello scontrino: è la voce 12 di In cima.
2. (S) Notifiche di pagamento (Google Wallet, app della banca) già lette da Compila da solo: proporre la spesa con importo e negozio, un tocco per confermare.
3. (S) "Come ieri": dal campo delle Spese i 5 negozi/descrizioni più frequenti come chip, con l'ultimo importo.
4. (M) Spese divise: "cena 80 diviso 4" segna 20 € e tiene "Luca mi deve 20 €" in fondo al mese, con promemoria.

---

## Fatto

Dalle versioni recenti (dettagli in `CHANGELOG.md` su main):
- 1.91.0 · Nuova icona; vetro liquido da Android 13; titoli grandi con freccia nelle pagine interne; schede delle Note a pillole; spese in card; Personalizza con icone
- 1.90.0 · Accesso a Spotify con OAuth ufficiale; YouTube rifatto (anteprime grandi, Short in fila, "Stai guardando", comandi propri del player, velocità, descrizione)
- 1.88.0 · Liste di spunta nelle note (ex 9); Cerca in tutte le Note (ex 8); Spese fisse e abbonamenti con "Domani addebito" in Adesso (ex 5); Abitudini con obiettivo settimanale (ex 7) e fatte/non fatte a colpo d'occhio (ex 13); inviti di Oggi in una riga in fondo (ex 14); eventi del calendario dal "+" (ex 15); Backup ed esportazione in Impostazioni › Dati (ex 2); test dei dati delle Note con file di una versione vecchia (ex 0); V3, V4, V5 del verificatore
- 1.86.0 · Adesso, meteo e brano in corso con il colore del tema (Material You)
- 1.85.0 · Vetro della barra in basso e del mini player davvero trasparente
- 1.84.0 · YouTube: minutaggio, qualità, capitoli, commenti, Home, ricerca nativa; Spotify suona nell'app
- 1.83.0 · YouTube con il player nativo di Android, ascolto in sottofondo
- 1.82.0 · Player YouTube dell'app, login Spotify mobile
- 1.81.0 · Tutto dentro l'app: link nella pagina interna; Calendario nativo dentro Canvas con creazione e modifica degli eventi
- 1.80.0 · Impostazioni riorganizzate a gruppi, vetro sfocato; Blocco pubblicità spostato da Media a Impostazioni › Media (ex 12)
- 1.73.0 · Media tutto nell'app, un solo player alla volta
- 1.72.0 · "Compila da solo": pacchi, spese e scadenze dalle notifiche e dagli SMS; `build.yml` esegue i test unitari e un test rosso blocca la release (prima parte dell'ex 0)
- 1.70.0 · Nuovo design Canvas (tema, barra flottante con "+", card con bordo)
- 1.74.0 · "Rimanda" (1 ora / domani 9:00) nelle notifiche dei promemoria, anche per i ricorrenti (ex 4); Oggi "Da fare" solo scaduti/oggi/domani dalle 18 + max 2 senza scadenza (ex 6); Punto del giorno con il titolo dell'articolo più centrale (ex 3); Agenda "Non ora" e card nascosta dopo due rifiuti (ex 11); cerchio del pull solo dopo il trascinamento in Oggi e Meteo (ex 10); test nuovi NewsRecapTest, SnoozeTest, TodayTasksTest, CalendarAskTest
- 1.64.0 (in uscita) · Da fare: numeri a lettere e "un'ora e mezza" nelle scadenze scritte
- 1.63.0 · Adesso e widget: quanti anni compie chi festeggia oggi
- 1.61.0 · Date: "compie 30 anni" per compleanni e anniversari
- 1.56.0 · Note: "Cerca nelle note" quando sono tante (solo note libere: la ricerca completa resta in In cima, voce 8)
- 1.55.0 · Tempi relativi leggibili in tutta l'app ("ieri 14:30", "lun 9:15")
- 1.54.0 · Spese sopra i mille euro e validazione dell'importo
- 1.53.0 · Note: "Modificata X fa" nell'editor
- 1.52.0 · Note: fissa in alto dall'editor
- 1.51.0 · Località del meteo: X per cancellare, Cerca sceglie la prima città
- 1.49–1.50 · Tastiera che si apre da sola nei dialog nuovi, Invio salva
- 1.48.0 · Meteo: trascina per aggiornare
- 1.47.0 · Note e Media: toccare la scheda aperta torna in cima
- 1.34–1.41 · "Annulla" dopo spunte ed eliminazioni in tutte le schede delle Note
- 1.31.0 · Blocco pubblicità con le liste di uBlock Origin
- 1.27.0 · Promemoria in linguaggio naturale e ricorrenti, Abitudini, Spese, Pacchi, cattura al volo
- 1.21.0 · Oggi riorganizzato attorno ad "Adesso", News "dall'ultima visita"

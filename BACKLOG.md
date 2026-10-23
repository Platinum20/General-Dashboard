# Backlog di Canvas

Tenuto dal "regista del prodotto" (ogni ora) e implementato dal "giro di sviluppo" (ai :51).
Il giro prende le voci dall'alto di **In cima**; quando una voce esce in una versione la sposta in **Fatto** con il numero di versione.

Ogni voce: sezione · dimensione (S = una sessione corta, M = una sessione piena, L = più sessioni).
Regole fisse: niente paywall né fonti a pagamento, niente server o costi, testi e commenti in italiano, stile condiviso dell'app (TopLevelBar, Theme compatto, bouncyPress, vibrazioni; dopo il nuovo design, PR #56, CanvasCard / SectionHeader / Section e niente FAB per schermata: non proporre altro su tema, navigazione e layout finché quella PR è aperta).
Ogni voce dice anche quali **test** scrivere: nessuna voce è fatta senza.

_Ultimo aggiornamento: 2026-10-23 ~03:55 UTC (UI prima di tutto), su main 1.98.0 (PR #79 unita alle 03:44), foto: branch `screenshots`, cartella `claude_project-thread-qvj56k/54` (giro della 1.98.0 fatto sulla PR). Nessuna PR aperta._

---

## In lavorazione

_**Velocità e stabilità** (thread su richiesta di Antonio, 2026-10-23 00:03 UTC, branch `claude/project-thread-wqcw2c`): cache su disco di notizie, giudizi sugli articoli e feed YouTube/Spotify (mostrati subito all'avvio), notizie man mano che arrivano le fonti, tempi di rete più brevi, Spotify in parallelo, avvio più leggero. Tocca `NewsStore`, `NewsRepository`, `NewsViewModel`, `ArticleVerifier`, `YouTubeStore`, `SpotifyStore`, `MainActivity.onCreate`, nuovi `net/DiskCache.kt` e `media/FeedCache.kt`. Il giro non tocchi queste parti finché questa nota c'è._

_**Revisione delle ridondanze** (thread su richiesta di Antonio, 2026-10-23 00:05 UTC, branch `claude/project-thread-7wuf5j`): audit di doppioni in UI e codice. Prende V20, la parte "Domani ripete Da fare" della voce 7, i doppioni in Video/Media, le scorciatoie doppie e il Client ID di Spotify (voce 8). Il giro non tocchi queste parti finché questa nota c'è; la toglie il thread quando rilascia._

---

## Problemi trovati dal verificatore

_(Le scrive il "verificatore": vanno fatte per prime. Ordinate per gravità. Ultima verifica: 1.96.0, giro fotografico `main/53`, 2026-10-22.)_

### V1. Il giro fotografico non vede le novità più grandi · giro fotografico · gravità media
- **Versione:** 1.72.0 e 1.81.0 (anche 1.80.0)
- **Aggiornamento 1.91.0 (`main/46`):** `44_localita` e `26_nota` ora ci sono; **`43_compila_da_solo` invece manca ancora**: nel tour `visit("Compila da solo")` viene chiamato dopo `scroll(down = true)`, la riga è fuori schermo e non composta nella lista, `tap` restituisce false e la foto viene saltata in silenzio. Spostare la visita prima dello scorrimento (o usare `performScrollToNode`).
- **Aggiornamento 1.93.0 (`main/50`):** ora manca anche **`41_aggiornamenti`** (e `scuro_41_aggiornamenti`): con la nuova riga "Avvisami se sta per piovere" nel gruppo Meteo la riga Aggiornamenti scende fuori dalla parte composta della lista e `visit("Aggiornamenti")` la salta in silenzio, come `43_compila_da_solo`. Serve davvero il controllo "schermata attesa mancante" (o `performScrollToNode(hasText(...))` prima di ogni `visit`).
- **Cosa non va:** nessuna foto di Calendario (agenda di 60 giorni, "Vai a un giorno", scheda evento, "Nuovo evento"/"Modifica evento"), della pagina Impostazioni › Compila da solo, della card "Trovati per te" in Oggi con una proposta, della pagina Licenze e della pagina web interna (es. tracciamento pacco). Sono le parti più grandi delle ultime versioni e oggi nessuno le guarda dopo una release.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt`
- **Come riprodurre:** `main/29/tour/` non contiene nessuna di queste schermate.
- **Cosa fare:** aggiungere al giro fotografico: `07_calendario` (con permesso calendario concesso in Robolectric via `ShadowApplication.grantPermissions` e 3-4 eventi finti nel provider, oppure con dati iniettati), `08_evento` e `09_nuovo_evento`; `43_compila_da_solo`; `05b_trovati` con una proposta finta in `AutoFill` (pacco + spesa); `44_licenze`. Anche al buio.
- **Test che lo impedisce:** le schermate stesse nel giro; un controllo nel tour che fallisce se una schermata attesa non è stata salvata.

### V8. Il giro fotografico non vede il nuovo player di YouTube né Spotify che suona · giro fotografico · gravità media
- **Versione:** 1.82.0-1.86.0 (giro `main/41`)
- **Aggiornamento 1.96.0 (`main/53`):** `youtube_iscrizioni` ora mostra la barra rossa di quanto hai visto e `youtube_player` il "Non mi piace" e la durata mentre carica: bene. Restano senza foto la scheda **Cronologia** ("Guardati qui" + "Dalla tua cronologia di YouTube"), i **commenti** nuovi (Mi piace/Non mi piace, fissato, cuore, risposte, "Altri commenti"), il tasto **sottotitoli**, i **correlati** con Ripeti/Condividi e la pagina **Video** con dei video in "Sul telefono" (`33_video` mostra solo l'invito, perché il tour non concede `READ_MEDIA_VIDEO`). Aggiungere `youtube_cronologia`, `youtube_commenti` e `33b_video_telefono` (permesso concesso con `ShadowApplication.grantPermissions` e 3 video finti in `PhoneVideos`, o una lista iniettata).
- **Aggiornamento 1.93.0 (`main/50`):** mancano anche "Riprendi" in cima a Media (`30_media` è senza cronologia, quindi la sezione non si vede mai) e la riga "Riprendi: …" in Adesso; la card "Domani" si vede solo perché il giro gira dopo le 18 (aggiungere un orario fisso alle 19 per il giro, altrimenti sparisce nei giri del mattino). L'avviso pioggia è solo una notifica: verificato dal codice e da `RainAlertTest`. Aggiungere `30b_media_riprendi` con un video e una radio finti in `MediaHistory`.
- **Aggiornamento 1.90.0 (`main/46`):** il player e il feed di YouTube ora sono fotografati da `YouTubeTour` (`youtube_iscrizioni`, `youtube_player`: IN DIRETTA, Short, capitoli, qualità, velocità, si vedono bene). Restano da fare: stato di errore del player con "Riprova", `30b_media_in_corso`, riga urgente in Adesso, e le due foto YouTube anche al buio.
- **Cosa non va:** la parte più grande di queste versioni non ha nessuna foto: schermata del player YouTube (`YouTubeWatchScreen`: video, titolo, barra col minutaggio, menu qualità, capitoli, commenti, "Prossimi dai canali che segui", stato di errore con "Riprova"), scheda Home e filtro "Solo dirette" del feed, bollino IN DIRETTA, il blocco "brano in riproduzione" di Media col colore del tema (1.86), una riga urgente in arancio dentro "Adesso" (1.86). `31_youtube` e `32_spotify` mostrano solo "Collega il tuo...", `30_media` è senza brano in corso.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt`
- **Come riprodurre:** in `main/41/tour/` non c'è nessuna di queste schermate.
- **Cosa fare:** aggiungere `34_youtube_player` aprendo `Routes.youtubeWatch(...)` con uno `YtNow` finto (stream con 3 capitoli, 3 qualità, durata) iniettato in `YtPlayer` (rendere `_now` impostabile da test, `@VisibleForTesting`), più `34b_youtube_errore` con `error` valorizzato; `35_youtube_feed` con video finti (uno IN DIRETTA, uno Short) e la scheda Home; `30b_media_in_corso` con un `Track` finto in `NowPlaying`; in `01_oggi` un pacco in ritardo o un promemoria scaduto per avere una riga urgente. Tutto anche al buio.
- **Test che lo impedisce:** le schermate stesse nel giro, con il controllo "schermata attesa mancante" di V1.

### V13. Le novità della 1.88 non si vedono nel giro fotografico · giro fotografico · gravità media
- **Versione:** 1.88.0 (giro `main/46`)
- **Cosa non va:** nessuna foto mostra le funzioni principali della 1.88: una nota con lista di spunta ("3 di 7 fatti" nella lista delle Note, caselle e "Aggiungi voce" nell'editor: `26_nota` mostra una nota di solo testo), la ricerca in tutte le Note con risultati, le spese fisse ("Fisse · variabili" in cima al mese, sezione "Spese fisse · … al mese"), un'abitudine settimanale ("2 su 3 questa settimana"), la riga "2 cose da attivare" aperta, l'anteprima di un evento dal "+", il dialogo "Ripristina da backup" con il riepilogo. I dati finti del tour non hanno né liste, né fisse, né abitudini settimanali, quindi un errore di layout lì non si vedrebbe.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt` (dati finti e visite).
- **Come riprodurre:** in `main/46/tour/` nessuna di queste schermate; `24_spese` e `23_abitudini` hanno solo spese variabili e abitudini giornaliere.
- **Cosa fare:** nei dati finti una nota "Spesa" con `[x] latte`, `[ ] pane`, `[ ] uova`; una spesa fissa ("ogni mese il 5 affitto 650") e una abitudine "Palestra 3 volte a settimana"; nuove foto `27_cerca` (lente, cerca "latte"), `28_lista` (editor della nota lista), `07_da_attivare` (riga aperta). Tutto anche al buio.
- **Test che lo impedisce:** le schermate stesse, con il controllo "schermata attesa mancante" di V1.

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
- **Aggiornamento 1.92.0 (`main/50`):** il velo è ancora più leggero (22% in chiaro, 30% al buio col vetro liquido): nel giro `10_news` il titolo "Fine vita, … legge sul …" passa sotto le icone della barra e si legge come se fosse sopra, più che in `main/46`. Anche il + ora è di vetro (primario al 72-78%): al buio è lilla chiaro, si vede bene. Da guardare sul telefono con una pagina piena di testo sotto la barra.
- **Aggiornamento 1.91.0:** con il vetro liquido (Android 13+, `LiquidGlass.kt`) la barra sfoca solo di 6 dp e la lente si disegna solo col disegno accelerato: nel giro fotografico (Robolectric, disegno software) non si vede mai, quindi rifrazione e leggibilità vanno guardate sul telefono. Se lo shader non si compila resta la sfocatura.
- **Cosa non va:** nel giro fotografico (dove la sfocatura non viene disegnata) il testo sotto la barra flottante si vede attraverso la tinta al 55-62% e si sovrappone alle icone (News `10_news`, Oggi `01_oggi`). Su Android 12+ la sfocatura vera dovrebbe coprirlo, ma se la sfocatura non parte (risparmio energetico, GPU, `hazeSource` mancante in qualche schermata) resta un testo leggibile male sotto le icone. Il `fallbackTint` al 96% vale solo sotto Android 12.
- **Dove:** `ui/screens/Common.kt` ~righe 336-350, `MainActivity.kt` ~righe 496-520.
- **Cosa fare:** Antonio lo guarda sul telefono; se si vede male, alzare la tinta al 75-80% o aggiungere un leggero bordo/ombra sotto la barra.
- **Test che lo impedisce:** nessuno automatico possibile per la sfocatura; tenere le foto `01_oggi`/`10_news` come confronto.

---

## In cima

_Giro fotografico della 1.98.0 (`claude_project-thread-qvj56k/54`): Abitudini ora ha le "Idee da aggiungere", in Adesso il pacco ha il bottone tondo "Arrivato". **Ancora uguali da `main/50`:** in News riquadro grigio di un terzo di schermo, tre miniature grigie e la riga "Fanpage non risponde…" in cima (1: è la voce UI più vecchia, farla per prima); Media al primo uso con due terzi vuoti e Pacchi con un pacco e tutto vuoto (3); Spotify con titolo piccolo e il Client ID in chiaro, Video con titolo piccolo, "Dal telefono" doppio e la card tecnica "mp4, webm, m3u8" (8); Punto del giorno fermo su "Raccolgo le notizie di oggi…" (7). **Nuovo:** dopo mezzanotte Adesso dice "Sereno · 22° / 10° · pioggia 48%" e subito sotto la card "Oggi · venerdì 23" dice "Rovesci · Pioggia 48% · 22° / 10°": stessi numeri due volte e due cieli diversi (7). Nuova voce 19 (widget Da fare spuntabile): nessuna voce di In cima toccava i widget._

_Giro fotografico della 1.96.0 (`main/53`): Date ora ha le "Idee da aggiungere" e non è più mezza vuota, Video ha "Sul telefono", la barra rossa di YouTube si vede. **Ancora uguali a `main/50`:** riquadro grigio e riga "Fanpage non risponde" in News (1), Media al primo uso con due terzi vuoti, Pacchi con un pacco e il resto vuoto, Abitudini a metà (3), Client ID in Spotify e titoli piccoli in Spotify/Video (8), "Alba · Tramonto" da solo e riga "Ancora da fare oggi: Bere 2 litri d'acqua, Cammi…" tagliata, la card Domani che ripete il promemoria di Da fare (7), la riga "Domani 22° / 10°, pioggia 48%" col sole in Adesso (V20). Le voci 1, 3, 7, 8 sono UI e vanno fatte prima delle funzioni nuove. Voce 14 (scorciatoie) tolta: le 3 scorciatoie e il riquadro rapido esistono già (`res/xml/shortcuts.xml`, `QuickCaptureTile`)._

_Foto `main/50` (2026-10-22 18:13). La 1.93 ha tolto "Niente di nuovo" dalle News e la X in Da fare, ma in `main/50` si vedono ancora: riquadro grigio al posto della foto e riga "Fanpage non risponde" in News (1), mezze pagine vuote in Date, Pacchi, Abitudini, Media e Video (3, 5), Client ID in Spotify (8), cerchio fermo e "Alba · Tramonto" da solo nel Meteo (7). Nuovo: la card Domani ripete il promemoria che c'è già sotto in Da fare (7)._

_**Antonio (2026-10-22 13:29): "Focalizzati su UI adesso prima del resto".** Le prime 8 voci sono interfaccia (cose che si vedono nelle foto), le funzioni nuove vengono dopo. La revisione UI di `brclxp` è uscita nella 1.91.0: si può lavorare su tutte le schermate._

### 1. News: intestazione, foto mancanti e righe vuote · News · S
_(1.93.0: fatti "Aggiornate X min fa" e lo scarto delle voci non-articolo; restano foto mancanti, miniature vuote e la riga d'errore della fonte. Confermato in `main/50/tour/10_news`: card grande con un riquadro grigio di un terzo di schermo e tre miniature grigie.)_
**Perché:** ancora nella 1.91 (foto 10_news) l'intestazione dice "Niente di nuovo dall'ultima visita" sopra notizie di 8 minuti fa; la prima notizia grande ha un riquadro grigio vuoto alto un terzo di schermo al posto della foto (quando la foto manca o non si carica), e le miniature vuote restano quadrati grigi. In più "Fanpage non risponde, riprovo al prossimo aggiornamento" è una riga grezza in cima a tutto.
**Cosa fare:** in `NewsHistory`/`NewsScreen.kt` al primo avvio o con 0 nuove ma articoli più recenti dell'ultima visita scrivere "Aggiornate 8 min fa"; la card grande senza immagine (o con immagine in errore, `AsyncImage` `onError`) diventa una card solo testo con titolo grande, e la miniatura che non si carica sparisce invece di lasciare il quadrato; la fonte che non risponde va in un chip discreto ("1 fonte non risponde") accanto ai filtri, non in una riga in cima. In `NewsRepository` scartare le voci senza articolo (titoli generici come "Dalla redazione di …", link alla home o a una sezione). Togliere da "Dopo" la voce gemella.
**Fatto quando:** nella foto l'intestazione non dice "niente di nuovo" sopra notizie fresche, nessun riquadro grigio vuoto, nessuna riga d'errore in cima.
**Test:** testo dell'intestazione per prima visita / 0 nuove / N nuove; filtro delle voci non-articolo con 3-4 esempi veri; scelta del layout della card (con foto / solo testo).

### 2. Test automatici: quello che manca · robustezza · S
**Perché:** la 1.88 ha coperto i dati delle Note (NotesStore, abitudini, spese fisse, ricerca, backup). Restano senza test il widget, le news "dall'ultima visita" e il giro fotografico non si accorge quando una schermata manca (vedi V1, V2, V6: `06_aggiungi` è ancora identica a `05_oggi_fondo` in `main/41`, `scuro_03_punto` manca ancora).
**Cosa fare:** estrarre in funzione pura le righe del widget (`DashboardWidget`) e testarle; `NewsHistoryTest` (nuove/già viste, prima visita); `RepeatTest` (31 del mese, 29 febbraio, ogni settimana) se non già coperto; nel `ScreenshotTour` un elenco delle foto attese che fa fallire il giro se una manca o è identica alla precedente.
**Fatto quando:** i test nuovi girano in `build.yml`; un giro con una foto mancante o doppia fallisce.
**Test:** sono la voce stessa.

### 3. Pacchi e Media: niente mezze pagine vuote · Note, Media · S
_(1.98.0: fatte le "Idee da aggiungere" in Abitudini. Restano Pacchi e Media al primo uso.)_
_(1.96.0: fatte Date con "Idee da aggiungere" e Video con "Sul telefono". In `main/53` restano `25_pacchi` (un pacco e poi tutto vuoto), `23_abitudini` (due card e metà schermo vuoto) e `30_media` (4 riquadri e due terzi vuoti). Per Abitudini usare lo stesso schema delle idee di Date: "Idee da aggiungere" con "Leggere 20 minuti", "Palestra 3 volte a settimana", "Niente telefono a letto" che aprono il dialogo già compilato.)_
**Perché:** ancora nella 1.91 Date (22_date) ha un compleanno e poi tre quarti di schermo vuoto, e lo stesso in Pacchi (25_pacchi, un pacco), Video (33_video) e Media (30_media). Una pagina mezza vuota sembra rotta o inutile.
**Cosa fare:** uno stato vuoto curato e utile in fondo alle liste corte: in Date una riga "Importa i compleanni dai contatti" + esempi tappabili ("Anniversario", "Scadenza passaporto") che aprono il dialog già compilato; in Pacchi "Arrivati di recente" (voce di Dopo) o un suggerimento "Incolla un codice di tracciamento" + invito a Compila da solo se spento; in Video la libreria del telefono (voce sotto); in Media il blocco Riprendi è uscito in 1.93 ma compare solo se c'è qualcosa da riprendere: in `main/50/tour/30_media` (primo uso) la pagina ha 4 riquadri e poi due terzi vuoti. Al primo uso mostrare "Per iniziare": 3-4 stazioni radio italiane tappabili (Radio 2, RTL 102.5, Radio Deejay: partono in un tocco) e, se YouTube è collegato, gli ultimi 3 video dalle iscrizioni. Stile `CanvasCard` con `IconTile`, testo breve.
**Fatto quando:** nessuna di queste schermate nelle foto ha più di un terzo di schermo vuoto (anche `30_media` senza cronologia).
**Test:** le foto del giro; funzione che decide quando mostrare lo stato vuoto (meno di 3 voci); funzione che sceglie le stazioni "Per iniziare" (niente doppioni con le recenti).

### 7. Rifiniture viste nelle foto · Aggiornamenti, Meteo, Oggi · S
_(1.98.0: fatti "Alba · Tramonto" a tutta riga, "Ancora da fare: N abitudini" e Da fare senza i promemoria di domani quando c'è la card. Restano indicatore fermo, icona Località, Punto del giorno lento, sfondo dell'ombrello nella card Domani.)_
**Perché:** piccole cose che fanno sembrare l'app non finita (foto 1.91).
**Cosa fare:** ~~Aggiornamenti: avviso nuove versioni solo in Impostazioni~~ (fatto in 1.93.0). Meteo (02) e Adesso (01): il "(" accanto a "ADESSO · ROMA" e il cerchio in alto a destra del Meteo sono l'indicatore di aggiornamento fermo a metà nel giro fotografico (al buio in `scuro_01_oggi` non c'è): sul telefono controllare che sparisca a dati arrivati; se resta più di 10 s mostrare "aggiornato 5 min fa" al suo posto. Meteo: l'icona a sinistra dell'aggiornamento (spunta con stella) non si capisce cosa fa: dare un'etichetta o un'icona chiara (es. "Località"). Oggi (01_oggi, 1.91): il Punto del giorno resta su "Raccolgo le notizie di oggi…" mentre News ha già gli articoli di 8 minuti fa: se la raccolta dura, mostrare subito i 3 titoli più recenti e sostituirli quando il punto è pronto (oltre al problema del giro fotografico in V6). Da `main/46`: nel Meteo i dettagli sono 7 riquadri e "Alba · Tramonto" resta da solo con un buco accanto: farlo largo quanto la riga o aggiungere un ottavo dato già scaricato (visibilità o punto di rugiada); in Adesso la riga "Ancora da fare oggi: Bere 2 litri d'acqua, Cammi…" è tagliata a metà parola: scrivere "Ancora da fare: 2 abitudini" quando i nomi non ci stanno. Da `main/50` (1.93): la card Domani mostra "9:00 · Chiamare il commercialista" e subito sotto Da fare ripete "Chiamare il commercialista · Domani 09:00": quando la card Domani è visibile, in Da fare tenere solo scaduti e oggi (i promemoria di domani stanno nella card), oppure togliere dalla card i promemoria già elencati sotto; e nella card Domani l'icona dell'ombrello sul meteo "Rovesci" ha uno sfondo diverso dalle altre `IconTile`: uniformare.
Da `claude_project-thread-qvj56k/54` (1.98, dopo mezzanotte): Adesso scrive "Sereno · 22° / 10° · pioggia 48%" e la card "Oggi · venerdì 23" sotto ripete "Rovesci · Pioggia 48% · 22° / 10°". Quando la card del giorno è visibile, Adesso tenga solo il cielo e la temperatura di adesso ("Sereno"), senza minime/massime e pioggia del giorno; la card del giorno dica da quando ("Rovesci dalle 10"). Tra la card del giorno e "Da fare" c'è uno spazio più alto degli altri: uniformare.
**Fatto quando:** nelle foto nessuno dei difetti.
**Test:** foto del giro; funzione che sceglie le parti della riga meteo di Adesso con e senza card del giorno visibile; in `TomorrowTest`/`TodayTasksTest` un caso "promemoria di domani con la card Domani visibile" che compare una volta sola.

### 8. Spotify, Video e Radio come le altre pagine; via il Client ID · Media · S
**Perché:** la 1.91 ha dato il titolo grande a tutte le pagine interne, ma Spotify (32_spotify) e Video (33_video) hanno ancora il titolo piccolo da barra vecchia. In Spotify, sotto "Accedi a Spotify", c'è ancora il campo "Client ID dell'app Spotify" con il codice esadecimale in chiaro e "Collega con il Client ID": dalla 1.90 l'accesso ufficiale funziona e quel blocco è solo rumore tecnico da sviluppatore.
**Cosa fare:** `SpotifyFeedScreen.kt`, `VideoPlayerScreen.kt`, `RadioScreen.kt`: stessa intestazione grande con freccia di Impostazioni/Meteo (con sopra la riga piccola "IL TUO SPOTIFY" come YouTube). Spostare il Client ID in Impostazioni › Media › Spotify sotto "Avanzate" (chiuso), non nella pagina di accesso.
Da `main/53`: stessa cosa per YouTube, dove sotto "Accedi a YouTube" c'è "Oppure collega l'API ufficiale di Google" (anche quello in Avanzate); in Video il pulsante "Dal telefono" in alto e la card "Mostra i video del telefono" sotto fanno la stessa cosa: tenerne uno solo (la card) e rendere più bassa la card "Scegli cosa guardare", che occupa un terzo di schermo per una frase tecnica ("mp4, webm, streaming m3u8").
**Fatto quando:** nelle foto 31/32/33 e Radio le intestazioni sono uguali; nelle pagine di accesso di Spotify e YouTube c'è solo il pulsante; in Video un solo modo di aprire i video del telefono.
**Test:** foto del giro (aggiungere `34_radio` se manca).

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

### 15. Abitudini: promemoria all'ora scelta · Note › Abitudini · M
_(era in Dopo; promossa perché in `main/50` entrambe le abitudini sono "Manca oggi" alle 20 e l'app non lo dice finché non la apri)_
**Perché:** "Manca oggi" in arancio si vede solo aprendo Note › Abitudini. Le app di abitudini (Loop, Streaks) vivono del promemoria all'ora giusta: è quello che tiene viva la serie di "5 di fila".
**Cosa fare:** nel `HabitDialog` "Ricordamelo alle…" (ora, facoltativa); un worker/alarm per abitudine (riusa lo schema delle notifiche dei promemoria e "Rimanda" della 1.74) con azioni "Fatto" e "Più tardi" (1 ora); nessuna notifica se già fatta oggi o, per le settimanali, se l'obiettivo della settimana è raggiunto. Dopo un riavvio del telefono gli avvisi si rimettono (stesso `BootReceiver` dei promemoria).
**Fatto quando:** con "alle 21" e l'abitudine non fatta arriva la notifica, "Fatto" la segna senza aprire l'app e la serie nella scheda sale.
**Test:** `HabitReminderTest`: prossimo avviso (oggi se non ancora passato, sennò domani), già fatta → nessun avviso, settimanale a obiettivo raggiunto → nessun avviso fino a lunedì, abitudini salvate da una versione vecchia senza ora.

### 16. Radio come sveglia · Media / Oggi · M
**Perché:** la mattina Antonio ha già la notifica del mattino e il player della radio; manca il legame. Svegliarsi con la propria stazione (e dopo la musica il riepilogo di Oggi) è il gesto quotidiano che rende Canvas la prima app della giornata, tutto sul telefono e gratis. Il timer di spegnimento esiste già in Media: la sveglia è la sua metà mancante.
**Cosa fare:** in Media, sotto il timer di spegnimento, "Sveglia con la radio": orario, giorni (lun-ven / tutti), stazione (ultima ascoltata o una dalle preferite), volume che sale in 30 s. `AlarmManager.setAlarmClock` (esatta, si vede nella barra di stato; permesso `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` chiesto solo qui) che avvia `MediaPlaybackService` con la stazione; notifica a schermo intero con "Stop" e "Ancora 10 min". Se la rete non c'è dopo 20 s, suono di sveglia di sistema come riserva (mai sveglia muta). In Adesso la sera "Sveglia alle 7:00 · Radio Deejay". Riprogrammare dopo il riavvio (`BOOT_COMPLETED`) e dopo un ripristino del backup.
**Fatto quando:** impostata per tra 2 minuti, la radio parte da sola a volume crescente con l'app chiusa; senza rete suona la sveglia di sistema; "Ancora 10 min" la rimanda.
**Test:** funzione pura `nextAlarm(now, ora, giorni)` (oggi già passata → domani, venerdì sera con lun-ven → lunedì, cambio dell'ora legale); persistenza delle impostazioni; test Robolectric che l'allarme programmato porta l'intent con la stazione giusta.

### 18. Podcast in Media · Media · M
_(passo 1 dell'idea grande F, promosso: in `main/53` Media al primo uso è la pagina più vuota dell'app, e i podcast sono l'ascolto quotidiano "da riprendere" che manca)_
**Perché:** Antonio vuole tutto dentro l'app e un solo player. I podcast si possono fare in modo completamente nativo e gratis: ricerca con l'API pubblica di iTunes Search (nessuna chiave), episodi dai feed RSS, audio nel player condiviso. Niente siti, niente DRM.
**Cosa fare:** quinta fonte "Podcast" in Media (`MediaScreen.kt`, riquadro con `IconTile` come gli altri quattro, la griglia diventa 2+2+1 o una riga larga); `media/podcast/PodcastRepository.kt` con ricerca (`https://itunes.apple.com/search?media=podcast&country=it&term=…`), "Segui" salvato in prefs/JSON, `PodcastFeed.parse()` per l'RSS (titolo, data, durata, descrizione pulita, enclosure); pagina del podcast con l'elenco episodi e pagina "I tuoi podcast" con gli ultimi episodi di quelli seguiti. Il tocco su un episodio lo fa suonare in `MediaPlaybackService`/`NowPlaying` con posizione ricordata (`MediaHistory`, così entra in Riprendi), velocità e ±15/30 s.
**Fatto quando:** cerco "il post" o "muschio selvaggio", lo seguo, tocco un episodio e suona nel mini player; chiudendo e riaprendo riparte dal punto.
**Test:** `PodcastFeedTest` con 2 feed RSS veri salvati in `src/test/resources` (`itunes:duration` "1:02:03", "3723" e assente; descrizione HTML ripulita; episodi senza enclosure scartati); parsing della risposta di iTunes Search; foto `35_podcast` nel giro.

### 19. Widget "Da fare" da spuntare senza aprire l'app · Widget · M
_(era in Dopo; promossa: nessuna voce di In cima toccava i widget, e con le azioni rapide di Adesso della 1.98 la stessa logica si porta sulla schermata Home)_
**Perché:** il widget di oggi (`DashboardWidget`) mostra righe ma non si tocca: per segnare "Pagare la bolletta della luce" bisogna aprire Canvas, Note, Da fare. Il gesto più frequente della giornata si può fare dalla schermata Home, come in Google Tasks o TickTick.
**Cosa fare:** secondo widget ridimensionabile (2x2 fino a 4x4) "Da fare" con i promemoria scaduti e di oggi (stessa scelta di `TodayTasks`), ognuno con un cerchio da toccare; con Glance (`androidx.glance:glance-appwidget`) o `RemoteViews` con collezione e, da Android 12, le checkbox native. Il tocco segna fatto con un `BroadcastReceiver`/`ActionCallback` che usa lo stesso `NotesStore` di Note (ricorrenti: passa alla prossima scadenza come nell'app), poi aggiorna questo widget, Oggi e il widget principale. In fondo "+ Aggiungi" apre direttamente il "+" (`CaptureDialog`). Vuoto: "Niente da fare oggi" con il prossimo promemoria. Colori del tema (Material You da Android 12) e angoli come le `CanvasCard`.
**Fatto quando:** messo sulla Home, spuntando un promemoria quello sparisce dal widget e da Oggi senza aprire l'app; un ricorrente passa alla prossima data; il widget si aggiorna a mezzanotte e dopo un riavvio.
**Test:** funzione pura delle righe del widget (stessa di `TodayTasks`, massimo N righe per la dimensione, "+ altri 3"); Robolectric: l'azione di spunta con l'id giusto segna il promemoria e il ricorrente avanza; testo dello stato vuoto con e senza prossimo promemoria.

---

## Dopo

- **Scorciatoia "Radio" sull'icona** · Cattura al volo · S. Le scorciatoie Promemoria/Nota/Spesa e il riquadro rapido esistono già; manca una quarta scorciatoia dinamica (`ShortcutManagerCompat.pushDynamicShortcut`) "Radio Deejay" con l'ultima stazione ascoltata, che parte senza aprire Media. Test: l'intent porta la stazione giusta; nessuna scorciatoia se non c'è una stazione recente.

- **Notifica del mattino: proporla** · Oggi · S. (era In cima) In 40_impostazioni è ancora spenta. Dopo 3 giorni d'uso, nella riga "cose da attivare" di Oggi (`InvitesUi.kt`), "Riepilogo ogni mattina alle 7:30 · Attiva"; un tocco la accende (permesso notifiche su Android 13+). Sparisce se accesa o dopo due "Non ora". Test: nel test degli inviti (giorni d'uso, già attiva, due rifiuti).

- **Meteo: la pioggia della prossima ora** · Meteo / Oggi · S. L'avviso pioggia della 1.93 scarica già la pioggia a 15 minuti (Open-Meteo) ma la usa solo per la notifica. Nel Meteo sotto la card grande (e in Adesso quando piove entro un'ora) una barretta "Prossima ora" con l'intensità a 15 minuti e la frase "Pioggia debole dalle 20:30 per 45 minuti"; al posto del buco accanto ad "Alba · Tramonto" se serve. Test: frase dalla serie a 15 minuti (niente pioggia, inizia tra poco, sta piovendo e smette alle…), riusando la logica di `RainAlert`.
- **News: "Leggi dopo"** · News · M. (era In cima) Segnalibro in `ReaderScreen` e pressione lunga in `NewsScreen`, store `SavedArticles` con testo pulito per leggere offline, chip "Salvati". Test: store, limite 100, doppioni.
- **Cerca ovunque** · Oggi · M. La lente delle Note (1.88) cerca solo nelle Note. Una lente in Oggi che cerca insieme Note, notizie già scaricate, date, stazioni radio e video recenti, con i risultati raggruppati per sezione. Riusa la normalizzazione di `NotesSearch` (accenti, maiuscole). Test: raggruppamento, ordinamento, limite per gruppo.
- **Abitudini: il mese a colpo d'occhio** · Note › Abitudini · S. Foto `23_abitudini` (`main/46`): due card e poi metà schermo vuoto. Toccando un'abitudine, la griglia del mese con percentuale e serie migliore; con meno di 3 abitudini, esempi tappabili ("Leggere 20 minuti", "Niente telefono a letto"). Test: serie migliore e percentuale del mese, anche per le settimanali.
- **Cattura a voce** · Cattura al volo · M. Microfono nel campo del "+" (`CaptureDialog`) e nei campi di Da fare/Spese (`RecognizerIntent`, italiano, funziona offline sui telefoni Google), il testo passa per `QuickParse` (anche per gli eventi della 1.88). Fatto quando: dico "domani alle 9 dentista" e il promemoria ha giorno e ora.
- **Date: promemoria per il regalo** · Note > Date · S. Per i compleanni opzione "Avvisami 7 giorni prima" che crea il promemoria "Regalo per Marco". Si sposa con l'import dai contatti (voce 4).
- **Spese: esporta CSV del mese** · Note > Spese · S. Dal menu del mese "Esporta CSV" (data;descrizione;categoria;importo;fissa) con condivisione.
- **Pacchi: "arrivato" in un tocco e storico** · Note > Pacchi · S. Oggi il pacco si spunta e sparisce; tenere gli ultimi 10 arrivati in una sezione chiusa "Arrivati", e in Adesso "Pacco in arrivo: Cuffie" (si vede in `main/41`) con l'azione "Arrivato".
- **Calendario in Adesso: "Esci alle…"** · Oggi · M. Per il prossimo evento con un luogo, stimare il tempo di viaggio (distanza in linea d'aria dalla posizione del meteo, senza servizi a pagamento) e scrivere "Dentista alle 15 · esci verso le 14:35"; notifica facoltativa 10 minuti prima.
- **News: segui un tema** · News · M. "Segui" su un tema del punto del giorno o una parola ("sciopero", "Roma") e una notifica silenziosa quando esce un articolo nuovo che la contiene (dal `RefreshWorker`, senza server). Max 5 temi.

---

## Idee grandi

### A. La sera: "Prepara domani" · Oggi · L
**Perché:** il momento in cui si decide la giornata dopo è la sera. Ora che Canvas scrive nel calendario (1.81) e crea eventi dal "+" (1.88) la sera si può anche pianificare, non solo guardare.
**Passi:**
1. ~~(M) Card "Domani" dalle 18~~ fatto in 1.93.0.
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
1. (M) È la voce 18 di In cima. Quinta fonte "Podcast" in Media: cerca (iTunes Search API `entity=podcast`), segui, elenco episodi dal feed RSS (titolo, data, durata, descrizione). Test: parser RSS con 2 feed veri salvati come risorsa (itunes:duration "1:02:03" e "3723").
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
- 1.98.0 · Adesso: azioni rapide con Annulla (ex 17); idee in Abitudini (parte del 3); Meteo senza buchi e abitudini non tagliate (parte del 7); V20, V21, V22, V23, V24 del verificatore
- 1.97.0 · Spotify: playlist non tue con avviso e Riproduci, nuovi indirizzi dell'API
- 1.96.0 · Video: libreria "Sul telefono" da MediaStore (ex 5); Date: idee tappabili con poche voci (parte del 3); V14, V15, V16, V17, V18, V19 del verificatore
- 1.94.0 · Vetro della barra e del + più morbido (sotto si vedono colori e forme, non il testo sbavato)
- 1.93.0 · Card "Domani" dalle 18 (ex 10, passo 1 dell'idea A); Avviso pioggia (ex 9); Media "Riprendi" e Recenti (ex 4); Da fare: scorri per eliminare (ex 6); News "Aggiornate X min fa" e via le voci non-articolo (parte dell'1); avviso versioni solo in Impostazioni (parte del 7); V9, V10, V11, V12 del verificatore
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

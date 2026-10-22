# Backlog di Canvas

Tenuto dal "regista del prodotto" (ogni ora) e implementato dal "giro di sviluppo" (ai :51).
Il giro prende le voci dall'alto di **In cima**; quando una voce esce in una versione la sposta in **Fatto** con il numero di versione.

Ogni voce: sezione · dimensione (S = una sessione corta, M = una sessione piena, L = più sessioni).
Regole fisse: niente paywall né fonti a pagamento, niente server o costi, testi e commenti in italiano, stile condiviso dell'app (TopLevelBar, Theme compatto, bouncyPress, vibrazioni; dopo il nuovo design, PR #56, CanvasCard / SectionHeader / Section e niente FAB per schermata: non proporre altro su tema, navigazione e layout finché quella PR è aperta).
Ogni voce dice anche quali **test** scrivere: nessuna voce è fatta senza.

_Ultimo aggiornamento: 2026-10-22 ~13:40 UTC, su main 1.86.0 (1.88.0 in uscita con quasi tutta la vecchia cima), foto: branch `screenshots`, cartella `main/41`._

---

## Problemi trovati dal verificatore

_(Le scrive il "verificatore": vanno fatte per prime. Ordinate per gravità. Ultima verifica: 1.81.0, giro fotografico `main/29`, 2026-10-22.)_

### V1. Il giro fotografico non vede le novità più grandi · giro fotografico · gravità media
- **Versione:** 1.72.0 e 1.81.0 (anche 1.80.0)
- **Cosa non va:** nessuna foto di Calendario (agenda di 60 giorni, "Vai a un giorno", scheda evento, "Nuovo evento"/"Modifica evento"), della pagina Impostazioni › Compila da solo, della card "Trovati per te" in Oggi con una proposta, della pagina Licenze e della pagina web interna (es. tracciamento pacco). Sono le parti più grandi delle ultime versioni e oggi nessuno le guarda dopo una release.
- **Dove:** `app/src/test/java/com/platinum20/dashboard/ScreenshotTour.kt`
- **Come riprodurre:** `main/29/tour/` non contiene nessuna di queste schermate.
- **Cosa fare:** aggiungere al giro fotografico: `07_calendario` (con permesso calendario concesso in Robolectric via `ShadowApplication.grantPermissions` e 3-4 eventi finti nel provider, oppure con dati iniettati), `08_evento` e `09_nuovo_evento`; `43_compila_da_solo`; `05b_trovati` con una proposta finta in `AutoFill` (pacco + spesa); `44_licenze`. Anche al buio.
- **Test che lo impedisce:** le schermate stesse nel giro; un controllo nel tour che fallisce se una schermata attesa non è stata salvata.

### V2. "06_aggiungi" non fotografa il "+" · giro fotografico · gravità bassa
- **Versione:** 1.81.0 (giro `main/29`)
- **Cosa non va:** `06_aggiungi.png` è identica byte per byte a `05_oggi_fondo.png` (stesso md5): il tocco su "Aggiungi" non apre la cattura veloce (o la apre in un'altra Activity che Robolectric non fotografa), quindi il pannello del "+" non è mai stato controllato.
- **Dove:** `ScreenshotTour.kt` righe ~105-108; "Aggiungi" è anche la contentDescription dei + di `HomeCards.kt:365`, `NotesScreen.kt:309` e del ✓ in `AutoFillUi.kt:163`, quindi `tap("Aggiungi")` può prendere il nodo sbagliato.
- **Come riprodurre:** `md5sum main/29/tour/05_oggi_fondo.png main/29/tour/06_aggiungi.png`.
- **Cosa fare:** dare al + della barra una contentDescription unica (es. "Aggiungi al volo") e toccare quella; se la cattura è un'Activity a parte, fotografarla con `Robolectric.buildActivity(QuickCaptureActivity::class.java)`.
- **Test che lo impedisce:** nel tour, dopo il tocco, controllare che esista il nodo con il campo della cattura (es. testo "Promemoria") prima di salvare la foto.

### V6. Il punto del giorno al buio resta su "Raccolgo le notizie di oggi…" · giro fotografico · gravità bassa
- **Versione:** 1.81.0 (anche in `main/26`)
- **Cosa non va:** in `scuro_01_oggi` e `scuro_05_oggi_fondo` la card Il punto del giorno è ancora in caricamento, mentre in chiaro mostra i temi; manca anche `scuro_03_punto`. Il giro al buio quindi non controlla mai il punto del giorno (titoli dei temi della 1.74.0) al buio.
- **Dove:** `ScreenshotTour.kt`, parte `+night` (il `settle` iniziale non aspetta `NewsRecap`; la visita "Tutti" non trova il link mentre carica).
- **Cosa fare:** nel giro al buio aspettare che il recap sia pronto come in chiaro, poi fotografare anche `scuro_03_punto`.
- **Test che lo impedisce:** il controllo "schermata attesa mancante" di V1.

### V7. Vetro della barra in basso: da controllare sul telefono · tutte le schermate · gravità bassa
- **Versione:** 1.80.0
- **Cosa non va:** nel giro fotografico (dove la sfocatura non viene disegnata) il testo sotto la barra flottante si vede attraverso la tinta al 55-62% e si sovrappone alle icone (News `10_news`, Oggi `01_oggi`). Su Android 12+ la sfocatura vera dovrebbe coprirlo, ma se la sfocatura non parte (risparmio energetico, GPU, `hazeSource` mancante in qualche schermata) resta un testo leggibile male sotto le icone. Il `fallbackTint` al 96% vale solo sotto Android 12.
- **Dove:** `ui/screens/Common.kt` ~righe 336-350, `MainActivity.kt` ~righe 496-520.
- **Cosa fare:** Antonio lo guarda sul telefono; se si vede male, alzare la tinta al 75-80% o aggiungere un leggero bordo/ombra sotto la barra.
- **Test che lo impedisce:** nessuno automatico possibile per la sfocatura; tenere le foto `01_oggi`/`10_news` come confronto.

---

## In cima

_Giro fotografico di main 1.86.0 (`main/41`, 2026-10-22 13:3x UTC). La 1.88.0 ha svuotato quasi tutta la cima: qui le prossime voci, con più funzioni nuove._

### 1. Media: "Riprendi" in un tocco · Media (+ Oggi) · M
**Perché:** lo scopo di Media è "riprendi o avvia in un tocco", ma ancora nella 1.86 (`main/41`, 30_media) la schermata è solo la griglia delle quattro fonti e sotto più di metà schermo vuoto. Quando nulla suona, per riprendere il video di ieri o la radio di stamattina servono 2-3 tocchi e bisogna ricordarsi dove era. La card "In riproduzione" c'è solo finché il player è vivo.
**Cosa fare:** in cima a `MediaScreen.kt`, quando nulla suona, un blocco colorato "Riprendi" (lo stesso hero del brano in corso, `primaryContainer` dalla 1.86) con l'ultima cosa vista o ascoltata: video locale/link con posizione (`VideoLibrary.recent`, ha `positionMs`/`durationMs`) e barra di avanzamento, ultima stazione (`RadioStore.recent`), ultimo video YouTube del player nativo (`YtPlayer`/`YouTubeWatchScreen`: salvare id, titolo, canale, miniatura e secondo in un piccolo store `MediaHistory`). Sotto la griglia, "Recenti": le altre 3-4 voci in una riga ciascuna. Se c'è qualcosa lasciato a metà da meno di 24 h, una riga anche in Adesso di Oggi.
**Fatto quando:** aprendo Media senza nulla in riproduzione la prima cosa è "Riprendi: <titolo> · 12:30 di 45:00" e un tocco riparte da lì; senza cronologia il blocco non c'è.
**Test:** funzione pura che sceglie cosa riprendere dalle cronologie (video con posizione, radio, YouTube; più recente vince; video finito al 95% escluso; più vecchio di 24 h non va in Adesso).

### 2. Avviso pioggia · Oggi / notifiche · M
**Perché:** Canvas ha già il nowcast a 15 minuti di Open-Meteo (`Forecast.nowcast`, `Weather.kt`) e lo usa solo se apri l'app. Il momento in cui serve è prima di uscire: "tra 20 minuti piove" va detto da solo, come fanno le app meteo di riferimento.
**Cosa fare:** in Impostazioni › Meteo l'interruttore "Avvisami se sta per piovere" (spento di default). Un `RainAlertWorker` periodico (15 min, solo tra le 7 e le 23, rete qualsiasi) legge `minutely_15` della località attuale e, se nei prossimi 45 minuti la pioggia passa da 0 a ≥ 0,3 mm, manda una notifica "Pioggia tra 20 minuti a Roma, debole per 40 minuti" che apre il Meteo. Al massimo un avviso ogni 3 ore, niente avvisi se sta già piovendo. Stessa frase anche come prima riga di Adesso.
**Fatto quando:** con l'interruttore acceso e pioggia prevista tra mezz'ora arriva una sola notifica; spento, nessun worker attivo.
**Test:** funzione pura `RainAlert.decide(nowcast, now, lastAlert)` (inizio pioggia tra 20 min → avviso con minuti e durata; già piove → niente; avviso 1 h fa → niente; soglia 0,3 mm; fuori orario → niente).

### 3. Domani, dalle 18 · Oggi · M
_(primo passo dell'idea grande A)_
**Perché:** dalle 18 Oggi già guarda a domani nella card Da fare, ma meteo, agenda e date restano su oggi: la sera, per sapere com'è domani, bisogna aprire tre schermate.
**Cosa fare:** in `HomeScreen.kt`/`HomeCards.kt`, dalle 18 alle 3 una card "Domani" sotto Adesso: meteo di domani (icona, min/max, pioggia % e da che ora, da `daily`/`hourly` già scaricati), primo impegno e numero di impegni (`CalendarDays`), promemoria con scadenza domani, date e addebiti fissi di domani. Righe toccabili verso le rispettive schermate. Prima della card, in Adesso, la frase più importante ("Domani 8:30 dentista, pioggia dalle 10").
**Fatto quando:** alle 19 la card Domani mostra meteo, impegni e cose da fare di domani; alle 10 non c'è.
**Test:** funzione pura `Tomorrow.build(now, forecast, events, tasks, countdowns, fixed)` (orari 17:59/18:00/02:59/03:00, pioggia "dalle 10", nessun dato → card nascosta).

### 4. Date: compleanni dai contatti · Note › Date · M
_(primo passo dell'idea grande E)_
**Perché:** nella foto 22_date (`main/41`) c'è un solo compleanno e metà schermo vuoto: la scheda si riempie a mano una data alla volta e quindi nessuno la usa. I compleanni sono già nella rubrica del telefono.
**Cosa fare:** in `NotesScreen.kt` › Date, quando la lista è corta, una riga "Importa i compleanni dai contatti"; al tocco chiede `READ_CONTACTS` (solo lì), legge `ContactsContract.CommonDataKinds.Event` (TYPE_BIRTHDAY e TYPE_ANNIVERSARY), mostra un elenco con caselle (già spuntate quelle non presenti) e crea le date "ogni anno" con l'anno di nascita se c'è. Nessun doppione se riimporto.
**Fatto quando:** importo da una rubrica con 5 compleanni e in Date ne compaiono 5 con "compie N anni" dove l'anno è noto; reimportando non si duplicano.
**Test:** conversione riga contatto → data (`1990-03-12`, `--03-12` senza anno, formato non valido scartato), riconoscimento doppioni per nome + giorno.

### 5. Abitudini: promemoria all'ora scelta · Note › Abitudini · M
**Perché:** dalla 1.88 si vede cosa manca oggi, ma solo aprendo l'app; Adesso lo ricorda dopo le 17 per tutte insieme. Le abitudini si tengono quando il promemoria arriva al momento giusto ("Bere acqua" alle 11, "Camminare" alle 18) e si spuntano dalla notifica.
**Cosa fare:** nel `HabitDialog` "Ricordamelo alle…" facoltativo (ora, giorni della settimana per le settimanali). Allarme come i promemoria (`TaskReminderWorker` o `AlarmManager` inesatto), notifica "Camminare 30 minuti · 5 giorni di fila, non perderla" con azioni "Fatto" e "Più tardi" (1 ora, riusa `Snooze`). Nessuna notifica se è già fatta.
**Fatto quando:** imposto le 18:00, alle 18 arriva la notifica, "Fatto" spunta l'abitudine e la serie sale senza aprire l'app.
**Test:** calcolo del prossimo avviso (oggi se non ancora passato e non fatta, altrimenti domani; settimanali: solo se l'obiettivo non è già raggiunto), parse delle abitudini vecchie senza orario.

### 6. News: "Leggi dopo" · News · M
**Perché:** la News serve a "cosa è successo dall'ultima volta", ma un articolo lungo visto alle 8 di mattina oggi si perde: sparisce con il feed e non si può leggere offline in metro.
**Cosa fare:** icona segnalibro in `ReaderScreen` e pressione lunga sulle righe di `NewsScreen`; salva titolo, fonte, link e il testo pulito di `ArticleExtractor` in uno store `SavedArticles` (file JSON, max 100). Chip "Salvati (3)" accanto alle fonti; lettura offline dal testo salvato; "Letto" lo toglie con Annulla. Incluso nel backup della 1.88.
**Fatto quando:** salvo un articolo, spengo la rete e lo leggo intero dai Salvati.
**Test:** serialize/parse dello store, limite a 100 (toglie i più vecchi letti), doppioni per link.

### 7. Test automatici: quello che manca · robustezza · S
**Perché:** la 1.88 ha coperto i dati delle Note (NotesStore, abitudini, spese fisse, ricerca, backup). Restano senza test il widget, le news "dall'ultima visita" e il giro fotografico non si accorge quando una schermata manca (vedi V1, V2, V6: `06_aggiungi` è ancora identica a `05_oggi_fondo` in `main/41`, `scuro_03_punto` manca ancora).
**Cosa fare:** estrarre in funzione pura le righe del widget (`DashboardWidget`) e testarle; `NewsHistoryTest` (nuove/già viste, prima visita); `RepeatTest` (31 del mese, 29 febbraio, ogni settimana) se non già coperto; nel `ScreenshotTour` un elenco delle foto attese che fa fallire il giro se una manca o è identica alla precedente.
**Fatto quando:** i test nuovi girano in `build.yml`; un giro con una foto mancante o doppia fallisce.
**Test:** sono la voce stessa.

### 8. News: intestazione e righe vuote · News · S
**Perché:** nella foto 10_news (`main/41`) l'intestazione dice ancora "Niente di nuovo dall'ultima visita" sopra notizie di 2 minuti fa, e c'è una notizia di Internazionale con titolo "Dalla redazione di Internazionale" (una voce del feed che non è un articolo) con la miniatura vuota.
**Cosa fare:** in `NewsHistory`/`NewsScreen.kt` al primo avvio o con 0 nuove ma articoli più recenti dell'ultima visita scrivere "Aggiornate 2 min fa"; in `NewsRepository` scartare le voci senza articolo (titoli generici della fonte come "Dalla redazione di …", link alla home o a una sezione). Togliere da "Dopo" la voce gemella.
**Fatto quando:** nella foto l'intestazione non dice "niente di nuovo" sopra notizie fresche e la voce di Internazionale non c'è.
**Test:** testo dell'intestazione per prima visita / 0 nuove / N nuove; filtro delle voci non-articolo con 3-4 esempi veri.

### 9. Video: la pagina vuota diventa la libreria · Media › Video · S
**Perché:** foto 33_video: un riquadro "Scegli cosa guardare" e due pulsanti, poi due terzi di schermo vuoto. I video del telefono (registrazioni, download) sono lì e l'app non li mostra.
**Cosa fare:** in `VideoPlayerScreen.kt` sotto i pulsanti "Sul telefono": gli ultimi 12 video da `MediaStore.Video` (miniatura, durata, data) con permesso `READ_MEDIA_VIDEO` chiesto al primo tocco su "Mostra i video del telefono"; con i recenti di `VideoLibrary` in cima con la barra di avanzamento. Togliere la voce gemella da "Dopo".
**Fatto quando:** con il permesso concesso la pagina mostra la griglia dei video del telefono e un tocco li apre nel player.
**Test:** ordinamento e unione recenti + MediaStore senza doppioni (stesso URI), durata "1:02:03".

### 10. Notifica del mattino: proporla · Oggi · S
**Perché:** nella foto 40_impostazioni la notifica del mattino è ancora spenta: è il riepilogo più utile dell'app e si scopre solo frugando nelle Impostazioni.
**Cosa fare:** dopo 3 giorni d'uso una riga in fondo a Oggi (dentro la riga "cose da attivare" della 1.88, `InvitesUi.kt`) "Riepilogo ogni mattina alle 7:30 · Attiva"; un tocco la accende (chiede il permesso notifiche su Android 13+). Sparisce se accesa o dopo due "Non ora".
**Fatto quando:** al quarto giorno compare l'invito e con un tocco la notifica è attiva.
**Test:** nel test degli inviti della 1.88: giorni d'uso, già attiva, due rifiuti.

---

## Dopo

- **Note > Da fare: meno X, scorri per eliminare** · Note · S. Ogni riga ha ancora una X grande a destra (foto `main/41`, 20_note) dove il pollice tocca per sbaglio: `SwipeToDismissBox` verso sinistra elimina con l'Annulla che c'è già; l'eliminazione resta nel dialog di modifica.
- **Cattura a voce** · Cattura al volo · M. Microfono nel campo del "+" (`CaptureDialog`) e nei campi di Da fare/Spese (`RecognizerIntent`, italiano, funziona offline sui telefoni Google), il testo passa per `QuickParse` (anche per gli eventi della 1.88). Fatto quando: dico "domani alle 9 dentista" e il promemoria ha giorno e ora.
- **Date: promemoria per il regalo** · Note > Date · S. Per i compleanni opzione "Avvisami 7 giorni prima" che crea il promemoria "Regalo per Marco". Si sposa con l'import dai contatti (voce 4).
- **Spese: esporta CSV del mese** · Note > Spese · S. Dal menu del mese "Esporta CSV" (data;descrizione;categoria;importo;fissa) con condivisione.
- **Pacchi: "arrivato" in un tocco e storico** · Note > Pacchi · S. Oggi il pacco si spunta e sparisce; tenere gli ultimi 10 arrivati in una sezione chiusa "Arrivati", e in Adesso "Pacco in arrivo: Cuffie" (si vede in `main/41`) con l'azione "Arrivato".
- **Aggiornamenti: un solo interruttore** · Impostazioni · S. "Avviso nuove versioni" sta sia in Impostazioni sia in cima alla pagina Aggiornamenti (foto 41): lasciarlo solo in Impostazioni.
- **Calendario in Adesso: "Esci alle…"** · Oggi · M. Per il prossimo evento con un luogo, stimare il tempo di viaggio (distanza in linea d'aria dalla posizione del meteo, senza servizi a pagamento) e scrivere "Dentista alle 15 · esci verso le 14:35"; notifica facoltativa 10 minuti prima.
- **Widget piccolo "Da fare"** · Widget · M. Secondo widget 2x2 con i promemoria di oggi spuntabili direttamente (RemoteViews con collezione o checkbox API 31+), senza aprire l'app.
- **Meteo: cerchio che gira in alto** · Meteo · S. Nella foto 02_meteo (`main/41`) l'icona in alto a destra è un cerchio di caricamento anche con i dati già mostrati; verificare che dopo il caricamento torni l'icona "aggiorna" (forse è solo il tempo del giro fotografico: se sì, scartare).
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

---

## Fatto

Dalle versioni recenti (dettagli in `CHANGELOG.md` su main):
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

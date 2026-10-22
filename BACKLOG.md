# Backlog di Canvas

Tenuto dal "regista del prodotto" (ogni ora) e implementato dal "giro di sviluppo" (ai :51).
Il giro prende le voci dall'alto di **In cima**; quando una voce esce in una versione la sposta in **Fatto** con il numero di versione.

Ogni voce: sezione · dimensione (S = una sessione corta, M = una sessione piena, L = più sessioni).
Regole fisse: niente paywall né fonti a pagamento, niente server o costi, testi e commenti in italiano, stile condiviso dell'app (TopLevelBar, Theme compatto, bouncyPress, vibrazioni; dopo il nuovo design, PR #56, CanvasCard / SectionHeader / Section e niente FAB per schermata: non proporre altro su tema, navigazione e layout finché quella PR è aperta).
Ogni voce dice anche quali **test** scrivere: nessuna voce è fatta senza.

_Ultimo aggiornamento: 2026-10-22 ~10:35 UTC, su main 1.63.0, foto: branch `screenshots`, cartella `main/26`._

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

### V3. Pagina web interna: i link a YouTube e Spotify restano nella pagina · News › pagina web, Pacchi › Traccia · gravità bassa
- **Versione:** 1.81.0
- **Cosa non va:** il changelog dice che i link dei video negli articoli vanno nel player di YouTube dell'app. È vero nella modalità lettura (`ReaderScreen.ArticleVideo`), ma in `ArticleScreen` `shouldOverrideUrlLoading` restituisce `false` per ogni http/https: un link a youtube.com o open.spotify.com dentro la pagina di un giornale apre il sito mobile di YouTube/Spotify dentro la WebView (con login e pubblicità del sito) invece del player dell'app.
- **Dove:** `app/src/main/java/com/platinum20/dashboard/ui/screens/ArticleScreen.kt`, `createWebView` › `shouldOverrideUrlLoading`
- **Come riprodurre:** aprire un articolo in pagina web (non lettura) che contiene un link a un video YouTube e toccarlo.
- **Cosa fare:** per i link del frame principale usare `InAppLinks.target(url)`: `YouTube`/`Spotify` → `LocalInAppNav.openWeb` (che li manda al player), `Web` → resta nella pagina.
- **Test che lo impedisce:** in `InAppLinksTest` un caso per una funzione pura "resta nella pagina?" (`https://www.repubblica.it/...` → sì; `https://youtu.be/abc` e `https://open.spotify.com/track/...` → no).

### V4. Calendario: eliminare o ottenere il permesso può fallire in silenzio · Calendario › scheda evento · gravità bassa
- **Versione:** 1.81.0
- **Cosa non va:** in `EventDetailScreen`, se `CalendarRepository.delete` restituisce `false` (calendario che rifiuta, evento sparito, errore del provider) non succede niente: niente messaggio, la scheda resta. Lo stesso se il permesso di scrittura viene negato da "Modifica" o "Elimina" (`withWrite` azzera l'azione senza dire perché). Con il permesso negato per sempre, "Consenti" in Calendario e in "Nuovo evento" non apre più nulla (Android non mostra più il dialog) e serve un rimando a Info app.
- **Dove:** `ui/screens/CalendarScreen.kt` (dialog "Eliminare l'evento?", `withWrite`, `CalendarMessage` con "Consenti").
- **Come riprodurre:** negare il permesso Calendario in scrittura, aprire un evento, toccare Elimina › Elimina.
- **Cosa fare:** snackbar "Non è stato possibile eliminare l'evento" / "Serve il permesso di scrittura del calendario"; dopo un rifiuto definitivo il pulsante diventa "Apri impostazioni" (come in Compila da solo).
- **Test che lo impedisce:** test della funzione che sceglie il messaggio/azione dallo stato del permesso (concesso, negato, negato per sempre).

### V5. "Domani 9:00" dopo mezzanotte salta un giorno · notifica del promemoria · gravità bassa
- **Versione:** 1.74.0
- **Cosa non va:** `Snooze.TOMORROW.until(now)` è sempre `now + 1 giorno alle 9:00`. Se si rimanda alle 00:30 di venerdì, il promemoria torna sabato alle 9:00, mentre chi è sveglio a mezzanotte e mezza intende "domattina" (venerdì 9:00).
- **Dove:** `app/src/main/java/com/platinum20/dashboard/notes/Snooze.kt`
- **Come riprodurre:** `Snooze.TOMORROW.until(LocalDateTime.of(2026, 10, 23, 0, 30))` → 2026-10-24T09:00.
- **Cosa fare:** prima delle 5 del mattino usare le 9:00 dello stesso giorno (e magari chiamare il tasto "Stamattina 9:00" in quel caso).
- **Test che lo impedisce:** in `SnoozeTest` i casi 00:30 → stesso giorno 9:00, 23:50 → giorno dopo 9:00, 08:59 e 09:01.

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

### 0. Test automatici in CI · robustezza · M
**Perché:** oggi i test (`BriefingTest`, `QuickParseTest`, `ExpensesTest`, `CountdownTest`, `FilterEngineTest`) girano solo dentro il giro fotografico (`screenshots.yml`); `build.yml`, che costruisce e pubblica le versioni, non li esegue. Un giro di sviluppo può quindi rilasciare una versione che rompe il parser delle date o le serie delle abitudini senza che nessuno se ne accorga. L'app sta crescendo di una versione ogni pochi minuti: la rete di sicurezza deve crescere con lei.
**Cosa fare:** (1) in `.github/workflows/build.yml` un passo `gradle :app:testDebugUnitTest` prima della build, che fa fallire la build e quindi blocca la release (escludere `ScreenshotTour` e `LiveChecks`, che vanno in rete, con la stessa proprietà `-Ptour` di `screenshots.yml`). (2) Test nuovi sulle parti principali: `NotesStore` serialize/parse andata e ritorno con tutti i tipi (promemoria ricorrenti, abitudini con serie, spese, pacchi, date) e con un JSON delle versioni vecchie senza i campi nuovi; `Habit.streak`; `Repeat` (prossima occorrenza a fine mese, 29 febbraio); `NewsRecap` (raggruppamento su un piccolo insieme fisso di titoli); `NewsHistory` (nuove/già viste); le righe del widget (`DashboardWidget`, la parte che costruisce i testi).
**Fatto quando:** una PR che rompe un test ha la build rossa e non esce nessuna release; almeno 6 file di test nuovi.
**Test:** sono la voce stessa; controllare che un test fallito volutamente su un branch di prova faccia fallire `build.yml`.

### 1. Media: "Riprendi" in un tocco · Media (+ Oggi) · M
**Perché:** lo scopo di Media è "riprendi o avvia in un tocco", ma oggi la schermata è solo un menu di quattro voci (Video, YouTube, Spotify, Radio): per riprendere il video di ieri servono 3 tocchi e bisogna ricordarsi dove era.
**Cosa fare:** in cima a `MediaScreen.kt` una card "Riprendi" con l'ultima cosa ascoltata o vista: video locale/link con la posizione (`VideoLibrary.recent`, ha già `positionMs`/`durationMs`), ultima stazione radio (`RadioStore.recent`), ultimo video YouTube aperto nel player. Un tocco riparte dal punto giusto; barra di avanzamento per i video. Se c'è qualcosa in pausa da meno di 24 h, una riga anche in "Adesso" di Oggi (`BriefKind` nuovo o `FREE`).
**Fatto quando:** aprendo Media la prima cosa è "Riprendi: <titolo> · 12:30 di 45:00" e un tocco riprende da lì; senza cronologia la card non c'è.
**Test:** funzione pura che sceglie cosa riprendere (video con posizione, radio, YouTube) dalle cronologie, con i casi "niente cronologia" e "più vecchio di 24 h".

### 2. Backup ed esportazione dei dati · Impostazioni · M
**Perché:** promemoria, abitudini (con le serie), spese, note, pacchi e date stanno in un solo file JSON dentro l'app. Cambiando telefono o reinstallando si perde tutto, e Antonio ci sta mettendo la sua vita quotidiana.
**Cosa fare:** in Impostazioni una sezione "Dati" con "Esporta backup" (file `canvas-backup-AAAA-MM-GG.json` scelto con `ACTION_CREATE_DOCUMENT`, il JSON di `NotesStore.serialize` più le impostazioni utili di `AppSettings`) e "Ripristina da backup" (`ACTION_OPEN_DOCUMENT`, conferma con il riepilogo "142 promemoria, 3 abitudini…", poi sostituisce). Aggiungere `dataExtractionRules`/`fullBackupContent` nel manifest perché il file delle note sia sicuramente nel backup automatico di Android. Test unitario: serialize → parse dà gli stessi dati.
**Fatto quando:** esporto, cancello i dati dell'app, ripristino e ritrovo tutto (anche serie delle abitudini e budget).
**Test:** serialize → parse identico con tutti i tipi; ripristino da un file di una versione vecchia; file non valido rifiutato senza toccare i dati.

### 5. Spese fisse e abbonamenti · Note > Spese (+ Adesso) · M
**Perché:** affitto, bollette, Netflix, Spotify sono la parte più grossa delle spese ma vanno riscritte ogni mese a mano, e il budget non le prevede. Il parser dei ricorrenti esiste già per i promemoria.
**Cosa fare:** nelle Spese "ogni mese il 5 affitto 650" (riusare `Repeat` e `QuickParse`) crea una spesa fissa: si aggiunge da sola il giorno giusto (al primo avvio dopo la data), con un'icona di ripetizione. In cima al mese "Fisse: 720 € · Variabili: 115,90 €"; il budget giornaliero calcola solo sulle variabili. In Adesso, il giorno prima: "Domani addebito: Netflix 12,99 €".
**Fatto quando:** creo una spesa fissa, cambio data del telefono al mese dopo, e la spesa compare da sola nel totale.
**Test:** parse di "ogni mese il 5 affitto 650"; generazione delle spese fisse saltando mesi (anche il 31 in febbraio); totale fisse/variabili e budget.

### 7. Abitudini con obiettivo settimanale · Note > Abitudini · M
**Perché:** molte abitudini reali non sono quotidiane ("palestra 3 volte a settimana", "chiamare la nonna 1 volta"): oggi l'unica scelta è "ogni giorno", così la serie si rompe e l'app rimprovera alle 17 anche quando sei in pari.
**Cosa fare:** nel `HabitDialog` scelta "Ogni giorno" / "N volte a settimana". Per le settimanali: card con "2 su 3 questa settimana", serie in settimane, e in Adesso (`Briefing.kt`) la riga solo se non si fa più in tempo a raggiungere l'obiettivo. Migrazione: le abitudini esistenti restano quotidiane (campo nuovo con default nel parse).
**Fatto quando:** una abitudine "3 a settimana" spuntata lun-mer non compare più in Adesso fino a lunedì.
**Test:** conteggio della settimana, serie in settimane, riga di Adesso solo quando l'obiettivo non è più raggiungibile; parse delle abitudini vecchie senza campo nuovo.

### 8. Cerca in tutte le Note · Note · M
**Perché:** le Note crescono (promemoria completati, spese, note libere, pacchi). Dalla 1.56.0 si cercano solo le note libere, e solo quando sono tante: non c'è modo di trovare "quanto ho speso dal meccanico" o il promemoria completato di marzo.
**Cosa fare:** riusare la ricerca della 1.56.0 come base; icona lente nella barra di `NotesScreen.kt`; una pagina di ricerca che cerca mentre scrivi in promemoria (anche completati), note, spese (testo e importo), pacchi (titolo e codice) e date; risultati raggruppati per tipo, tocco apre la voce. Senza accenti e maiuscole ("perche" trova "perché").
**Fatto quando:** scrivo "mecc" e trovo la spesa "Tagliando meccanico" e il promemoria "Chiamare il meccanico".
**Test:** ricerca senza accenti e maiuscole su tutti i tipi, anche importi ("12,5" trova 12,50 €).

### 9. Note libere con liste di spunta · Note > Note · M
**Perché:** nelle foto la nota "Libri da leggere" è una lista scritta con le virgole. Liste della spesa, valigie, libri sono l'uso più comune di un'app di note.
**Cosa fare:** nell'editor delle note (`NotesScreen.kt`/`NoteEditor`) le righe che iniziano con "- " o "[ ]" diventano caselle spuntabili; pulsante "Lista" nella barra dell'editor che trasforma le righe selezionate. Nella lista delle note si vede "3 di 7 fatti". Salvato come testo semplice (`[x] ` / `[ ] `) così resta compatibile.
**Fatto quando:** creo una lista della spesa, spunto due voci, chiudo e riapro: le spunte ci sono e in lista leggo "2 di 5".
**Test:** conversione testo ↔ righe spuntabili e conteggio "2 di 5".

### 12. Blocco pubblicità fuori da Media · Media / Impostazioni · S
**Perché:** nelle foto di Media l'ultima card è "Blocco pubblicità" con contatori, numero di regole e "Aggiorna": è un'impostazione da toccare una volta, non qualcosa da riprendere o avviare. In Media ruba spazio alla futura card "Riprendi" (voce 1) e confonde lo scopo della schermata.
**Cosa fare:** spostare la card da `MediaScreen.kt` a una riga di `SettingsScreen.kt` (sezione "Account e Media": interruttore + "N richieste bloccate · aggiornate il 22 ott", tocco apre il dettaglio con "Aggiorna"). In Media, al massimo una piccola etichetta "Senza pubblicità" nei player.
**Fatto quando:** nella foto di Media non c'è più la card; in Impostazioni si accende, spegne e aggiorna come prima.
**Test:** nessuna logica nuova; aggiornare `ScreenshotTour` se fotografa la card.

---

## Dopo

- **Note > Da fare: meno X, scorri per eliminare** · Note · S. Ogni riga ha una X grande a destra (foto `main/26`, 20_note) dove il pollice tocca per sbaglio: `SwipeToDismissBox` verso sinistra elimina con l'Annulla che c'è già; l'eliminazione resta nel dialog di modifica.
- **Cattura a voce** · Cattura al volo · M. Microfono nel campo di `QuickCaptureActivity` e nei campi di Da fare/Spese (`RecognizerIntent`, italiano, funziona offline sui telefoni Google), il testo passa per `QuickParse`. Fatto quando: dico "domani alle 9 dentista" e il promemoria ha giorno e ora.
- **Date: promemoria per il regalo** · Note > Date · S. Per i compleanni ("Compleanno di …") opzione "Avvisami 7 giorni prima" che crea il promemoria "Regalo per Marco". Fatto quando: la data a 7 giorni compare in Da fare.
- **News: "Leggi dopo"** · News · M. Pressione lunga o icona segnalibro su un articolo lo salva (con il testo pulito di `ArticleCache` per leggerlo offline); filtro "Salvati" accanto alle fonti. Fatto quando: salvo un articolo, spengo la rete e lo leggo.
- **Notifica del mattino: proporla** · Oggi · S. È spenta di default (foto Impostazioni) e pochi la scoprono. Dopo 3 giorni d'uso una riga in Adesso "Vuoi questo riepilogo ogni mattina alle 7:30? Attiva" che la accende in un tocco, una volta sola.
- **Video: schermata vuota** · Media > Video · S. Senza recenti mezza schermata è vuota (foto 33_video): mostrare i video recenti del telefono (MediaStore, ultimi 6 con miniatura) oppure togliere il riquadro grande e tenere solo i due pulsanti.
- **Spese: esporta CSV del mese** · Note > Spese · S. Dal menu del mese "Esporta CSV" (data;descrizione;categoria;importo) con condivisione. Utile per il commercialista e per fogli di calcolo.
- **Giro fotografico più completo** · robustezza · S. `ScreenshotTour` non fotografa Meteo, Radio, Lettore, Cattura al volo, dialog di modifica promemoria, editor di una nota e News senza rete: aggiungerli, così il regista vede tutta l'app. Nelle foto i dati di prova non hanno l'anno di nascita del compleanno: aggiungerlo, così si vede anche "compie 30 anni" (1.61–1.63).
- **Note: le sei schede non entrano** · Note · S. Nelle foto la fila Da fare / Abitudini / Spese / Note / Pacchi / Date è più larga dello schermo: "Date" o "Da fare" restano tagliate a metà. Valutare con il nuovo design icone + etichetta corta o un numero di scadenze sulla scheda ("Da fare 2"), così si vede dove c'è qualcosa da fare senza aprirle tutte.
- **Pacchi: "arrivato" in un tocco e storico** · Note > Pacchi · S. Oggi il pacco si spunta e sparisce; tenere gli ultimi 10 arrivati in una sezione chiusa "Arrivati", e in Adesso "Pacco in arrivo: Cuffie" con l'azione "Arrivato".
- **Test di non regressione dei dati** · robustezza · S. Test unitari su `NotesStore.parse` con un JSON delle versioni vecchie (senza abitudini/spese/pacchi) per garantire che un aggiornamento non perda mai i dati.
- **Widget piccolo "Da fare"** · Widget · M. Secondo widget 2x2 con i promemoria di oggi spuntabili direttamente (RemoteViews con collezione o checkbox API 31+), senza aprire l'app.

---

## Idee grandi

### A. La sera: "Prepara domani" · Oggi · L
**Perché:** dalle 18 Oggi guarda già a domani, ma solo per leggere. Il momento in cui si decide la giornata dopo è la sera.
**Passi:**
1. (M) Card "Domani" dalle 18: meteo di domani (min/max, pioggia), primo impegno con orario, promemoria in scadenza, date e pacchi.
2. (S) Per i promemoria scaduti di oggi azioni rapide "Domani" / "Fatto" direttamente nella card.
3. (S) Notifica serale facoltativa (orario scelto, come quella del mattino) con la riga più importante di domani: "Domani 8:30 dentista, pioggia dalle 10".
4. (S) Nella notifica del mattino rimandare a ciò che si è pianificato la sera.

### B. Riepilogo della settimana · Oggi / notifiche · L
**Perché:** l'app ha già abitudini, spese, promemoria completati e notizie lette, ma non restituisce mai un quadro d'insieme.
**Passi:**
1. (M) Funzione pura `WeekReview` (come `Briefing`): promemoria completati, percentuale di ogni abitudine, spese della settimana per categoria contro la media delle ultime 4, prossimi impegni e date della settimana nuova. Con test.
2. (M) Schermata "La tua settimana" con queste sezioni, aperta da una card in Oggi la domenica dalle 18 al lunedì mattina.
3. (S) Notifica della domenica sera (attivabile) con una riga di sintesi: "12 cose fatte, palestra 3/3, spese -18% sulla media".

### C. Backup automatico · Impostazioni · L
(dopo la voce 2 di In cima)
1. (S) Scelta di una cartella con `ACTION_OPEN_DOCUMENT_TREE` (anche una cartella di Drive sincronizzata).
2. (M) Worker settimanale che scrive lì il backup, tenendo gli ultimi 5; riga in Impostazioni "Ultimo backup: lun 9:15".
3. (S) Al primo avvio dopo una reinstallazione, se l'app è vuota, proporre "Ripristina dall'ultimo backup".

### E. Il mio anno in Date · Note > Date · L
**Perché:** la scheda Date ha un solo compleanno nelle foto e si riempie a mano una data alla volta; ora che sa contare gli anni (1.61) può diventare l'agenda delle ricorrenze della vita di Antonio.
**Passi:**
1. (M) "Importa dai contatti": legge i compleanni da `ContactsContract` (permesso chiesto solo al tocco), mostra l'elenco con caselle e crea le date "ogni anno" con l'anno di nascita se c'è. Test: conversione riga contatto → data (anche senza anno, formato `--MM-DD`).
2. (S) Vista "Prossimi 12 mesi" raggruppata per mese.
3. (S) Con la voce "promemoria per il regalo" di Dopo: per ogni compleanno importato, opzione di avviso 7 giorni prima.

### D. Spese: andamento nel tempo · Note > Spese · L
1. (M) Grafico degli ultimi 6 mesi (barre, Canvas Compose, senza librerie pesanti) con budget come linea.
2. (S) Tocco su una categoria: l'elenco delle sue spese del mese e il confronto con il mese prima.
3. (S) Categorie modificabili e regola "impara": se correggo la categoria di "Tigotà", le prossime spese con quella parola la usano.

---

## Fatto

Dalle versioni recenti (dettagli in `CHANGELOG.md` su main):
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

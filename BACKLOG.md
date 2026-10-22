# Backlog di Canvas

Tenuto dal "regista del prodotto" (ogni ora) e implementato dal "giro di sviluppo" (ai :51).
Il giro prende le voci dall'alto di **In cima**; quando una voce esce in una versione la sposta in **Fatto** con il numero di versione.

Ogni voce: sezione · dimensione (S = una sessione corta, M = una sessione piena, L = più sessioni).
Regole fisse: niente paywall né fonti a pagamento, niente server o costi, testi e commenti in italiano, stile condiviso dell'app (TopLevelBar, Theme compatto, bouncyPress, vibrazioni; dopo il nuovo design, PR #56, CanvasCard / SectionHeader / Section e niente FAB per schermata: non proporre altro su tema, navigazione e layout finché quella PR è aperta).
Ogni voce dice anche quali **test** scrivere: nessuna voce è fatta senza.

_Ultimo aggiornamento: 2026-10-22 ~11:40 UTC, su main 1.81.0 (1.82.0 in lavorazione: player YouTube nativo), foto: branch `screenshots`, cartella `main/29`._

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

### 0. Test automatici: la rete sui dati · robustezza · M
**Perché:** dalla 1.72 `build.yml` esegue i test e un test rosso blocca la release: bene. Ma i test coprono soprattutto parser e logica di Oggi; manca proprio la parte che, se si rompe, fa perdere la vita di Antonio: il file delle Note. Nessun test garantisce che un aggiornamento legga ancora i dati vecchi, e la voce Backup (2) ci si appoggia.
**Cosa fare:** test nuovi in `app/src/test`: `NotesStore` serialize → parse andata e ritorno con tutti i tipi (promemoria ricorrenti e rimandati, abitudini con giorni, spese e budget, note fissate, pacchi, date ogni anno con anno di nascita); parse di un JSON "di una versione vecchia" senza abitudini/spese/pacchi (salvato come risorsa di test); `Habit.streak`/`isDone`/`lastDays`; `Repeat` (31 del mese, 29 febbraio, ogni settimana); `NewsHistory` (nuove/già viste); la parte del widget che costruisce le righe (`DashboardWidget`, estrarla in funzione pura se serve). Rendere `serialize`/`parse` `internal` per poterli chiamare.
**Fatto quando:** almeno 5 file di test nuovi verdi in `build.yml`; il JSON vecchio si legge senza perdere nulla.
**Test:** sono la voce stessa.

### 2. Backup ed esportazione dei dati · Impostazioni · M
**Perché:** promemoria, abitudini (con le serie), spese, note, pacchi e date stanno in un solo file JSON dentro l'app, e con "Compila da solo" (1.72) si riempie anche da solo. Cambiando telefono o reinstallando l'APK si perde tutto.
**Cosa fare:** in Impostazioni un gruppo "Dati" (sopra Informazioni) con "Esporta backup" (`ACTION_CREATE_DOCUMENT`, file `canvas-backup-AAAA-MM-GG.json` con il JSON di `NotesStore` più le impostazioni utili di `AppSettings`: card di Oggi, località, tema, budget) e "Ripristina da backup" (`ACTION_OPEN_DOCUMENT`, conferma con il riepilogo "42 promemoria, 3 abitudini, 18 spese…", poi sostituisce). `dataExtractionRules`/`fullBackupContent` nel manifest perché il file sia nel backup automatico di Android. Riga a destra "Ultimo backup: mai / 12 ott".
**Fatto quando:** esporto, cancello i dati dell'app, ripristino e ritrovo tutto (anche serie delle abitudini e budget).
**Test:** serialize → parse identico; ripristino da un file di una versione vecchia; file non valido o di un'altra app rifiutato senza toccare i dati; riepilogo del conteggio.

### 1. Media: "Riprendi" in un tocco · Media (+ Oggi) · M
**Perché:** lo scopo di Media è "riprendi o avvia in un tocco", ma nelle foto (`main/29`, 30_media) la schermata è solo la griglia delle quattro fonti e sotto metà schermo vuoto. Quando nulla suona, per riprendere il video di ieri o la radio di stamattina servono 2-3 tocchi e bisogna ricordarsi dove era. La card "In riproduzione" c'è solo finché il player è vivo.
**Cosa fare:** in cima a `MediaScreen.kt`, quando nulla suona, un blocco scuro "Riprendi" (lo stesso stile del brano in corso) con l'ultima cosa vista o ascoltata: video locale/link con posizione (`VideoLibrary.recent`, ha `positionMs`/`durationMs`) e barra di avanzamento, ultima stazione (`RadioStore.recent`), ultimo video YouTube del player nativo (dalla 1.82 `YouTubeWatchScreen`: salvare id, titolo e secondo). Sotto la griglia, "Recenti": le altre 3-4 voci in una riga ciascuna. Se c'è qualcosa lasciato a metà da meno di 24 h, una riga anche in Adesso di Oggi.
**Fatto quando:** aprendo Media senza nulla in riproduzione la prima cosa è "Riprendi: <titolo> · 12:30 di 45:00" e un tocco riparte da lì; senza cronologia il blocco non c'è.
**Test:** funzione pura che sceglie cosa riprendere dalle cronologie (video con posizione, radio, YouTube; più recente vince; video finito al 95% escluso; più vecchio di 24 h non va in Adesso).

_Il giro fotografico completo (Calendario, scheda evento, player YouTube 1.82, Radio, editor nota, "+") è ora la segnalazione V1 del verificatore: aggiungere anche il player YouTube nativo e il compleanno con l'anno di nascita nei dati di prova._

### 13. Abitudini: si capisce cosa è fatto · Note > Abitudini · S
**Perché:** nelle foto (23_abitudini) l'intestazione dice "Oggi 0 su 2" ma entrambe le abitudini hanno a destra lo stesso cerchio grigio con la spunta: sembra già fatto. È `FilledIconToggleButton` con l'icona `Check` sia da spento sia da acceso (`NotesScreen.kt`, `HabitsTab`).
**Cosa fare:** da non fatta un cerchio vuoto con bordo (`OutlinedIconToggleButton` o icona `RadioButtonUnchecked`), da fatta pieno con l'accento e la spunta bianca; il pallino di oggi nella fila dei giorni con un anello quando non è ancora fatto, così si vede "manca oggi". Dopo le 17, se manca, testo "Manca oggi" in arancio al posto di "5 giorni di fila".
**Fatto quando:** nella foto si distingue a colpo d'occhio la fatta dalla non fatta.
**Test:** nessuna logica nuova; la foto di `ScreenshotTour` con una fatta e una no.

### 14. Oggi: gli inviti a impostare non stanno in cima · Oggi · S
**Perché:** nelle foto (01_oggi) sotto Adesso ci sono due card che chiedono di impostare qualcosa: "Compila da solo · Attiva" e "Agenda · Consenti". Occupano lo spazio dove dovrebbe esserci cosa fare oggi, e "Da fare" scivola sotto la piega: va contro lo scopo di Oggi (5 secondi).
**Cosa fare:** in `HomeScreen.kt`/`AutoFillUi.kt` unire gli inviti in una sola riga compatta in fondo a Oggi ("2 cose da attivare: agenda, compila da solo ›") che apre un piccolo foglio con i due pulsanti e "Non ora"; una volta concesso il permesso o detto due volte "Non ora" la riga sparisce (la logica dei rifiuti c'è già in `CalendarAsk`). Le card vere (agenda con eventi, "Trovati per te") restano dove sono.
**Fatto quando:** nella foto di Oggi senza permessi, subito sotto Adesso c'è Da fare.
**Test:** funzione pura che decide quali inviti mostrare (permesso, rifiuti, già attivo) sul modello di `CalendarAskTest`.

### 15. Il "+" crea anche eventi del calendario · Cattura al volo / Calendario · M
**Perché:** dalla 1.81 Canvas scrive nel calendario del telefono, ma il "+" e la cattura al volo creano solo promemoria: "venerdì alle 20 cena da Luca" finisce in Da fare invece che in agenda, dove lo vedrebbero anche Adesso, l'Agenda di Oggi e il widget. È il flusso più frequente di una giornata e oggi richiede di aprire Calendario e compilare il modulo.
**Cosa fare:** in `CaptureDialog`/`QuickCaptureActivity` una scelta "Promemoria / Evento" accanto al campo (ricordando l'ultima usata); con Evento il testo passa per `QuickParse` (giorno, ora, "per 2 ore", "tutto il giorno") e crea l'evento con `CalendarRepository` nel calendario predefinito, con anteprima "Ven 24 ott 20:00–21:00 · Cena da Luca" prima di salvare. "Annulla" nella snackbar. Se manca il permesso di scrittura, lo chiede lì.
**Fatto quando:** dal "+" scrivo "domani alle 15 dentista" con Evento e lo trovo nell'Agenda di Oggi e nel Calendario di Canvas.
**Test:** conversione testo → evento (inizio, fine predefinita di 1 ora, tutto il giorno, "per 2 ore", "dalle 9 alle 11").

### 5. Spese fisse e abbonamenti · Note > Spese (+ Adesso) · M
**Perché:** affitto, bollette, Netflix, Spotify sono la parte più grossa delle spese ma vanno riscritte ogni mese a mano, e il budget non le prevede (nelle foto: "48,41 € al giorno" calcolato sul totale). Il parser dei ricorrenti esiste già per i promemoria ("Pagare l'affitto · ogni mese").
**Cosa fare:** nelle Spese "ogni mese il 5 affitto 650" (riusare `Repeat` e `QuickParse`) crea una spesa fissa che si aggiunge da sola il giorno giusto (al primo avvio dopo la data), con un'icona di ripetizione. In cima al mese "Fisse: 720 € · Variabili: 115,90 €"; il budget giornaliero calcola solo sulle variabili. In Adesso, il giorno prima: "Domani addebito: Netflix 12,99 €". Con "Compila da solo" acceso, se la stessa spesa arriva dalla notifica non va contata due volte.
**Fatto quando:** creo una spesa fissa, il mese dopo compare da sola nel totale e il budget giornaliero non la conta.
**Test:** parse di "ogni mese il 5 affitto 650"; generazione saltando mesi (anche il 31 in febbraio); totale fisse/variabili e budget; nessun doppione con una spesa trovata da Compila da solo lo stesso giorno.

### 7. Abitudini con obiettivo settimanale · Note > Abitudini · M
**Perché:** molte abitudini reali non sono quotidiane ("palestra 3 volte a settimana"): oggi l'unica scelta è "ogni giorno", così la serie si rompe e Adesso rimprovera alle 17 anche quando sei in pari.
**Cosa fare:** nel `HabitDialog` scelta "Ogni giorno" / "N volte a settimana". Per le settimanali: "2 su 3 questa settimana", serie in settimane, e in Adesso (`Briefing.kt`) la riga solo se non si fa più in tempo a raggiungere l'obiettivo. Le abitudini esistenti restano quotidiane (campo nuovo con default nel parse).
**Fatto quando:** una abitudine "3 a settimana" spuntata lun-mer non compare più in Adesso fino a lunedì.
**Test:** conteggio della settimana, serie in settimane, riga di Adesso solo quando l'obiettivo non è più raggiungibile; parse delle abitudini vecchie.

### 8. Cerca in tutte le Note · Note · M
**Perché:** le Note crescono (promemoria completati, spese anche automatiche, note, pacchi). Dalla 1.56 si cercano solo le note libere: non c'è modo di trovare "quanto ho speso dal meccanico" o il promemoria completato di marzo.
**Cosa fare:** lente accanto all'ingranaggio nella barra di `NotesScreen.kt`; pagina di ricerca che cerca mentre scrivi in promemoria (anche completati), note, spese (testo e importo), pacchi (titolo e codice) e date; risultati raggruppati per tipo, tocco apre la voce. Senza accenti e maiuscole.
**Fatto quando:** scrivo "mecc" e trovo la spesa "Tagliando meccanico" e il promemoria "Chiamare il meccanico".
**Test:** ricerca senza accenti e maiuscole su tutti i tipi, anche importi ("12,5" trova 12,50 €).

### 9. Note libere con liste di spunta · Note > Note · M
**Perché:** nelle foto (21_note_libere) "Libri da leggere" e "Idee per le vacanze" sono liste scritte con le virgole. Liste della spesa, valigie, libri sono l'uso più comune di un'app di note.
**Cosa fare:** nell'editor (`NoteEditor`) le righe che iniziano con "- " o "[ ]" diventano caselle spuntabili; pulsante "Lista" che trasforma le righe. In lista "3 di 7 fatti". Salvato come testo (`[x] ` / `[ ] `) per restare compatibile.
**Fatto quando:** creo una lista della spesa, spunto due voci, chiudo e riapro: le spunte ci sono e in lista leggo "2 di 5".
**Test:** conversione testo ↔ righe spuntabili e conteggio "2 di 5".

---

## Dopo

- **Note > Da fare: meno X, scorri per eliminare** · Note · S. Ogni riga ha una X grande a destra (foto `main/26`, 20_note) dove il pollice tocca per sbaglio: `SwipeToDismissBox` verso sinistra elimina con l'Annulla che c'è già; l'eliminazione resta nel dialog di modifica.
- **Cattura a voce** · Cattura al volo · M. Microfono nel campo di `QuickCaptureActivity` e nei campi di Da fare/Spese (`RecognizerIntent`, italiano, funziona offline sui telefoni Google), il testo passa per `QuickParse`. Fatto quando: dico "domani alle 9 dentista" e il promemoria ha giorno e ora.
- **Date: promemoria per il regalo** · Note > Date · S. Per i compleanni ("Compleanno di …") opzione "Avvisami 7 giorni prima" che crea il promemoria "Regalo per Marco". Fatto quando: la data a 7 giorni compare in Da fare.
- **News: "Leggi dopo"** · News · M. Pressione lunga o icona segnalibro su un articolo lo salva (con il testo pulito di `ArticleCache` per leggerlo offline); filtro "Salvati" accanto alle fonti. Fatto quando: salvo un articolo, spengo la rete e lo leggo.
- **Notifica del mattino: proporla** · Oggi · S. È spenta di default (foto Impostazioni) e pochi la scoprono. Dopo 3 giorni d'uso una riga in Adesso "Vuoi questo riepilogo ogni mattina alle 7:30? Attiva" che la accende in un tocco, una volta sola.
- **Video: schermata vuota** · Media > Video · S. Senza recenti mezza schermata è vuota (foto 33_video): mostrare i video recenti del telefono (MediaStore, ultimi 6 con miniatura) oppure togliere il riquadro grande e tenere solo i due pulsanti.
- **Spese: esporta CSV del mese** · Note > Spese · S. Dal menu del mese "Esporta CSV" (data;descrizione;categoria;importo) con condivisione. Utile per il commercialista e per fogli di calcolo.
- **Note: le sei schede non entrano** · Note · S. Ancora vero nella 1.81 (foto `main/29`: "Pacchi" e "P" tagliate). Nelle foto la fila Da fare / Abitudini / Spese / Note / Pacchi / Date è più larga dello schermo: "Date" o "Da fare" restano tagliate a metà. Valutare con il nuovo design icone + etichetta corta o un numero di scadenze sulla scheda ("Da fare 2"), così si vede dove c'è qualcosa da fare senza aprirle tutte.
- **Pacchi: "arrivato" in un tocco e storico** · Note > Pacchi · S. Oggi il pacco si spunta e sparisce; tenere gli ultimi 10 arrivati in una sezione chiusa "Arrivati", e in Adesso "Pacco in arrivo: Cuffie" con l'azione "Arrivato".
- **Aggiornamenti: un solo interruttore** · Impostazioni · S. "Avvisami delle nuove versioni" compare sia in Impostazioni (dalla 1.80) sia in cima alla pagina Aggiornamenti (foto 41): lasciarlo solo in Impostazioni e in Aggiornamenti mostrare la versione e "Controlla e aggiorna".
- **News: "Niente di nuovo" con notizie di 2 minuti fa** · News · S. Nella foto 10_news l'intestazione dice "Niente di nuovo dall'ultima visita" sopra articoli "2 min fa": al primo avvio (nessuna visita registrata) o quando il conteggio è 0 ma ci sono articoli più recenti dell'ultima visita, verificare `NewsHistory` e dire "Aggiornate 2 min fa" invece di "niente di nuovo".
- **Calendario in Adesso: "Esci alle…"** · Oggi · M. Per il prossimo evento con un luogo, stimare il tempo di viaggio (distanza in linea d'aria dalla posizione del meteo, senza servizi a pagamento) e scrivere "Dentista alle 15 · esci verso le 14:35"; notifica facoltativa 10 minuti prima.
- **Widget piccolo "Da fare"** · Widget · M. Secondo widget 2x2 con i promemoria di oggi spuntabili direttamente (RemoteViews con collezione o checkbox API 31+), senza aprire l'app.

---

## Idee grandi

### A. La sera: "Prepara domani" · Oggi · L
**Perché:** dalle 18 Oggi guarda già a domani, ma solo per leggere. Il momento in cui si decide la giornata dopo è la sera. Ora che Canvas scrive nel calendario (1.81) la sera si può anche pianificare, non solo guardare.
**Passi:**
1. (M) Card "Domani" dalle 18: meteo di domani (min/max, pioggia), impegni del calendario con orario, promemoria in scadenza, date e pacchi.
1b. (S) Da un promemoria senza ora, "Mettilo in agenda domani" che crea l'evento in un'ora libera tra gli impegni (usa `CalendarDays`).
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
- 1.82.0 (in lavorazione) · Player YouTube nativo, login Spotify mobile
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

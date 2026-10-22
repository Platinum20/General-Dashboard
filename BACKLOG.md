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

### 1. Media: "Riprendi" in un tocco · Media (+ Oggi) · M
**Perché:** lo scopo di Media è "riprendi o avvia in un tocco", ma nelle foto (`main/29`, 30_media) la schermata è solo la griglia delle quattro fonti e sotto metà schermo vuoto. Quando nulla suona, per riprendere il video di ieri o la radio di stamattina servono 2-3 tocchi e bisogna ricordarsi dove era. La card "In riproduzione" c'è solo finché il player è vivo.
**Cosa fare:** in cima a `MediaScreen.kt`, quando nulla suona, un blocco scuro "Riprendi" (lo stesso stile del brano in corso) con l'ultima cosa vista o ascoltata: video locale/link con posizione (`VideoLibrary.recent`, ha `positionMs`/`durationMs`) e barra di avanzamento, ultima stazione (`RadioStore.recent`), ultimo video YouTube del player nativo (dalla 1.82 `YouTubeWatchScreen`: salvare id, titolo e secondo). Sotto la griglia, "Recenti": le altre 3-4 voci in una riga ciascuna. Se c'è qualcosa lasciato a metà da meno di 24 h, una riga anche in Adesso di Oggi.
**Fatto quando:** aprendo Media senza nulla in riproduzione la prima cosa è "Riprendi: <titolo> · 12:30 di 45:00" e un tocco riparte da lì; senza cronologia il blocco non c'è.
**Test:** funzione pura che sceglie cosa riprendere dalle cronologie (video con posizione, radio, YouTube; più recente vince; video finito al 95% escluso; più vecchio di 24 h non va in Adesso).

_Il giro fotografico completo (Calendario, scheda evento, player YouTube 1.82, Radio, editor nota, "+") è ora la segnalazione V1 del verificatore: aggiungere anche il player YouTube nativo e il compleanno con l'anno di nascita nei dati di prova._

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
- 1.88.0 · Liste di spunta nelle note (ex 9); Cerca in tutte le Note (ex 8); Spese fisse e abbonamenti con "Domani addebito" in Adesso (ex 5); Abitudini con obiettivo settimanale (ex 7) e fatte/non fatte a colpo d'occhio (ex 13); inviti di Oggi in una riga in fondo (ex 14); eventi del calendario dal "+" (ex 15); Backup ed esportazione in Impostazioni › Dati (ex 2); test dei dati delle Note con file di una versione vecchia (ex 0); V3, V4, V5 del verificatore
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

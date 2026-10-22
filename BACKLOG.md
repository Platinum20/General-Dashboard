# Backlog di Canvas

Tenuto dal "regista del prodotto" (ogni ora) e implementato dal "giro di sviluppo" (ai :51).
Il giro prende le voci dall'alto di **In cima**; quando una voce esce in una versione la sposta in **Fatto** con il numero di versione.

Ogni voce: sezione · dimensione (S = una sessione corta, M = una sessione piena, L = più sessioni).
Regole fisse: niente paywall né fonti a pagamento, niente server o costi, testi e commenti in italiano, stile condiviso dell'app (TopLevelBar, Theme compatto, ElevatedCard, bouncyPress, vibrazioni).

_Ultimo aggiornamento: 2026-10-22 ~10:15 UTC, su main 1.55.0, foto: branch `screenshots`, cartella `main/22`._

---

## In cima

### 1. Media: "Riprendi" in un tocco · Media (+ Oggi) · M
**Perché:** lo scopo di Media è "riprendi o avvia in un tocco", ma oggi la schermata è solo un menu di quattro voci (Video, YouTube, Spotify, Radio): per riprendere il video di ieri servono 3 tocchi e bisogna ricordarsi dove era.
**Cosa fare:** in cima a `MediaScreen.kt` una card "Riprendi" con l'ultima cosa ascoltata o vista: video locale/link con la posizione (`VideoLibrary.recent`, ha già `positionMs`/`durationMs`), ultima stazione radio (`RadioStore.recent`), ultimo video YouTube aperto nel player. Un tocco riparte dal punto giusto; barra di avanzamento per i video. Se c'è qualcosa in pausa da meno di 24 h, una riga anche in "Adesso" di Oggi (`BriefKind` nuovo o `FREE`).
**Fatto quando:** aprendo Media la prima cosa è "Riprendi: <titolo> · 12:30 di 45:00" e un tocco riprende da lì; senza cronologia la card non c'è.

### 2. Backup ed esportazione dei dati · Impostazioni · M
**Perché:** promemoria, abitudini (con le serie), spese, note, pacchi e date stanno in un solo file JSON dentro l'app. Cambiando telefono o reinstallando si perde tutto, e Antonio ci sta mettendo la sua vita quotidiana.
**Cosa fare:** in Impostazioni una sezione "Dati" con "Esporta backup" (file `canvas-backup-AAAA-MM-GG.json` scelto con `ACTION_CREATE_DOCUMENT`, il JSON di `NotesStore.serialize` più le impostazioni utili di `AppSettings`) e "Ripristina da backup" (`ACTION_OPEN_DOCUMENT`, conferma con il riepilogo "142 promemoria, 3 abitudini…", poi sostituisce). Aggiungere `dataExtractionRules`/`fullBackupContent` nel manifest perché il file delle note sia sicuramente nel backup automatico di Android. Test unitario: serialize → parse dà gli stessi dati.
**Fatto quando:** esporto, cancello i dati dell'app, ripristino e ritrovo tutto (anche serie delle abitudini e budget).

### 3. Punto del giorno: titoli che si leggono · Oggi / Punto del giorno · S
**Perché:** nelle foto ogni tema ha per titolo tre parole chiave ("Tir · Sospesa · Protesta", "Bancarotta · Chiedono · Santanchè", "Russo · Morti · Kiev"): sembrano errori e non dicono niente in 5 secondi. Il titolo vero è subito sotto, ripetuto.
**Cosa fare:** in `HomeScreen.kt` (`RecapRow`) e `RecapScreen.kt` usare `theme.headline.title` come titolo del tema e togliere la riga di parole chiave (o tenerla solo come piccola etichetta della fonte più citata). Valutare in `NewsRecap.kt` di scegliere come headline l'articolo più "centrale" (più parole chiave in comune) invece del primo.
**Fatto quando:** nelle foto di Oggi e del Punto del giorno ogni tema si legge come un titolo di giornale, senza la terna di parole.

### 4. "Rimanda" nelle notifiche dei promemoria · Note / notifiche · S
**Perché:** la notifica di un promemoria ha solo "Fatto". Se suona in un momento sbagliato l'unica scelta è ignorarla, e il promemoria diventa "scaduto" in rosso.
**Cosa fare:** in `TaskReminderWorker.kt` due azioni in più: "Tra 1 ora" e "Domani 9:00" (BroadcastReceiver che chiama `NotesStore.setTaskDue`). Per i ricorrenti sposta solo questa occorrenza.
**Fatto quando:** dalla notifica rimando di un'ora, la notifica sparisce e torna dopo un'ora; in Note la scadenza è aggiornata.

### 5. Spese fisse e abbonamenti · Note > Spese (+ Adesso) · M
**Perché:** affitto, bollette, Netflix, Spotify sono la parte più grossa delle spese ma vanno riscritte ogni mese a mano, e il budget non le prevede. Il parser dei ricorrenti esiste già per i promemoria.
**Cosa fare:** nelle Spese "ogni mese il 5 affitto 650" (riusare `Repeat` e `QuickParse`) crea una spesa fissa: si aggiunge da sola il giorno giusto (al primo avvio dopo la data), con un'icona di ripetizione. In cima al mese "Fisse: 720 € · Variabili: 115,90 €"; il budget giornaliero calcola solo sulle variabili. In Adesso, il giorno prima: "Domani addebito: Netflix 12,99 €".
**Fatto quando:** creo una spesa fissa, cambio data del telefono al mese dopo, e la spesa compare da sola nel totale.

### 6. Oggi: la card "Da fare" mostra solo quello che conta · Oggi · S
**Perché:** nelle foto la card elenca tutti e 5 i promemoria, anche quelli senza scadenza, e ripete in piccolo la scheda Note. Oggi dovrebbe rispondere "cosa devo fare adesso?".
**Cosa fare:** in `HomeCards.kt` mostrare scaduti, di oggi e (dalle 18) di domani; poi al massimo 2 senza scadenza; sotto "+ altri N in Note" che apre la scheda. Il campo "Aggiungi un promemoria" resta.
**Fatto quando:** con 10 promemoria la card non supera 5 righe e i primi sono sempre quelli con scadenza più vicina.

### 7. Abitudini con obiettivo settimanale · Note > Abitudini · M
**Perché:** molte abitudini reali non sono quotidiane ("palestra 3 volte a settimana", "chiamare la nonna 1 volta"): oggi l'unica scelta è "ogni giorno", così la serie si rompe e l'app rimprovera alle 17 anche quando sei in pari.
**Cosa fare:** nel `HabitDialog` scelta "Ogni giorno" / "N volte a settimana". Per le settimanali: card con "2 su 3 questa settimana", serie in settimane, e in Adesso (`Briefing.kt`) la riga solo se non si fa più in tempo a raggiungere l'obiettivo. Migrazione: le abitudini esistenti restano quotidiane (campo nuovo con default nel parse).
**Fatto quando:** una abitudine "3 a settimana" spuntata lun-mer non compare più in Adesso fino a lunedì.

### 8. Cerca in Note · Note · M
**Perché:** le Note crescono (promemoria completati, spese, note libere, pacchi): oggi non c'è modo di trovare "quanto ho speso dal meccanico" o la nota con il codice del wifi.
**Cosa fare:** icona lente nella barra di `NotesScreen.kt`; una pagina di ricerca che cerca mentre scrivi in promemoria (anche completati), note, spese (testo e importo), pacchi (titolo e codice) e date; risultati raggruppati per tipo, tocco apre la voce. Senza accenti e maiuscole ("perche" trova "perché").
**Fatto quando:** scrivo "mecc" e trovo la spesa "Tagliando meccanico" e il promemoria "Chiamare il meccanico".

### 9. Note libere con liste di spunta · Note > Note · M
**Perché:** nelle foto la nota "Libri da leggere" è una lista scritta con le virgole. Liste della spesa, valigie, libri sono l'uso più comune di un'app di note.
**Cosa fare:** nell'editor delle note (`NotesScreen.kt`/`NoteEditor`) le righe che iniziano con "- " o "[ ]" diventano caselle spuntabili; pulsante "Lista" nella barra dell'editor che trasforma le righe selezionate. Nella lista delle note si vede "3 di 7 fatti". Salvato come testo semplice (`[x] ` / `[ ] `) così resta compatibile.
**Fatto quando:** creo una lista della spesa, spunto due voci, chiudo e riapro: le spunte ci sono e in lista leggo "2 di 5".

### 10. Oggi: niente cerchio di caricamento sopra il meteo · Oggi · S
**Perché:** nella foto di Oggi all'apertura c'è un cerchio di caricamento appoggiato sopra la card del meteo, accanto a "Roma": sembra un difetto. È l'indicatore del "trascina per aggiornare" acceso dal caricamento automatico (`isRefreshing = weather.loading || recap.refreshing`, `HomeScreen.kt` ~199).
**Cosa fare:** accendere l'indicatore del pull solo quando il caricamento parte da un trascinamento; per il caricamento automatico basta il piccolo indicatore già presente dentro la card (`HomeScreen.kt` ~427). Stessa cosa da controllare in News e Meteo.
**Fatto quando:** nelle foto di Oggi non c'è più il cerchio sopra la card; trascinando in basso l'indicatore compare come prima.

### 11. Agenda: "Non ora" sulla richiesta del calendario · Oggi · S
**Perché:** senza permesso del calendario la card "Agenda di oggi" con "Consenti" resta lì per sempre al secondo posto: occupa spazio in una schermata che deve mostrare solo cose utili.
**Cosa fare:** in `HomeCards.kt` accanto a "Consenti" un "Non ora" che nasconde la card (aggiunge `HomeCard.CALENDAR` a `hidden`, riattivabile da Personalizza) con un messaggio "La riaccendi da Personalizza". Se il permesso viene negato due volte, idem automaticamente.
**Fatto quando:** tocco "Non ora" e la card sparisce; in Personalizza l'interruttore dell'Agenda è spento.

### 12. Note > Da fare: meno X, scorri per eliminare · Note · S
**Perché:** ogni riga ha una X grande a destra (foto 20_note): la lista sembra un elenco di cose da cancellare e la X sta dove il pollice tocca per sbaglio.
**Cosa fare:** in `NotesScreen.kt` (`TasksTab`) togliere la X dalle righe e usare `SwipeToDismissBox` (verso sinistra elimina, con l'"Annulla" che c'è già); l'eliminazione resta anche nel dialog di modifica.
**Fatto quando:** le righe dei promemoria non hanno più la X e uno scorrimento le elimina con Annulla.

---

## Dopo

- **Cattura a voce** · Cattura al volo · M. Microfono nel campo di `QuickCaptureActivity` e nei campi di Da fare/Spese (`RecognizerIntent`, italiano, funziona offline sui telefoni Google), il testo passa per `QuickParse`. Fatto quando: dico "domani alle 9 dentista" e il promemoria ha giorno e ora.
- **Date: promemoria per il regalo** · Note > Date · S. Per i compleanni ("Compleanno di …") opzione "Avvisami 7 giorni prima" che crea il promemoria "Regalo per Marco". Fatto quando: la data a 7 giorni compare in Da fare.
- **News: "Leggi dopo"** · News · M. Pressione lunga o icona segnalibro su un articolo lo salva (con il testo pulito di `ArticleCache` per leggerlo offline); filtro "Salvati" accanto alle fonti. Fatto quando: salvo un articolo, spengo la rete e lo leggo.
- **Notifica del mattino: proporla** · Oggi · S. È spenta di default (foto Impostazioni) e pochi la scoprono. Dopo 3 giorni d'uso una riga in Adesso "Vuoi questo riepilogo ogni mattina alle 7:30? Attiva" che la accende in un tocco, una volta sola.
- **Video: schermata vuota** · Media > Video · S. Senza recenti mezza schermata è vuota (foto 33_video): mostrare i video recenti del telefono (MediaStore, ultimi 6 con miniatura) oppure togliere il riquadro grande e tenere solo i due pulsanti.
- **Spese: esporta CSV del mese** · Note > Spese · S. Dal menu del mese "Esporta CSV" (data;descrizione;categoria;importo) con condivisione. Utile per il commercialista e per fogli di calcolo.
- **Giro fotografico più completo** · robustezza · S. `ScreenshotTour` non fotografa Meteo, Radio, Lettore, Cattura al volo, dialog di modifica promemoria e News senza rete: aggiungerli, così il regista vede tutta l'app.
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

### D. Spese: andamento nel tempo · Note > Spese · L
1. (M) Grafico degli ultimi 6 mesi (barre, Canvas Compose, senza librerie pesanti) con budget come linea.
2. (S) Tocco su una categoria: l'elenco delle sue spese del mese e il confronto con il mese prima.
3. (S) Categorie modificabili e regola "impara": se correggo la categoria di "Tigotà", le prossime spese con quella parola la usano.

---

## Fatto

Dalle versioni recenti (dettagli in `CHANGELOG.md` su main):
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

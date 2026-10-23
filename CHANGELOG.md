# Changelog

## 1.132.0
**Finestre di vetro**
- Il vetro della barra arriva anche alle finestre che si aprono sopra la pagina (conferme, scelta di data e ora, token, licenze, backup e le altre): da Android 12 la pagina dietro si sfoca e la finestra la lascia intravedere, con un filo di luce sul bordo alto. Se il telefono spegne la sfocatura (risparmio energetico) la finestra torna piena, sempre leggibile
- Il vetro che c'era resta dov'era: barra, +, mini player, fumetto del + e della voce

## 1.130.0
**Media › Continua**
- I titoli e le righe sotto delle tessere di "Continua" non vengono più tagliati dagli angoli arrotondati: il testo sta un po' più dentro

## 1.129.0
**Niente pulsanti in alto, tutto a portata di gesto**
- Via la lente e l'ingranaggio in alto. Trascinando giù dalla cima di Oggi, News, Note o Media scende una lente: lasciando, si apre Cerca ovunque. Le pagine si aggiornano da sole (all'ingresso, al ritorno nell'app e ogni pochi minuti), non serve più tirare per ricaricare
- Impostazioni divise come la barra in basso: Oggi, News, Note, Media e Tutta l'app, con i chip in alto per saltare a una parte. Si aprono tenendo premuta una sezione della barra, già alla sua parte
- Cerca ovunque cerca davvero dappertutto: oltre a note, agenda e notizie anche brani, album, artisti e playlist di Spotify, video di YouTube, radio e podcast. I chip in alto restringono a una parte; toccando un brano, una radio o un podcast parte nel player dell'app, un video si apre nel suo player
- Il + toccato apre lo stesso fumetto di vetro della voce, con dentro il campo per scrivere: sale con la tastiera, mostra cosa diventerà ("Diventa: spesa · 12,50 €") e, inviato, vola alle Note come le cose dette a voce
- La voce parte anche solo trascinando il +, senza tenerlo premuto prima; trascinando subito in su si blocca
- **Che canzone è?** Dettando con il microfono bloccato c'è "♪ Canzone"; e se tenendo premuto il + si sente musica ma nessuna parola, Canvas prova da solo. Riconosciuta, parte su Spotify nel player dell'app e finisce nella playlist "Sentite da Canvas". Si attiva in Impostazioni › Media › Riconosci canzoni (serve il token di AudD; per la playlist Spotify chiede di accedere di nuovo una volta)

## 1.128.0
**Note più pulite**
- Niente più campo fisso in cima: per scrivere c'è il + della barra, lo stesso di tutta l'app (toccalo per scrivere, tienilo premuto per dettare). Canvas capisce da solo che cos'è e lo mette nella parte giusta
- A destra delle pillole due tastini: lo scontrino (fotografalo e si apre il + già compilato con totale e negozio) e la lampadina con le idee da aggiungere (compleanni dalla rubrica, abitudini, date), che non occupano più il fondo della pagina
- I pacchi in arrivo sono conti alla rovescia come i compleanni: a destra "Oggi", "Domani" o quanti giorni mancano (in rosso se è in ritardo). Tieni premuto per segnarlo arrivato
- Quando salvi dal +, il pallino atterra proprio sulla sezione giusta delle Note (promemoria, note, abitudini, in arrivo, spese)

## 1.126.0
**Un player solo, davvero**
- Suona una cosa alla volta: quando parte un video, una canzone, la radio o un podcast, tutto il resto si mette in pausa, compreso Spotify che suona nell'app di Spotify o su un altro dispositivo
- Spotify: la pausa non dà più errore e il brano non sparisce dal player; "Riproduci" lo fa ripartire dal punto esatto anche se Spotify nel frattempo l'aveva dimenticato
- Notifica con copertina e comandi anche per i video e la musica di YouTube (prima non compariva)

**Player più bello**
- Dietro al player la copertina sfocata che sfuma nello sfondo, copertina grande con ombra e la pillola della fonte (Spotify, YouTube Music, Radio, Podcast)
- Spotify nell'app ha la copertina nitida (prima prendeva la più piccola), la barra del tempo che scorre e si può trascinare per saltare

**Continua da dove eri, per tutto**
- Il blocco in cima a Media e la fila "Continua" tengono l'ultimo media di qualunque tipo: video, musica di YouTube, brani di Spotify (con l'album o la playlist da cui venivano), podcast e radio. Un tocco li fa ripartire dal punto in cui eri

**Video e mini player**
- In fondo a Media non c'è più la riga del blocco pubblicità: si regola nelle Impostazioni
- Nei controlli del video restano la rotella (qualità, velocità, sottotitoli, ripeti) e il prossimo video: via il tasto CC e la pillola della qualità, che erano doppioni
- Il mini player si lancia: uno scatto verso l'altro lato (o un piccolo spostamento) basta per mandarlo in alto o in basso. Prima la velocità del lancio veniva misurata male e bisognava trascinarlo fino in fondo; stesso problema corretto per il video trascinato giù

**Sotto il cofano**
- Test: ResumeAnyMediaTest, YtPlayerStateTest (musica che riparte come musica)

## 1.125.0
**Salvato, e si vede dove va**
- Dopo aver salvato dal + (scritto o detto) compare un fumetto di vetro nello stile di Canvas, con l'icona del tipo, cosa è stato salvato ("Spesa: 12,50 € · Cibo") e "Annulla" se Canvas ha capito male
- Dopo un attimo il fumetto diventa un pallino che vola ad arco fin dove la cosa è finita: la scheda Note in basso (gli eventi su Oggi, dove c'è l'agenda), che fa un piccolo sobbalzo quando arriva. Se sei già nelle Note vola alla sezione giusta

## 1.124.0
**Torna il carattere di prima**
- Via il serif della 1.123.0: titoli e testo tornano in Inter e Inter Display, con le dimensioni di prima

## 1.123.0
**Il carattere di Claude**
- Titoli e testo di tutta l'app ora sono in un serif elegante come quello di Claude (Source Serif 4, il carattere libero più simile). Pulsanti, etichette e chip restano in un sans pulito, come nell'app di Claude
- Il testo è un filo più grande perché il serif si legga bene anche piccolo

## 1.122.0
**Oggi › La tua settimana**
- In "Cosa arriva" ci sono anche gli impegni del calendario della settimana nuova ("Mar 27 9:30 · Dentista"), mescolati a compleanni e addebiti fissi in ordine di giorno; toccandoli si apre il Calendario. Un evento di più giorni compare una volta sola, al massimo sei impegni
- I promemoria ricorrenti contano: ogni volta che li spunti la spunta resta segnata (passano comunque alla volta dopo), così "Buttare la plastica" fatto ogni martedì finisce tra le cose fatte della settimana

**Meteo**
- La rotella dell'aggiornamento accanto a "ADESSO · ROMA" resta al massimo 10 secondi: poi, e quando i dati sono vecchi, la riga dice da quando sono ("· 5 MIN FA", "· ALLE 9:40"), anche in Oggi
- Al posto dell'icona che non si capiva, in alto c'è il pulsante "Località"

**Sotto il cofano**
- Il giro fotografico usa il fuso di Roma: saluto, card del giorno e "Domani" sono quelli che vedi sul telefono
- Il giro fotografico trova di nuovo il + della barra (la foto del pannello mancava)
- Test nuovi: WeekReviewEventsTest (impegni nella settimana nuova, ordine con le date, eventi di più giorni, limite, ricorrenti spuntati, salvataggio delle spunte), FreshnessTest (da quando è il meteo)

## 1.120.0
**Player più fluido**
- Trascinando giù un video di YouTube tutta la pagina scende con il dito, si rimpicciolisce e si arrotonda; lasciata (o lanciata) in basso scivola via e il video continua nel mini player, altrimenti torna su con una molla. Prima il video scendeva da solo e poi la pagina usciva di lato
- Il video si apre salendo dal basso, e chiudendolo ridiscende
- La barra del tempo del player grande e del mini player avanza fluida da sola mentre suona, per ogni fonte (anche Spotify su un altro dispositivo); la barra del video segue sempre il player giusto, anche dopo aver chiuso e riaperto un video

## 1.119.0
**Note: scrivi e basta**
- Niente più pillole per scegliere il tipo: Canvas decide da solo se è un promemoria, una spesa, un'abitudine, una data, un pacco, un evento o una nota. Sotto il campo una riga dice cosa diventerà ("Spesa · 12,50 € · Cibo", "Promemoria · domani 09:00")
- Dopo il salvataggio "Spesa: 12,50 € · Annulla": se Canvas ha capito male, un tocco e sparisce

**Le pillole in alto hanno un senso**
- Riassumono ogni parte: "Da fare · 5", "Abitudini · 1/2", "In arrivo · 2", "Note · 4", "Spese · 115,90 €". Compaiono solo le parti che hanno qualcosa
- Toccandone una la pagina mostra solo quella parte (Spese apre mese, budget e spese fisse); un altro tocco o "indietro" torna a tutto

**Sotto il cofano**
- CaptureResult.undo per l'"Annulla" (lo usano anche il + e la dettatura). Test: notesChips in NotesUiLogicTest

## 1.118.0
**Il + decide da solo**
- Niente più pillole da scegliere nel +: scrivi e sotto compare "Diventa: spesa" (o promemoria, nota, abitudine, data, pacco, evento in calendario) con la sua icona, che cambia mentre scrivi. Salva e finisce al posto giusto
- Detto a voce, si salva da solo nel tipo giusto, senza finestre: in basso compare cosa è stato salvato ("Spesa: 12,50 € · Cibo"); per gli eventi c'è anche "Annulla". Il dialogo si apre solo se manca qualcosa (l'importo, il giorno, il permesso del calendario)

**Microfono bloccabile, come WhatsApp**
- Mentre tieni premuto il +, sopra compare un lucchetto: trascina il dito verso l'alto per bloccare il microfono e puoi lasciare. Continua ad ascoltare anche con le pause; il + diventa ➤ e un tocco inserisce quello che hai detto. "Annulla" nel fumetto butta tutto

**La voce si vede**
- Mentre detti, nel fumetto sopra il + ci sono le barre che seguono la voce e sotto il testo che Canvas sta capendo. Se non senti niente dopo un paio di secondi c'è scritto "Nessun suono rilevato", e resta lì un attimo anche quando rilasci senza aver detto niente (niente più avviso a parte)

**Icona**
- Colori invertiti: fondo arancione e goccia blu

**In alto a destra, solo due cerchi**
- In ogni sezione (Oggi, News, Note, Media) in alto a destra ci sono sempre e solo la lente di Cerca ovunque e l'ingranaggio delle Impostazioni, nello stesso posto
- News: niente più pulsante Aggiorna, si aggiorna tirando giù la lista. Oggi: "Personalizza" sta nelle Impostazioni (Card di Oggi). Note: la lente è quella di Cerca ovunque, che trova anche tutto quello che c'è nelle Note

**Sotto il cofano**
- Il giro fotografico trova di nuovo il + (06_aggiungi) e fotografa Cerca ovunque e Card di Oggi

## 1.117.0
**Il player di YouTube, con tutto sul video**
- Le impostazioni stanno nei controlli del video: la rotella apre Qualità, Velocità, Sottotitoli e Ripeti; il tasto CC accende e sceglie i sottotitoli; accanto allo schermo intero la pillola con la qualità attuale ("Auto · 720p") la cambia al volo. Mentre un menu è aperto i controlli non spariscono
- Sotto il titolo tre tasti: **Mi piace** (con il numero), **Commenti** (apre e scorre ai commenti) e **Condividi**. I Mi piace restano nell'app (il collegamento con YouTube è in sola lettura) e compaiono in cima alla scheda Mi piace di Video

**Mini player a barra**
- Il mini player è una barra larga quanto la barra in basso più il +: si trascina e si aggancia in alto o in basso, con una spinta veloce va dove lo lanci

**Sotto il cofano**
- MediaClickTour: nel giro fotografico tocca uno per uno tutti gli elementi di Media, Musica, Video e Radio e podcast e segnala quelli che non fanno niente (build/tour/collaudo-media.md)
- Il + della barra ha di nuovo nome e tocco sullo stesso elemento (per TalkBack e per il giro fotografico)
- Test: YtFavoritesTest
## 1.116.0
**Note › Date: i compleanni dalla rubrica**
- Tra le idee delle Note c'è "Compleanni dai contatti": Canvas chiede il permesso della rubrica solo lì, legge compleanni e anniversari e li mostra in un elenco con le caselle, già spuntate quelle che mancano. Un tocco su "Importa" e diventano date "ogni anno"; con "Annulla" si tolgono tutte
- Se la rubrica sa l'anno di nascita, la data lo tiene e "In arrivo" dice "compie 30 anni"; quelle senza anno (o con l'anno finto 1604 dei telefoni Apple) restano senza età
- Niente doppioni: un compleanno già scritto a mano ("Compleanno Marta" il 12 marzo) o la stessa persona salvata su due account non si ripetono. L'invito sparisce quando hai almeno tre date di ogni anno

**Note › Spese: la foto dello scontrino**
- Nel campo in cima alle Note, quando è vuoto, c'è lo scontrino: fai la foto e Canvas legge il totale e il negozio, e scrive nel campo "63,40 Conad Superstore" già come spesa, con la categoria giusta. Controlli e salvi
- Il testo si legge sul telefono, senza rete e senza servizi esterni; la foto non resta salvata
- Trova "Totale complessivo", "Importo pagato" e "Totale" anche quando il prezzo sta sulla riga sotto, ignora subtotale, sconti, IVA, contanti dati e resto, toglie "S.p.A." e "Srl" dal nome del negozio

**Sotto il cofano**
- Test nuovi: ContactDatesTest (formati delle date della rubrica, anno sconosciuto, 29 febbraio, doppioni, lettura dal provider dei contatti), ReceiptTest (supermercato, farmacia, benzinaio, ristorante con migliaia, bar senza totale, data scambiata per prezzo)

## 1.114.0
**Widget**
- Nuovo widget **"Canvas · Da fare"**: i promemoria scaduti e di oggi, poi quelli senza scadenza, ognuno con un cerchio da toccare. Il tocco lo segna fatto senza aprire l'app (i ricorrenti passano alla volta dopo, come in Note) e sparisce anche da Oggi. Gli scaduti hanno il cerchio e l'ora in rosso, "+ Aggiungi" apre il + al volo, "+ altri 3 in Note" dice quanti non stanno. Si ridimensiona: più è alto, più righe mostra. Vuoto dice "Niente da fare oggi" con il prossimo promemoria
- Nel widget principale le righe di "Adesso" hanno il bottoncino come in Oggi: **pacco arrivato** e **abitudine fatta** con un tocco dalla schermata Home; la riga sparisce subito

**Media › Musica**
- **Nuove uscite**: in cima a Musica gli album e i singoli usciti nelle ultime 4 settimane dagli artisti che segui su Spotify, con copertina, artista e "3 giorni fa". Un pallino segna quelle che non hai ancora aperto ("Nuove uscite · 2 da ascoltare"); un tocco apre l'album da ascoltare. Si cercano al massimo una volta al giorno e restano salvate per l'apertura successiva

**Sotto il cofano**
- Test nuovi: TasksWidgetRowsTest e TasksWidgetActionTest (righe del widget, spunta e ricorrenti), WidgetBriefTest (azioni rapide del widget principale, formato vecchio delle righe), NewReleasesTest (date con precisione giorno/mese/anno, doppioni tra artisti, uscite future, pagine degli artisti seguiti, con risposte vere di Spotify salvate)

## 1.112.0
**Note, una pagina sola**
- Niente più schede: promemoria, abitudini, date, pacchi, note e spese stanno in una lista, in ordine di quando contano (Scaduti, Oggi, Abitudini, In arrivo, Prossimi giorni, Senza scadenza, Note, Spese)
- In cima un campo solo: scrivi e Canvas capisce cos'è ("12,50 pizza" è una spesa, "ogni giorno leggere" un'abitudine, "compleanno di Marta 12 marzo" una data, un codice di tracciamento un pacco, un testo lungo una nota). Se sbaglia, tocchi la pillola del tipo giusto prima di salvare
- "In arrivo": date e pacchi mescolati dal più vicino; i consegnati in fondo
- Note: si vedono le fissate e le ultime quattro, "Tutte" per il resto. Spese: il totale del mese e il budget, "Dettagli" apre mese, categorie e spese fisse
- Il + della barra (e la dettatura) conosce anche Abitudine e Data; "palestra 3 volte a settimana alle 19" diventa un'abitudine con l'avviso alle 19

**Sotto il cofano**
- CaptureSave: un solo salvataggio per il + e per il campo delle Note. Test nuovi: CaptureSaveTest, CaptureGuessTest (abitudini e date), NotesUiLogicTest (ordine di In arrivo)

## 1.111.0
**Il + a voce, come i vocali di WhatsApp**
- Tieni premuto il +: diventa un microfono che si ingrandisce e pulsa con la voce, e sopra la barra compare quello che stai dicendo. Rilasci e si apre il dialogo con il testo e il tipo già riconosciuto (spesa, pacco, evento, nota o promemoria)
- Niente più finestra di Google: il riconoscimento del telefono lavora nascosto. La prima volta Canvas chiede il permesso del microfono

## 1.110.0
**Media, rifatta da capo**
- Tre sezioni: **Musica**, **Video** e **Radio e podcast**. In alto il blocco colorato con quello che suona adesso (qualunque fonte) o da dove riprendere; sotto "Continua" con le anteprime, i nuovi episodi e le tue radio da far partire con un tocco
- **Musica** mette insieme Spotify e YouTube Music: i "Mi piace" di YouTube Music accanto ai tuoi ascolti di Spotify, e una ricerca sola che cerca su entrambi. I brani di YouTube si ascoltano in solo audio (meno dati), con "Guarda il video" per passare al video dallo stesso punto
- **Video** è YouTube più i video del telefono, i file e i link ("Sul telefono"), al posto della vecchia sezione Video
- **Radio e podcast** in una pagina sola, con le pillole Podcast e Radio. I podcast salvati su Spotify arrivano da soli insieme a quelli che segui: se hanno un feed pubblico si ascoltano nel player dell'app (con il punto salvato), se sono solo su Spotify con il riproduttore di Spotify

**Un player solo**
- Un unico mini player per tutto (YouTube, musica, Spotify, radio, podcast), con il tempo che scorre; toccandolo si apre il player grande, uguale per tutte le fonti: copertina, barra del tempo da trascinare, comandi e timer di spegnimento (che ora ferma qualunque cosa)
- Le barre del player dentro Radio, Podcast e Spotify sono sparite: c'è solo il mini player

**YouTube**
- Con i sottotitoli accesi il video ora parte sempre: i sottotitoli si scaricano prima e, se YouTube non li dà, compare un avviso e il video continua senza

**Meteo**
- La schermata del Meteo si apre con lo stesso blocco di "Adesso" che c'è in Oggi (luogo, temperatura, cielo, massima e minima), così la card e la pagina sono la stessa cosa; sotto, la temperatura percepita

**Sotto il cofano**
- Test nuovi: MediaUnifiedTest (player unico, podcast di Spotify senza doppioni), YtCaptionsTest; la verifica dal vivo di YouTube controlla anche i sottotitoli
## 1.109.0
**Oggi più semplice**
- "La giornata": Agenda, Da fare e In arrivo diventano una card sola, in ordine di orario. In cima le cose di tutto il giorno e le date di oggi, poi impegni e promemoria mescolati per ora (gli scaduti per primi), poi i promemoria senza ora e infine compleanni e date dei prossimi giorni ("tra 3 giorni"). I promemoria si spuntano da qui con "Annulla", impegni e date si aprono con un tocco
- Cerca ovunque: la lente in alto in Oggi trova insieme promemoria, note, spese, pacchi, date, abitudini, impegni del calendario (un mese indietro e tre avanti) e notizie già scaricate
- "Non ora" sull'invito del calendario non nasconde più tutta la card: smette solo di chiederlo

**Il +**
- Tenendo premuto il + si detta a voce: il testo passa dallo stesso riconoscimento (spesa, pacco, evento, nota o promemoria) e si conferma con Salva

**Sotto il cofano**
- Test nuovi: DayTimelineTest (ordine della giornata), EverywhereSearchTest (impegni e notizie)

## 1.108.0
**Note, in quattro schede invece di sei**
- **Da fare**: promemoria e, sotto, le abitudini di ogni giorno ("Abitudini · oggi 2 su 4", con "Nuova abitudine"); i completati restano in fondo
- **In arrivo**: date (compleanni, viaggi, scadenze) e pacchi in una lista sola; se un pacco arriva entro tre giorni o è in ritardo, i pacchi vanno sopra. Il + chiede se aggiungere una data o un pacco
- **Spese** e **Note** come prima. Le pillole in cima ora stanno tutte in vista, senza scorrere
- Dalla ricerca, un pacco apre la scheda "In arrivo"

**Sotto il cofano**
- NotesUiLogicTest: quando i pacchi vanno sopra le date

## 1.107.0
**Le funzioni si parlano**
- Il "+" capisce da solo cosa scrivi: "12,50 pizza" diventa una spesa, un codice di tracciamento un pacco, "venerdì alle 20 cena da Luca" un evento, un testo lungo una nota; il resto resta un promemoria. Toccando un tipo lo scegli tu e non cambia più
- Meteo dentro gli impegni: se all'ora di un impegno è probabile la pioggia, la riga lo dice ("Alle 18:00: Calcetto · pioggia 70%"), in Adesso e nella card di domani
- Nel lettore delle News c'è "Leggi dopo": salva l'articolo in Note con titolo e link

## 1.106.0
**Oggi**
- Nuova card "La tua settimana", dalla domenica alle 17 al lunedì a mezzogiorno: una frase in cima ("Settimana buona: 12 cose fatte, palestra 3 su 3, spese -18%"), poi "Com'è andata" (promemoria chiusi, ogni abitudine con "5 su 7" o "3 su 3", spese a mano della settimana contro la media delle 4 prima e la categoria cresciuta di più, pacchi arrivati) e "Cosa arriva" (compleanni e date della settimana nuova, addebiti fissi). "Fatto" la chiude fino alla domenica dopo; ogni riga apre Note
- Il punto del giorno si ascolta anche da Oggi: cuffie accanto a "Tutti" nella card, un altro tocco ferma la lettura

**Note › Da fare**
- I promemoria ricordano quando li hai spuntati: serve a "La tua settimana" (quelli chiusi prima di questa versione non contano)

**Media › Spotify**
- Playlist che Spotify non fa elencare: l'avviso vale solo per quel caricamento. Ricaricando senza rete si vede "Spotify non è raggiungibile" e restano copertina, titolo e "Riproduci", invece del vecchio avviso a tutto schermo; se poi i brani arrivano, l'avviso sparisce
- L'avviso non dice più per certo "non l'hai creata tu": Spotify risponde allo stesso modo anche quando l'account non è tra gli utenti dell'app
- Il numero di brani sotto le playlist legge anche il nuovo campo di Spotify ("items"), così non sparisce

**Sotto il cofano**
- Test nuovi: WeekReviewTest (finestra della card, settimana completa, settimana vuota, spese senza storia, frase), SpotifyParseTest (brani in "item" e in "track", album dell'artista, numero di brani, avviso dopo un errore di rete)

## 1.105.0
**Note › Abitudini**
- "Ricordamelo alle…" nella scheda dell'abitudine: scegli un'ora e, se a quell'ora non l'hai ancora fatta, arriva una notifica con la serie in gioco ("5 di fila: non perdere la serie") o, per le settimanali, "1 su 3 questa settimana"
- Dalla notifica "Fatto" la segna senza aprire l'app, "Più tardi" la ripropone tra un'ora. Nessun avviso se è già fatta o se l'obiettivo della settimana è raggiunto (si riparte il lunedì); gli avvisi restano anche dopo il riavvio del telefono

**Oggi**
- Il saluto segue l'orologio: lasciando l'app aperta la notte, la mattina dice "Buongiorno" e non più "Buonasera"; anche la data in cima cambia a mezzanotte
- Fino alle 6 del mattino la card "Oggi · …" dice com'è la mattina: pioggia ("pioggia dalle 8") e primo impegno o promemoria. Prima spariva alle 3
- Con la card del giorno visibile, Adesso non ripete massima, minima e pioggia: resta solo il cielo di adesso
- La riga del meteo di Adesso non va più a capo a metà ("pioggia / 48%"): parole e numeri restano insieme

**Meteo**
- "Prevalentemente sereno" ha il sole (o la luna di notte) invece di una nuvola piena, nel Meteo, in Adesso, nel widget e nella notifica del mattino

**Sotto il cofano**
- Test nuovi: HabitReminderTest, GreetingTest, WeatherCodesTest; TomorrowTest con la mattina presto (3:30, 5:30, 7:00)

## 1.104.0
**Più veloce all'apertura**
- News e Oggi: le ultime notizie restano salvate sul telefono e si vedono subito appena apri l'app, mentre arrivano quelle nuove. Al primo avvio compaiono fonte per fonte, senza aspettare il sito più lento
- News: gli articoli già controllati (leggibili o a pagamento) si ricordano per tre giorni, quindi la lista non riparte da "Preparo gli articoli…" a ogni apertura; quelli nuovi si controllano 6 alla volta invece di 4
- YouTube (Home, Iscrizioni, Cronologia, Playlist, Mi piace, Guarda più tardi) e Spotify (Per te, Playlist, Libreria) mostrano subito l'ultimo feed salvato e si aggiornano in sottofondo. Scollegando l'account i dati salvati si cancellano
- Spotify: le parti di "Per te" e della Libreria si scaricano tutte insieme, non una dopo l'altra

**Più stabile**
- Un sito di notizie lento o irraggiungibile non blocca più le altre per decine di secondi (tempi di attesa più brevi, niente secondo tentativo inutile quando manca la rete)
- L'avvio non programma più i lavori in background sul thread principale: il primo schermo arriva prima

**Sotto il cofano**
- Test nuovi: OfflineCacheTest. La verifica dal vivo misura i tempi delle notizie (sul server di prova: prime dopo 0,3 s, tutte dopo 1,2 s)

## 1.103.0
**News**
- Niente più riquadri grigi: la prima notizia senza foto (o con la foto che non si carica) diventa una card solo testo, e le miniature che non arrivano spariscono invece di lasciare un quadrato vuoto. Loghi e immagini segnaposto dei feed non si mostrano più come foto
- La fonte che non risponde non è più una riga in cima a tutto: c'è un chip discreto in coda ai filtri ("1 fonte non risponde"), toccandolo si leggono i nomi

**Note › Pacchi**
- Con pochi pacchi, sotto la lista c'è "Per aggiungerne": "Incolla un codice di tracciamento" prende il codice dagli appunti (anche da una mail copiata intera) e apre il pacco già compilato col corriere riconosciuto; "Prendili dalle notifiche" porta a Compila da solo se è spento; e come condividere la mail di spedizione verso Canvas

**Media**
- Al primo uso, quando non c'è niente da riprendere, "Per iniziare" propone quattro radio popolari da far partire con un tocco (mai una che hai già tra recenti o preferite), con "Tutte le radio" per vederne altre

**Widget**
- Riga del meteo e numero di notizie calcolati a parte e coperti da test

**Sotto il cofano**
- Il giro fotografico ora fallisce ed elenca le schermate attese che non trova (prima le saltava in silenzio), scorre la lista fino alle voci fuori schermo (Compila da solo, Aggiornamenti), aspetta il punto del giorno anche al buio e fotografa anche i dialoghi, così la foto del + della barra non è più uguale a quella di Oggi
- Test nuovi: NewsLayoutTest, ParcelHintsTest, MediaStartersTest, WidgetRowsTest
## 1.102.0
Revisione dei doppioni: ogni cosa compare in un posto solo.

**Oggi**
- L'Agenda non si trasforma più in "Domani" la sera: finiti gli impegni di oggi sparisce, perché domani c'è già la sua card
- Tolta la card "Scorciatoie": ripeteva la barra in basso, il Meteo (si apre toccando Adesso) e "Il punto del giorno" (Tutti)

**Media**
- Video: al posto del pulsante "Dal telefono", del pulsante "Da un link" e della grande card "Scegli cosa guardare" c'è una sola card con "Scegli un file" e "Apri un link"; "Recenti" ha l'etichetta come le altre sezioni, e la pagina ha l'intestazione grande come Meteo e YouTube
- Spotify: la pagina di accesso non mostra più il campo "Client ID" da sviluppatore; il collegamento ufficiale è un pulsante discreto, come in YouTube. Intestazione grande come le altre pagine
- Radio: intestazione grande come le altre pagine

**Sotto il cofano**
- Tolto il vecchio player web di YouTube e Spotify (la pagina del sito in un riquadro, la sua bolla e l'avvio automatico): non era più raggiungibile da nessuna parte, YouTube suona solo nel player di Canvas e Spotify nel suo riproduttore nascosto

## 1.101.0
**Media › Podcast (nuovo)**
- Quinta fonte in Media: cerchi un podcast (ricerca gratuita di iTunes, senza account), lo apri e vedi gli episodi con data, durata e una riga di descrizione, tutto dentro l'app
- "Segui": i podcast che segui stanno in cima alla pagina e sotto ci sono i loro ultimi episodi tutti insieme, dal più nuovo
- Un tocco su un episodio lo fa suonare nello stesso player della radio (notifica, schermata di blocco, cuffie), con 10 secondi indietro, 30 avanti e la barra del tempo
- Ogni episodio riparte dal punto in cui eri, anche dopo aver chiuso l'app; nella lista si vede "restano 12 min" con la barra, e "ascoltato" quando l'hai finito
- Al primo uso la pagina non è vuota: ricerche pronte ("Il Post", "Muschio Selvaggio", "Geopop"…)
- Radio e podcast si passano il player senza interrompersi a vicenda per sbaglio

**Media › Radio**
- Sveglia con la radio: nella pagina Radio scegli ora, giorni (lun-ven o tutti) e la stazione, e la mattina parte da sola anche ad app chiusa. Comincia piano e sale in 30 secondi; se dopo 20 secondi la radio non suona (niente rete) parte la sveglia del telefono, mai una sveglia muta
- La notifica della sveglia ha "Ferma" e "Ancora 10 min"; la sveglia si vede nella barra di stato come le altre e resta programmata anche dopo il riavvio del telefono

**News › Il punto del giorno**
- "Ascolta" (icona delle cuffie in alto): la voce italiana del telefono legge il punto come un notiziario breve, un'apertura con l'ora e poi una frase per tema, senza indirizzi né simboli. La card "In ascolto" dice a che tema sei, con Successivo e Ferma
- Se sul telefono manca la voce italiana, un messaggio dice dove aggiungerla invece di non fare nulla; la voce si ferma da sola quando parte musica, radio, un podcast o un video

**Note › Promemoria**
- "Ogni mese" il 31 non scivola più al 28 per sempre dopo febbraio: torna al 31 (o all'ultimo giorno del mese); "ogni anno" il 29 febbraio torna al 29 negli anni bisestili

**Widget**
- La riga dei promemoria indica il primo in ordine di orario

**Sotto il cofano**
- Test nuovi: PodcastFeedTest (due feed RSS e una risposta di iTunes salvati nei test: durate "1:02:03", "62:03" e secondi, date con fusi diversi, descrizioni HTML, episodi senza audio, doppioni), RecapReaderTest, RadioAlarmTest (anche il cambio dell'ora legale), RepeatEndOfMonthTest, WidgetLinesTest
- Giro fotografico: nuove foto di Radio e Podcast
## 1.98.0
**Oggi › Adesso**
- Azioni rapide: accanto alle righe di Adesso c'è un bottoncino tondo. "Scaduto: Bolletta" si segna fatto, "Pacco in arrivo: Cuffie" si segna arrivato, un'abitudine che manca si spunta, e con più abitudini si apre l'elenco da spuntare. Tutto senza aprire Note, con la vibrazione e "Annulla" per qualche secondo
- Un promemoria scaduto da solo dice cos'è ("Scaduto: Bolletta") invece di "1 promemoria scaduto"
- Le abitudini con nomi lunghi diventano "Ancora da fare: 2 abitudini" invece di un nome tagliato a metà
- La sera, quando sotto c'è la card Domani, Adesso non ripete più meteo, impegni, date, pacchi e addebiti di domani; e la riga meteo di domani ha l'icona del tempo previsto (pioggia se piove), non sempre il sole

**Oggi › Domani e Da fare**
- La frase in cima alla card Domani compare anche senza calendario, con il primo promemoria che ha un orario ("Domani 9:00 Chiamare il commercialista")
- Dopo mezzanotte la card si chiama "Oggi · venerdì 23" e la frase non dice più "Domani" per il giorno già iniziato
- Da fare non ripete i promemoria di domani quando la card Domani è visibile

**Note › Abitudini**
- Con poche abitudini compaiono delle idee da aggiungere (Leggere 20 minuti, Palestra 3 volte a settimana, Niente telefono a letto) che aprono il dialogo già compilato: la pagina non resta mezza vuota

**Media**
- YouTube › Cronologia: "Svuota" chiede conferma, si può annullare e toglie anche i video da Riprendi in Media
- YouTube: cambiando video mentre suona, quello di prima resta in Riprendi al punto in cui eri; il punto si salva anche ogni 30 secondi e quando chiudi il player
- Video › Sul telefono: dopo un rifiuto definitivo del permesso la card dice "Permesso negato" e apre le impostazioni di Canvas, invece di non fare nulla; i video appena registrati compaiono tornando nella pagina

**Meteo**
- L'ultimo dettaglio da solo (Alba · Tramonto) prende tutta la riga, senza il buco accanto

**Sotto il cofano**
- Test nuovi: YtHistoryTest, HabitIdeasTest, più casi in BriefingTest (azioni rapide, sera con la card Domani, testo delle abitudini), TomorrowTest, TodayTasksTest, PhoneVideosTest e YtPlayerStateTest

## 1.97.0
- Spotify: aprendo una playlist creata da Spotify (o da altri) compariva "Spotify non permette questa richiesta". Da febbraio 2026 Spotify non mostra alle app personali i brani delle playlist che non sono tue: ora resta il titolo con "Riproduci" (suona normalmente) e un avviso chiaro al posto dell'errore
- Spotify: le tue playlist usano il nuovo indirizzo dell'API di Spotify; la pagina di un artista mostra album e singoli (i "brani più ascoltati" non sono più disponibili per le app personali)

## 1.96.0
**Media › Video**
- La pagina Video non è più mezza vuota: sotto i Recenti c'è "Sul telefono" con gli ultimi video registrati o scaricati (miniatura, durata, giorno). Il permesso si chiede solo quando tocchi "Mostra i video del telefono", e un tocco apre il video nel player
- Player YouTube: finché il video si carica si vede la sua durata (non più "0:00 / 0:00")

**Note**
- Date: con poche voci compaiono delle idee da aggiungere (compleanno, anniversario, scadenza passaporto, revisione auto, vacanze) che aprono il dialogo già compilato
- Editor: la prima riga della nota, il titolo, è più grande e in grassetto come nella lista
- La fila di schede non lascia più un pezzo di pillola tagliato sul bordo: scorre solo se la scheda aperta non si vede tutta
- Spese fisse: l'affitto del 5 compare il 5 anche se l'app era rimasta aperta o in sottofondo, e dopo un ripristino da backup
- Se il file delle Note non si legge, Canvas ne salva una copia e lo dice, invece di ripartire vuoto e cancellare tutto alla prima modifica
- Promemoria: "Rimanda › Stamattina 9:00" fa davvero stamattina anche se lo tocchi dopo le 5

**Impostazioni › Dati**
- Esporta backup funziona anche nelle cartelle di Google Drive
- Dopo "Ripristina da backup" la notifica del mattino e il widget si aggiornano subito

**Sotto il cofano**
- Test nuovi: NotesSafeLoadTest, YtTimeLabelTest, PhoneVideosTest, NotesUiLogicTest, DateIdeasTest, più casi in SnoozeTest

## 1.95.0
**Spotify**
- Accesso sistemato davvero: la pagina di Spotify dentro l'app restava nera (si vedeva solo lo sfondo). Ora il modulo per entrare compare, verificato su Android 14 e 16

**YouTube**
- Qualità "Automatica" di default: parte dalla più alta e scende solo se la rete non regge, senza fermarsi a caricare; si può sempre scegliere una qualità fissa
- Audio sempre nella lingua originale del video (prima a volte partiva un doppiaggio, per esempio in tedesco)
- Sottotitoli: scegli la lingua dal tasto sotto il video, restano accesi anche per i video dopo
- Commenti con icone Mi piace e Non mi piace, commento fissato e cuore dell'autore, risposte da aprire con un tocco e "Altri commenti"
- Non mi piace del video (stima pubblica di Return YouTube Dislike) accanto ai Mi piace
- Cronologia: nuova scheda con i video guardati qui e la cronologia del tuo account YouTube
- Riprende da dove eri rimasto e sulle anteprime c'è la barra rossa di quanto hai visto
- Sotto il video i correlati come nell'app di YouTube, più Ripeti e Condividi

## 1.94.0
- Vetro della barra e del +: sfocatura più morbida e velo un filo più presente, così sotto si vedono colori e forme ma non più il testo sbavato intorno alle icone

## 1.93.0
**Oggi**
- Nuova card "Domani" dalle 18: meteo di domani (minime, massime e "pioggia dalle 10"), i primi impegni, i promemoria con scadenza domani, compleanni e addebiti fissi. In cima la frase che conta, tipo "Domani 8:30 Dentista, pioggia dalle 10"; ogni riga apre la sua pagina
- Adesso: le cose urgenti sono di un arancio fisso, più scuro e leggibile col tema chiaro, e restano arancio anche con Material You
- Adesso ricorda il video lasciato a metà nelle ultime 24 ore, con un tocco riparte da lì

**Meteo**
- Avviso pioggia: in Impostazioni › Meteo "Avvisami se sta per piovere". Arriva una notifica circa mezz'ora prima ("Pioggia tra 20 minuti a Roma, debole per circa 45 minuti"), solo dalle 7 alle 23, al massimo una ogni 3 ore e mai se piove già

**Media**
- "Riprendi" in cima a Media quando nulla suona: l'ultimo video, YouTube o radio con il punto in cui eri, e sotto le fonti i Recenti
- YouTube parte dal secondo scritto nel link (t=90, t=1m30s)
- Player YouTube: se i commenti non arrivano si vede l'errore con Riprova (non più "nessun commento"), e un video messo in pausa resta in pausa quando ci torni
- Spotify riparte da solo se il riproduttore perde il collegamento, e gli errori sono spiegati in italiano

**News**
- Sopra notizie fresche si legge "Aggiornate X min fa" invece di "Niente di nuovo"; spariscono le voci dei feed che non sono articoli

**Note**
- Da fare: niente più X su ogni riga, si scorre verso sinistra per eliminare (con Annulla); eliminare resta anche nella modifica

**Aggiornamenti**
- L'interruttore "Avviso nuove versioni" sta solo in Impostazioni, non più ripetuto nella pagina Aggiornamenti

**Sotto il cofano**
- Test nuovi: avviso pioggia, card Domani, Riprendi, link di YouTube e Spotify, colore urgente; il controllo del player YouTube gira anche ogni giorno e su main

## 1.92.0
- Vetro liquido rifatto: velo molto più leggero, così la pagina sotto si vede davvero; la lente sul bordo ingrandisce quello che c'è sotto invece di riflettere cosa sta fuori (niente più bordo fantasma in alto) e sono spariti gli aloni azzurrini intorno alla barra
- Un filo di luce sul bordo e una lucentezza morbida in alto, come una goccia di vetro
- Il + accanto alla barra è anche lui di vetro, colorato col tema, senza l'ombra

## 1.91.0
- Nuova icona: una pennellata e una goccia di colore, piena e senza bordino. Con le icone a tema di Android 13+ prende i colori del tuo sfondo
- Vetro liquido (da Android 13): la barra in basso e il mini player piegano la pagina vicino ai bordi come una lente, con un filo di luce sul bordo. Sotto Android 13 resta il vetro sfocato
- Le pagine interne (Meteo, Il punto del giorno, Personalizza, Aggiornamenti, Calendario, Compila da solo, Località) hanno il titolo grande con la freccia, come Impostazioni
- Note: le sei schede diventano pillole come i filtri delle News, e la riga scorre da sola fino a quella aperta
- Spese: le spese di ogni giorno stanno in una card, come le altre liste
- Personalizza Oggi: ogni card ha la sua icona, l'interruttore è a destra e le frecce per l'ordine sono più leggibili

## 1.90.0
- Accesso a Spotify sistemato: la pagina restava nera, ora si entra con l'accesso ufficiale di Spotify (lo stesso che serve per la libreria e per ascoltare qui)
- YouTube: schede e filtri (Shorts, dirette) in un'unica fila, non più due barre che scorrono
- YouTube rifatto: anteprime grandi con foto del canale e durata, Short in una fila orizzontale, "Stai guardando" in cima per tornare al video
- Player di YouTube con comandi propri: tocca per mostrarli, doppio tocco a sinistra/destra per -10/+10 secondi, trascina giù per rimpicciolirlo nella bolla, schermo intero
- Sotto il video: canale con iscritti e Mi piace, qualità, velocità (0,5×–2×), descrizione, capitoli e commenti

## 1.88.0
**Note**
- Liste di spunta: le righe "- latte" o "[ ] pane" diventano caselle da spuntare con un tocco; il pulsante Lista trasforma le righe di una nota, "Aggiungi voce" in fondo, e nella lista delle note si legge "3 di 7 fatti"
- Cerca in tutte le Note: la lente in alto cerca in promemoria (anche completati), note, spese (anche per importo: "12,5" trova 12,50 €), pacchi (anche per codice), date e abitudini, senza badare ad accenti e maiuscole
- Spese fisse e abbonamenti: "ogni mese il 5 affitto 650" o "netflix 12,99 il 25 di ogni mese" si aggiungono da sole il giorno giusto; in cima al mese "Fisse · variabili", il budget al giorno tiene da parte le fisse ancora in arrivo
- Abitudini con obiettivo settimanale ("3 volte a settimana"): "2 su 3 questa settimana", serie in settimane, e niente rimproveri finché si è in tempo
- Abitudini: si vede a colpo d'occhio cosa manca oggi (cerchio vuoto da fare, pieno da fatta, "Manca oggi" in arancio dopo le 17)

**Oggi**
- Gli inviti a impostare (agenda, Compila da solo) non stanno più in cima: una riga in fondo "2 cose da attivare" apre i pulsanti, così sotto Adesso viene subito Da fare
- Adesso avvisa il giorno prima di un addebito fisso: "Domani addebito: Netflix 12,99 €"

**Cattura al volo e Calendario**
- Il "+" crea anche eventi: con "Evento", "venerdì alle 20 cena da Luca", "domani dalle 9 alle 11 corso", "per 2 ore" o "tutto il giorno" finiscono nel calendario del telefono, con l'anteprima prima di salvare
- Scheda evento: se eliminare non riesce o manca il permesso di scrittura ora lo dice, e dopo un rifiuto definitivo il pulsante porta alle impostazioni dell'app

**Impostazioni**
- Nuovo gruppo Dati: "Esporta backup" salva promemoria, abitudini, spese, note, pacchi, date e impostazioni in un file; "Ripristina da backup" mostra cosa contiene prima di sostituire. I dati entrano anche nel backup automatico di Android

**Promemoria e pagine web**
- "Rimanda" dopo mezzanotte diventa "Stamattina 9:00" invece di saltare un giorno
- Nella pagina web interna i link a YouTube e Spotify aprono i player dell'app invece del loro sito

**Test**
- Nuovi test automatici: ChecklistTest, NotesSearchTest, InvitesTest, QuickEventTest, HabitWeeklyTest, FixedExpenseTest, NotesStoreTest (anche un file di una versione vecchia), BackupTest, HabitTest, CalendarPermissionTest

## 1.86.0
- "Adesso" in Oggi, il meteo di adesso e il brano in riproduzione in Media hanno il colore del tema invece del nero: con Material You prendono il colore dello sfondo del telefono, con Canvas un azzurro chiaro
- In "Adesso" le cose urgenti restano in arancio, più carico col tema chiaro così si leggono sul fondo colorato

## 1.85.0
- Vetro della barra in basso e del mini player davvero trasparente: velo più leggero e sfocatura meno forte, così la pagina sotto si vede scorrere
- La sfocatura parte già da Android 12 (prima solo da Android 13); sui telefoni più vecchi resta un velo semitrasparente
- Tolta l'ombra sotto barra e mini player, che dietro il vetro lo faceva sembrare grigio e pieno

## 1.84.0
- Player di YouTube: barra con il minutaggio (dove sei e quanto dura), scelta della qualità video (ricordata), capitoli toccabili e commenti
- YouTube: nuova scheda Home con i video consigliati, filtro "Solo dirette" accanto a "Nascondi Shorts", durata e bollino IN DIRETTA sulle anteprime
- Spotify suona dentro l'app con il riproduttore ufficiale (serve Premium): tocchi un brano, un album o una playlist e parte qui, con notifica e bolla. Mai il sito
- Se avevi già collegato Spotify con la chiave, ricollegalo una volta per dare il permesso di ascolto
- I link di Spotify e YouTube (anche dalle notizie) si aprono nei player dell'app
- Ricerca di YouTube nativa: risultati senza consumare la quota dell'API di Google
- Tolti i pulsanti che aprivano il sito di YouTube: ora è tutto nell'app

## 1.83.0
- YouTube con il player nativo di Android: il video arriva direttamente nell'app, senza pagina web né pubblicità
- Ascolto in sottofondo con controlli nella notifica e nella schermata di blocco; la bolla mostra il video
- A fine video parte il successivo dai canali che segui

## 1.82.0
- YouTube con il player dell'app: toccando un video si apre una schermata dell'app con il video in alto e, sotto, titolo, canale, barra di avanzamento, ±10 secondi, schermo intero e i prossimi video dai canali che segui. Niente più pagina del sito
- Anche i link di YouTube condivisi, la notifica e la bolla aprono il player dell'app
- Accesso a Spotify: la pagina di login non resta più nera (si apre la versione per telefono del sito)
- Feed di YouTube senza API ufficiale: niente più caricamento infinito, dopo un minuto compare un messaggio chiaro con cosa fare
- YouTube e Spotify: le schede scorrono e i nomi non vengono più tagliati

## 1.81.0
- Tutto dentro l'app: nessun link apre più il browser o un'altra app. Le pagine dei giornali, il tracciamento dei pacchi e il codice sorgente si aprono nella pagina web interna (con il blocco pubblicità); i link dei video negli articoli vanno nel player di YouTube dell'app
- Le pagine che provano ad aprire l'app del sito (intent://) restano nella pagina interna usando il loro indirizzo web; mail e numeri di telefono continuano ad aprire il compositore e il tastierino
- Nuovo calendario dentro Canvas: da Oggi, "Calendario" mostra gli impegni dei prossimi 60 giorni giorno per giorno, con "Vai a un giorno"
- Toccando un evento si apre la sua scheda (orario, luogo, note, calendario); da lì si modifica o si elimina, e il + crea un nuovo evento con titolo, orari, tutto il giorno, luogo, note e calendario. Tutto si salva nel calendario del telefono (serve il permesso di scrittura, chiesto la prima volta)
- Per gli eventi che si ripetono si cambiano titolo, luogo e note di tutta la serie; i calendari di sola lettura si possono solo guardare

## 1.80.0
- Impostazioni riorganizzate: Aspetto, Oggi e notifiche, Dati automatici, Meteo, Media, Informazioni; ogni riga ha l'icona colorata e mostra a destra lo stato attuale (orario, località, acceso/spento)
- Il blocco pubblicità si regola ora in Impostazioni › Media (interruttore, richieste bloccate e "Aggiorna" le liste); in Media resta solo una riga che dice se è attivo
- In Impostazioni anche l'avviso delle nuove versioni (prima solo dentro Aggiornamenti) e la località del meteo, con "Automatica" o la città scelta
- Nuova voce Licenze (caratteri Inter, liste del blocco pubblicità, Open-Meteo)
- Vetro sfocato sugli elementi che galleggiano: la barra in basso e il mini player lasciano intravedere la pagina sotto (sfocatura vera da Android 12)

## 1.74.0
- Promemoria: nella notifica, oltre a "Fatto", ci sono "Tra 1 ora" e "Domani 9:00". Per quelli che si ripetono si sposta solo questa volta, la volta dopo resta quella di sempre
- Oggi: la card "Da fare" mostra quello che conta adesso (scaduti, di oggi e dalle 18 anche di domani, più al massimo due senza scadenza) e sotto "+ altri N in Note"
- Il punto del giorno: ogni tema ha per titolo il suo articolo più rappresentativo, come in un giornale, al posto delle tre parole chiave
- Agenda: accanto a "Consenti" c'è "Non ora", che nasconde la card (si riaccende da Personalizza); dopo due rifiuti si nasconde da sola
- Oggi e Meteo: il cerchio di aggiornamento compare solo quando trascini in basso, non più sopra le card durante il caricamento automatico

## 1.73.0
- YouTube e Spotify: nuovo pulsante con la chiave in alto per usare il collegamento ufficiale (API di Google o Client ID di Spotify) anche se hai già fatto l'accesso nell'app
- Spotify: il Client ID della tua app è già inserito, basta toccare "Collega con il Client ID"
- Tutto dentro l'app: il collegamento a Spotify si fa in una finestra interna (niente browser) e brani, album e playlist partono nel player di Spotify dell'app invece che nell'app Spotify
- Un solo player alla volta: se parte YouTube, Spotify o la radio, gli altri vanno in pausa; la bolla e la notifica mostrano sempre quello che suona

## 1.72.0
- Nuovo: Compila da solo (Impostazioni > Dati automatici). Canvas legge le notifiche che arrivano sul telefono e ci trova da solo:
  - pacchi in arrivo con codice e data prevista (Amazon, Poste, BRT, GLS, DHL, mail e SMS dei corrieri); quando arriva "consegnato" il pacco si segna da solo
  - spese con importo, esercente e categoria (Revolut, banche, Satispay, PayPal, Google Pay, ricevute via mail); niente entrate, rimborsi, codici o pagamenti rifiutati
  - scadenze e appuntamenti (bollette "scade il…", prenotazioni, visite, biglietti, anche da Gmail) come promemoria con avviso
- Le cose trovate compaiono in Oggi sotto Adesso ("Trovati per te") e in una notifica silenziosa, con Aggiungi o Ignora; oppure con "Salva senza chiedere" si salvano subito e si possono annullare
- "Cerca negli SMS": rilegge gli SMS degli ultimi 30 giorni (pagamenti con carta, corrieri, appuntamenti)
- Le chat (WhatsApp, Telegram…) non vengono lette; niente lascia il telefono

## 1.71.0
- Da fare: i tempi si possono scrivere anche a lettere ("tra due ore stendere", "fra dieci minuti tè") e con la mezz'ora ("tra un'ora e mezza uscire")

## 1.70.0
- Nuovo design di Canvas: sfondo color carta, card bianche con un bordo sottile al posto delle ombre, un blu deciso come accento e l'arancio per ciò che è urgente; caratteri Inter inclusi nell'app
- Nuovo tema "Canvas" (chiaro o scuro come il telefono), ora predefinito; Material You e AMOLED restano nelle Impostazioni
- Barra in basso flottante: il nome compare solo sulla sezione aperta, e accanto c'è un + sempre a portata per aggiungere al volo promemoria, note, spese e pacchi (in Note aggiunge alla scheda aperta)
- Intestazioni con il titolo grande e sopra la riga che conta: la data in Oggi, le nuove dall'ultima visita in News, le scadenze in Note
- Oggi: "Adesso" diventa il blocco scuro in cima con la temperatura grande; Agenda, Da fare, In arrivo e Il punto hanno l'etichetta fuori e le righe separate da linee sottili
- Note: schede sottolineate al posto dei bottoni, e i promemoria raggruppati in card per Scaduti, Oggi, Prossimi giorni
- Media: Video, YouTube, Spotify e Radio in una griglia 2×2 con un colore ciascuno; il brano in corso nel blocco scuro in alto
- News: la notizia principale con il titolo grande, filtri delle fonti più puliti

## 1.63.0
- Oggi: nella card Adesso (e nel widget) il giorno di un compleanno o anniversario dice anche quanti anni compie ("Oggi: Compleanno di Marta (30 anni)")

## 1.61.0
- Date: per compleanni e anniversari che si ripetono ogni anno, se hai scritto l'anno vero compare quanti anni compie ("3 dicembre · compie 30 anni"), anche nella card In arrivo di Oggi

## 1.56.0
- Note: quando le note sono tante, in cima alla lista c'è "Cerca nelle note" per trovare subito quella che ti serve (con la X per cancellare)

## 1.55.0
- Tempi più leggibili in tutta l'app (note, notizie, video, meteo): oltre il giorno si legge "ieri 14:30", "lun 9:15" o "12 ott" invece di "21/10 14:30"

## 1.54.0
- Spese: correggendo una spesa sopra i mille euro ("1.234,50") le modifiche venivano perse senza avviso; ora l'importo è riconosciuto
- Spese: se l'importo non è valido il dialog lo dice ("Scrivi un importo, es. 12,50") e Salva resta spento invece di chiudere senza salvare; Invio sulla descrizione salva

## 1.53.0
- Note: aprendo una nota, sotto il titolo compare quando l'hai modificata l'ultima volta ("Modificata 2 h fa")

## 1.52.0
- Note: nell'editor di una nota c'è il pulsante con la puntina per fissarla in alto (o toglierla), senza dover sapere della pressione lunga nella lista

## 1.51.0
- Località del meteo: una X nel campo di ricerca per cancellare quello che hai scritto, e il tasto Cerca della tastiera sceglie subito la prima città trovata

## 1.50.0
- Note: nel dialog di una nuova abitudine e del budget mensile basta premere Invio sulla tastiera per salvare; nel nuovo pacco Invio passa al codice di tracciamento
- Budget mensile: la prima volta la tastiera numerica si apre da sola
- Date: toccando la riga "Ogni anno" si sente la stessa vibrazione dell'interruttore

## 1.49.0
- Note: quando crei una nota, un'abitudine, una data o un pacco la tastiera si apre da sola sul campo da scrivere, senza un tocco in più (in modifica resta chiusa)

## 1.48.0
- Meteo: si aggiorna anche trascinando la pagina verso il basso, con il piccolo scatto alla soglia come in Oggi e News

## 1.47.0
- Note e Media: toccando di nuovo la scheda già aperta nella barra in basso la lista torna in cima, come già in Oggi e News

## 1.46.0
- Lettore articoli: se un articolo non si scarica il messaggio è in italiano chiaro ("Il sito non lascia leggere l'articolo nell'app: aprilo nell'originale", "Nessuna connessione a Internet") invece di codici come "HTTP 403"

## 1.45.0
- Personalizza Oggi: per mostrare o nascondere una card basta toccare tutta la riga (non solo l'interruttore), con la stessa vibrazione e pressione elastica del resto dell'app

## 1.44.0
- Meteo: se la previsione non si carica (niente posizione o niente rete) c'è anche "Scegli località" accanto a "Riprova", con un'icona invece del testo spoglio
- Impostazioni: le righe si "schiacciano" leggermente al tocco come il resto dell'app

## 1.43.0
- Note: i promemoria di Da fare, le righe delle Spese, il budget e i giorni delle Abitudini si "schiacciano" leggermente al tocco come il resto dell'app
- Scelta della località del meteo: stesso effetto sulle città

## 1.42.0
- Oggi: anche le righe dentro le card (Adesso, agenda, promemoria, In arrivo, notizie del giorno) si "schiacciano" leggermente al tocco come il resto dell'app
- Il punto del giorno: stesso effetto sugli articoli di ogni tema

## 1.41.0
- Note: "Annulla" per qualche secondo anche quando elimini un'abitudine (con tutta la sua serie), una spesa, un pacco o una data

## 1.40.0
- Note: quando elimini una nota dal suo editor, tornando alla lista compare "Annulla" per qualche secondo per recuperarla

## 1.39.0
- Note > Da fare: anche quando elimini un promemoria dalla sua finestra di modifica (o ne svuoti il testo) compare "Annulla" per qualche secondo

## 1.38.0
- Vibrazione leggera quando aggiungi un promemoria (da Oggi o da Note) o una spesa: senti che è salvato senza guardare

## 1.37.0
- Note > Da fare: quando elimini un promemoria con la X, o tutti i completati con il cestino in alto, compare "Annulla" per qualche secondo

## 1.36.0
- Il punto del giorno: gli articoli già aperti sono attenuati come nella sezione News, così vedi subito cosa ti manca
- Gli articoli aperti da Oggi o dal Punto del giorno contano come letti anche in News

## 1.35.0
- Media: le righe di radio, playlist di YouTube, Spotify e la card di cosa sta suonando si "schiacciano" al tocco come il resto dell'app
- Radio: vibrazione leggera quando aggiungi o togli una stazione dalle preferite

## 1.34.0
- Oggi: dopo aver spuntato un promemoria compare "Annulla" per qualche secondo, così una spunta data per sbaglio si rimette a posto con un tocco (anche per i promemoria ricorrenti)

## 1.33.0
- YouTube e Spotify: se il feed non si carica il messaggio è in italiano chiaro ("Nessuna connessione a Internet", "YouTube non risponde, riprova più tardi") invece dei testi tecnici in inglese
- Accesso a YouTube e collegamento a Spotify: errori comprensibili, senza codici di Google
- Oggi: anche il riassunto delle notizie spiega in italiano perché non si è caricato

## 1.32.0
- Aggiornamenti: le card hanno lo stesso stile del resto dell'app (angoli arrotondati e ombra leggera)
- Aggiornamenti: l'interruttore "Avvisami delle nuove versioni" vibra come gli altri e si attiva toccando tutta la card
- Aggiornamenti: se l'installazione non parte il messaggio è in italiano chiaro, senza codici tecnici

## 1.31.0
- Blocco pubblicità rifatto come uBlock Origin: usa le stesse liste di filtri (EasyList, EasyPrivacy e le liste di uBlock Origin), aggiornate da sole ogni 4 giorni o con "Aggiorna" in Media
- Funziona in tutte le pagine web dell'app, non solo nel player: articoli delle news, lettore, video negli articoli e il browser invisibile che scarica feed e pagine
- Oltre a bloccare le richieste nasconde i riquadri pubblicitari vuoti e ferma gli script che li rimettono; su YouTube toglie gli annunci dal video e salta quelli che passano
- Agenda: non mostra più i calendari nascosti e gli inviti che hai rifiutato
- Meteo: "oggi" è giusto anche quando la previsione salvata è di ieri
- Notifica del mattino: "Invia una prova" parte subito dopo aver concesso il permesso

## 1.30.0
- Giro di design su tutta l'app: le sei schede delle Note diventano chip come i filtri delle News, sempre visibili e scorrono da sole fino a quella scelta
- Abitudini: pressione elastica sulle card, vibrazione leggera alla spunta e giorni passati più facili da toccare (tutta la colonna, non solo il pallino)
- Pacchi e Spese: card e righe coerenti con il resto, vibrazione alla consegna
- Media: il blocco pubblicità ha lo stesso fondo delle altre card, con l'icona colorata quando è attivo
- News: le fonti che non rispondono sono indicate per nome, senza codici d'errore

## 1.29.0
- Notifica del mattino: i promemoria mostrano l'orario se scadono oggi, e quelli già scaduti sono segnalati
- Notifica del mattino: quando gli impegni o le cose da fare sono più di tre compare "…e altri N"

## 1.28.0
- Feedback aptico leggero dove aiuta: spunta di un promemoria, bolla del player presa e agganciata al bordo, soglia del "trascina per aggiornare", interruttori, ordine delle card, scelta del tema e pressione lunga su una nota per fissarla
- Usa le vibrazioni di sistema: se nel telefono sono spente, l'app non vibra

## 1.27.0
- Promemoria scritti come parli: "domani alle 9 dentista", "venerdì 18:30 palestra", "tra 20 minuti forno", "il 15 novembre bollo auto". Giorno, ora e avviso si impostano da soli, e mentre scrivi vedi quando suonerà
- Promemoria ricorrenti: "ogni mese il 5 affitto", "ogni lunedì spazzatura". Quando lo spunti passa alla volta successiva; la ripetizione si cambia anche dal promemoria
- Note > Abitudini: le cose da fare ogni giorno, con la spunta di oggi, i giorni di fila e l'ultima settimana. Dalle 17 Oggi ricorda quelle che mancano
- Note > Spese: si scrivono come un messaggio ("12,50 pizza", "benzina 40€"), la categoria si sceglie da sola. Totale del mese, confronto col mese prima, ripartizione per categoria e budget mensile con quanto resta al giorno; Oggi avvisa quando ti avvicini al limite
- Note > Pacchi: cosa aspetti, quando arriva e il tracciamento del corriere a un tocco (Amazon, Poste, UPS, DHL, GLS, BRT). Il giorno della consegna compare in Oggi
- Cattura al volo da ovunque: riquadro "Promemoria al volo" nelle impostazioni rapide, scorciatoie tenendo premuta l'icona (promemoria, nota, spesa) e "Salva in Canvas" nel menu Condividi. Una mail di spedizione condivisa diventa un pacco

## 1.26.0
- YouTube e Spotify senza registrazioni: il feed nell'app usa l'accesso fatto in Impostazioni, niente Google Cloud né app per sviluppatori
- YouTube: nuova scheda "Più tardi" con i video di Guarda più tardi
- Impostazioni: un solo accesso per servizio, che vale per player e feed; il blocco pubblicità si regola in Media
- Accesso a Spotify: suggerimento per entrare con email e codice se l'accesso con Google non va

## 1.25.0
- L'app ora si chiama Canvas: nome sotto l'icona, widget, voce "Guarda in Canvas" nel menu Condividi e titolo delle release
- Icona nuova: i tre riquadri appoggiati su una tela con una cornice sottile
- Animazioni elastiche: la bolla del player si lancia e rimbalza sul bordo, le schermate e la barra in basso entrano con una molla
- Card, scorciatoie, notizie e pulsanti si "schiacciano" leggermente al tocco e tornano su con un piccolo rimbalzo

## 1.24.0
- Errori di rete più chiari in meteo, news e aggiornamenti: "Nessuna connessione a Internet", "connessione troppo lenta" o "il servizio non risponde" al posto dei messaggi tecnici in inglese
- Aggiornamenti: se il download dell'APK si interrompe a metà ora lo dice, invece di provare a installare un file incompleto

## 1.23.0
- News: tolto Il Post, che continuava a bloccare l'app. Al suo posto AGI (agenzia di stampa, articoli gratuiti) e Internazionale

## 1.21.0
- Oggi risponde a "cosa mi serve sapere adesso?": sotto il meteo la sezione Adesso con poche righe che cambiano con l'ora (prossimo impegno, pioggia in arrivo, promemoria scaduti o in scadenza, date di oggi; la sera il meteo e il primo impegno di domani). Tocca una riga per aprire il dettaglio
- Le card vuote spariscono: niente più "Nessun impegno oggi"; "In arrivo" compare solo con date entro una settimana
- La sera, finiti gli impegni di oggi, l'agenda mostra quelli di domani
- Le scorciatoie sono nascoste di default (ripetevano la barra in basso): si riaccendono da Personalizza
- Widget: le righe di Adesso sotto il meteo; la notifica del mattino mostra in chiaro la riga più utile
- News: in cima "N nuove dall'ultima visita" e un separatore "Già viste"; gli articoli già aperti sono attenuati
- Note: i promemoria sono raggruppati in Scaduti, Oggi, Prossimi giorni e Senza scadenza
- Oggi: tolto il pulsante "Personalizza questa schermata" in fondo, che ripeteva l'icona in alto

## 1.20.0
- News: Il Post di nuovo raggiungibile. Il sito bloccava le richieste dell'app come se fosse un bot (HTTP 403): ora, quando succede, feed e articoli vengono scaricati con il motore del browser

## 1.19.0
- Widget: una riga con i promemoria scaduti o in scadenza oggi e il prossimo da fare, con l'orario; toccandola si apre la sezione Note
- Il widget si aggiorna subito quando aggiungi, completi o sposti un promemoria

## 1.18.0
- Note: nuova scheda "Date" con tutti i conti alla rovescia (compleanni, viaggi, scadenze), con il pulsante + per aggiungerne
- Animazioni tra le schermate: le pagine di dettaglio entrano da destra e tornano indietro scorrendo, tra le schede una dissolvenza
- Toccare la scheda già aperta (Oggi o News) riporta la lista in cima
- Oggi: senza il permesso del calendario la card dell'agenda occupa una sola riga
- Meteo: il titolo del dettaglio è "Meteo", la città resta nella card

## 1.17.0
- Mini player a bolla dentro l'app: il video di YouTube, Spotify o la radio restano in un riquadro che si trascina dove vuoi e ti segue in tutte le sezioni; un tocco riapre il player
- Radio: le stazioni più ascoltate in Italia, ricerca in tutto il mondo, preferite e ascoltate di recente, ascolto in sottofondo con notifica e comandi in cuffia
- Media: schede riordinate, il blocco pubblicità diventa una riga compatta in fondo

## 1.16.0
- Picture-in-picture: uscendo dall'app durante un video (player o YouTube) il video continua in un riquadro sopra le altre app
- "Guarda in Dashboard" nel menu Condividi: i link di YouTube si aprono nel player senza pubblicità, video e audio nel player video
- L'app compare anche tra quelle per aprire video e musica dal file manager o dalla galleria

## 1.15.0
- YouTube nativo: nuovi video dei canali che segui, le tue playlist, i video con il Mi piace e la ricerca, in schede dell'app (si collega con l'account Google, solo lettura)
- Spotify nativo: ascoltati di recente, i tuoi brani e artisti preferiti, playlist, brani piaciuti, album, podcast e ricerca; con Premium si comanda la riproduzione sui tuoi dispositivi, altrimenti si apre l'app Spotify
- Il login di Spotify ora si fa nel browser, sul sito di Spotify
- Player video: velocità di riproduzione e sottotitoli esterni (.srt, .vtt)
- Timer di spegnimento per la musica nella sezione Media
- Prima di collegare gli account va registrata l'app su Google Cloud e su Spotify for Developers (guida nella schermata di collegamento)

## 1.14.0
- Nuova icona minimal a tre riquadri, con la versione a tema per le icone Material You e una nuova icona per le notifiche
- Barra in basso più ordinata: Oggi, News, Note e Media. Le Impostazioni si aprono dall'ingranaggio in alto in ogni sezione
- Schermata Oggi: il saluto e la data fanno da titolo (di notte "Buonanotte"), al posto della grande card col sole
- Dimensioni più compatte in tutta l'app: titoli in alto bassi e uguali in ogni sezione, caratteri e card un filo più piccoli
- Meteo nella schermata Oggi più leggibile: massima e minima sotto la temperatura, niente più testo che va a capo
- Nuove scorciatoie in alto: meteo, punto del giorno, video, YouTube e Spotify a un tocco
- "Il punto del giorno" più snello, con titoli su due righe
- News: la prima notizia in grande, le altre come righe compatte con la miniatura
- Impostazioni riordinate: tema con tre pulsanti in una riga, notifica del mattino insieme alle card, account e blocco pubblicità insieme, aggiornamenti con la versione installata
- Pallino sulla scheda Note quando ci sono promemoria scaduti o in scadenza oggi

## 1.13.0
- Notifica quando esce una nuova versione dell'app: il controllo avviene da solo ogni qualche ora, e toccandola si apre la schermata Aggiornamenti
- Un solo avviso per versione, e la notifica sparisce quando apri Aggiornamenti
- Si spegne dall'interruttore "Avvisami delle nuove versioni" in Impostazioni > Aggiornamenti

## 1.12.0
- YouTube e Spotify suonano anche a schermo spento e con l'app in sottofondo
- Notifica con copertina, titolo e comandi (indietro, play/pausa, avanti, chiudi), anche sulla schermata di blocco e con i tasti delle cuffie
- Mini player nella sezione Media: vedi cosa sta suonando e lo comandi senza aprire la pagina
- Toccando la notifica si torna direttamente al player

## 1.11.0
- Nuova sezione Media nella barra in basso, con YouTube, Spotify e un player video
- YouTube e Spotify dentro l'app con il tuo account: accedi o esci da Impostazioni > Account
- Blocco pubblicità integrato: niente banner né server pubblicitari, e su YouTube gli spot nei video vengono saltati da soli (si spegne da Impostazioni o dalla sezione Media)
- La musica di Spotify e YouTube continua mentre usi il resto dell'app; "Chiudi player" la ferma
- Player video per i file del telefono e i link (mp4, webm, streaming m3u8), con schermo intero, recenti con anteprima e ripresa dal punto in cui eri rimasto
- I link di YouTube incollati nel player si aprono direttamente su YouTube

## 1.10.0
- Nuova card "Conti alla rovescia": quanti giorni mancano a compleanni, viaggi e scadenze, anche ripetuti ogni anno
- Promemoria con scadenza: tocca una cosa da fare per impostare giorno e ora, e ricevi un avviso con il tasto "Fatto"
- I promemoria con scadenza vengono mostrati per primi, in rosso se scaduti
- La notifica del mattino ricorda anche le ricorrenze di oggi e domani

## 1.9.0
- Meteo: avviso di pioggia in arrivo (o quando smette) grazie alle previsioni ogni 15 minuti, nella Dashboard, nel dettaglio e nella notifica del mattino
- Meteo: qualità dell'aria (indice europeo) nel dettaglio
- Meteo: puoi scegliere una città a mano dall'icona in alto nel dettaglio, oppure tornare alla tua posizione
- Dashboard: trascina in basso per aggiornare meteo, notizie e agenda

## 1.8.0
- Nuovo widget per la schermata Home del telefono: meteo del posto e i primi tre temi del giorno, con i colori del tuo sfondo; tocca un titolo per leggerlo
- Il widget si aggiorna da solo ogni ora
- Notifica del mattino (Impostazioni > Notifica del mattino): meteo, impegni di oggi, cose da fare e temi del giorno all'orario che scegli
- Tasto "Invia una prova" per vedere subito com'è la notifica

## 1.7.0
- Agenda di oggi nella Dashboard: gli impegni dei calendari del telefono, con orario e luogo; tocca un evento per aprirlo o + per crearne uno
- Nuova sezione Note nella barra in basso: promemoria da spuntare e note libere che si salvano da sole
- Card "Da fare" nella Dashboard per spuntare o aggiungere promemoria al volo
- Dashboard personalizzabile: scegli quali card mostrare e in che ordine (icona in alto o Impostazioni > Dashboard)
- Note fissabili in alto tenendole premute, e condivisibili

## 1.6.0
- News solo da fonti gratuite: tolte la Repubblica e Il Sole 24 Ore, aggiunte Open e RaiNews
- In lista compaiono solo gli articoli che il lettore riesce a pulire del tutto: quelli a pagamento o illeggibili vengono nascosti
- Gli articoli vengono preparati in anticipo e si aprono subito nel lettore

## 1.5.0
- Meteo nella Dashboard: temperatura e condizioni del posto in cui sei, con massima, minima e probabilità di pioggia
- Toccando il meteo si apre il dettaglio: prossime 24 ore, 7 giorni, umidità, vento, pressione, indice UV, alba e tramonto
- Nuovo "Il punto del giorno": le notizie simili delle varie testate vengono raggruppate in automatico nei temi principali delle ultime 24 ore, senza AI e gratis
- Il recap si apre per intero con tutti gli articoli di ogni tema, che si leggono nel lettore interno
- Le notizie vengono scaricate una volta sola e condivise tra Dashboard e News

## 1.4.0
- Lettore più affidabile: se il testo non si trova al primo colpo prova i dati strutturati della pagina, la versione AMP e infine carica la pagina come un browser (per i siti fatti in JavaScript)
- Download degli articoli con intestazioni da browser, codifica corretta e secondo tentativo in caso di errore
- I cookie accettati nella pagina originale valgono anche per il lettore
- Durante il caricamento il lettore dice cosa sta facendo

## 1.3.0
- Nuovo lettore: gli articoli si aprono in modalità lettura, solo titolo, testo, immagini e video, senza menu, pubblicità o banner del giornale
- Testo con caratteri da lettura, dimensione regolabile con il tasto "Aa", link agli altri articoli aperti sempre nel lettore
- Se il testo non è disponibile (per esempio articoli per abbonati) si può aprire la pagina originale dentro l'app

## 1.2.0
- Lettore interno: gli articoli si aprono dentro l'app, senza browser esterno
- Nel lettore: condividi, apri nel browser, tasto indietro tra le pagine e tema scuro anche sulle pagine
- News: corretto Il Post che risultava non raggiungibile
- News: le fonti non raggiungibili mostrano anche il motivo (es. HTTP 403, timeout)

## 1.1.0
- Scelta del tema nelle impostazioni: Material You, Sistema e AMOLED (nero puro)
- Nuova icona a lavagnetta, anche in versione a tema
- Nuovo modulo News: notizie da ANSA, Il Post, la Repubblica, Il Sole 24 Ore e Fanpage
- Filtro per fonte, trascina in basso per aggiornare, articoli aperti nel browser integrato
- La Dashboard mostra l'elenco dei moduli

## 1.0.0
- Prima versione di General Dashboard
- Design Material You con colori dinamici presi dallo sfondo
- Impostazioni con sottomenu Aggiornamenti
- Changelog di tutte le versioni
- Aggiornamento automatico da GitHub con un tasto

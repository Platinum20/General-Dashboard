# Changelog

## 1.84.0
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

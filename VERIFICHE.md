# Verifiche di Canvas

Tenuto dal "verificatore": una riga per ogni versione controllata dopo la release.
I problemi trovati sono in BACKLOG.md, sezione "Problemi trovati dal verificatore".

| Versione | Data | Giro fotografico | Esito |
|---|---|---|---|
| 1.91.0 | 2026-10-22 | `main/46` | Funziona: titoli grandi con freccia nelle pagine interne (02, 03, 04, 41, 44), schede delle Note a pillole che scorrono a quella aperta, spese in una card, Personalizza con icone e interruttore a destra, anche al buio. Vetro liquido e icona nuova non fotografabili (V7). Problemi: V16 V19 bassa; `43_compila_da_solo` saltata (V1) |
| 1.90.0 | 2026-10-22 | `main/46` | Funziona: feed YouTube rifatto (schede in una fila, anteprime grandi, IN DIRETTA, "In pausa" in cima) e player con comandi propri, capitoli, qualità e velocità (foto `youtube_*`). Accesso Spotify con OAuth ufficiale: solo dal codice (redirect gestito in `LoginScreen`). Problemi: V18 bassa |
| 1.88.0 | 2026-10-22 | `main/46` | Funziona: riga "2 cose da attivare" in fondo a Oggi, abitudini con cerchio vuoto e "Manca oggi" in arancio, gruppo Dati con backup; liste, ricerca, spese fisse, eventi dal +, Rimanda di notte solo dal codice e dai test (nuovi test verdi). Problemi: V13 V14 media, V15 V17 bassa |
| 1.86.0 | 2026-10-22 | `main/41` | Funziona: "Adesso" e meteo col colore del tema (azzurro con Canvas chiaro, blu al buio), leggibili. Problemi: V11 bassa; brano in corso e riga urgente non fotografati (V8) |
| 1.85.0 | 2026-10-22 | `main/41` | Funziona: niente ombra sotto barra e "+", velo più leggero. Il testo sotto la barra ora si legge di più senza sfocatura (V7 bassa, da guardare sul telefono) |
| 1.84.0 | 2026-10-22 | `main/41` | Solo dal codice: minutaggio, qualità, capitoli, commenti, Home, Solo dirette, Spotify nel riproduttore nascosto, link nei player. Spariti i pulsanti per il sito (foto 31, 32). Problemi: V8 V9 V10 media, V12 bassa |
| 1.83.0 | 2026-10-22 | `main/41` | Solo dal codice: player nativo ExoPlayer + NewPipe, servizio in sottofondo, video successivo. Problemi: V9 media, V12 bassa |
| 1.82.0 | 2026-10-22 | `main/41` | Solo dal codice: player dell'app per i link YouTube, notifica e bolla. Nessuna foto (V8) |
| 1.81.0 | 2026-10-22 | `main/29` | Funziona: link nella pagina interna, calendario nativo (solo dal codice e dai test, non fotografato). Problemi: V1 media, V3 V4 bassa |
| 1.80.0 | 2026-10-22 | `main/29` | Funziona: Impostazioni riorganizzate (foto 40 e 42), blocco pubblicità in Impostazioni › Media, Licenze. Problemi: V7 bassa |
| 1.74.0 | 2026-10-22 | `main/29` | Funziona: Da fare mirato con "+ altri N in Note", titoli dei temi del punto del giorno, "Non ora" in Agenda. Problemi: V5 bassa |
| 1.73.0 | 2026-10-22 | `main/29` | Funziona: pulsante con la chiave in YouTube e Spotify, Client ID già inserito. Nessun problema |
| 1.72.0 | 2026-10-22 | `main/29` | Funziona: card "Compila da solo" in Oggi e riga in Impostazioni; logica coperta da AutoFillExtractorTest/AutoFillRulesTest. Problemi: V1 media (proposte non fotografate) |

Base della prima verifica: 1.71.0 (giro `main/26`). Test del giro `main/29`: 138, 0 falliti, 1 ignorato.
Test del giro `main/41` (main 1.86.0): 139, 0 falliti, 2 ignorati (`YouTubeNativeCheck` gira solo con la rete in `ytcheck.yml`).
Test del giro `main/46` (main 1.91.0): 225, 0 falliti, 2 ignorati (`YouTubeNativeCheck`, `LiveChecks`).

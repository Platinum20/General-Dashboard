# Revisione UX (routine ogni 4 ore)

Ultimo commit di main revisionato: 3e396cd30842853569dffff5c74c2a81c32b8318 (1.284.0, 2026-10-28)

## Giro fotografico
ScreenshotTour copre ora anche le pagine profonde, aperte per rotta con `tourNavigate` (MainActivity):
60-64 dettagli (promemoria, data, pacco, spesa, abitudine), 65 nuovo evento, 66 editor nota, 67 Spese,
68 Recap, 69 player, 70 coda, 71 video YouTube, 72 fumetto delle azioni. Già c'erano: Meteo, Punto,
Cerca ovunque, composer "+", Note, Media, tutte le sottopagine di Impostazioni.
Mancano ancora: dettaglio evento (Robolectric non ha il calendario), layout video vero (serve un video
che suona: oggi 69/71 mostrano la radio), un CanvasDialog aperto.

## 2026-10-28 (1.284.0)
Corretto:
- Lettore articoli in serif (FontFamily.Serif) -> Inter.
- Player audio: tasto Coda nell'header -> sotto i comandi + trascina in su apre la coda.
- Player radio: "In diretta" due volte (sottotitolo + riga del tempo) -> una.
- Editor nota lunga: barra in fondo coperta dal mini player -> sale sopra (MiniDock.bottomRoom).
- Tolti Snackbar mai usato in DetailPage e import FAB in NotesScreen.

Controllati e lasciati apposta: DropdownMenu in Pickers (dentro un dialogo) e nel menu impostazioni
video YouTube; Toast in QuickCaptureActivity e CaptureFeedback fuori dalla schermata principale.

Lasciati ad Antonio / rimandati:
- DetailPage ha Modifica/Elimina in alto a destra (header): è il modello di tutte le pagine dettaglio,
  spostarli è una scelta sua.
- Player: la fonte compare due volte (sottotitolo "RADIO" sopra il titolo + pastiglia "Radio").
- Fumetto delle azioni sopra il mini player: in Robolectric il mini player traspare sotto l'ultima riga;
  da verificare sul telefono.
- Dettaglio spesa senza riga di azioni (gli altri dettagli ne hanno una): forse giusto così.

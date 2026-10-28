# Beta test di Canvas

Stato del beta tester automatico ("Canvas: beta tester", ogni 4 ore). La prima riga dice l'ultima
versione già provata: se l'ultima release GitHub è questa, il giro si ferma subito.

**Ultima versione testata: 1.285.0**

## 2026-10-28 · da 1.274.0 a 1.284.0

- Giro screenshots.yml su main (run 104, su 1.284.0): 625 test, 0 rossi, 2 ignorati. Tutti i giri fotografici verdi.
- Foto controllate: player radio (coda sotto i comandi, "In diretta" una volta), editor nota (tasti sopra il
  mini player), Impostazioni › Media, Aggiornamenti, Spese. Nessun crash, nessuna schermata vuota.
- Codice letto senza foto: aggiornamenti automatici (UpdateCheckWorker/UpdateInstallReceiver), fumetto novità
  (WhatsNew): gestiscono rete assente, conferma di Android e prima installazione. Già coperti da test.
- Aggiunto BetaNoteSpeseLiveTest (1.279 Spese da Note accende Note, 1.284 "In diretta" una volta), PR #230.

### Corretto

- **Impostazioni › Media (1.283)**: "Escludi video per membri" e "Riconosci canzoni" avevano il sottotitolo coi
  puntini. Testi più corti + SettingsLinesTest. Rilasciato in 1.285.0 (#231).

### Problemi aperti

- Nessuno. Da notare: con "Scarica solo col Wi-Fi" acceso e sempre su rete mobile, l'aggiornamento automatico
  aspetta il Wi-Fi senza notifica (scelta voluta, da rivedere solo se Antonio lo segnala).

## 2026-10-27 · da 1.261.0 a 1.273.0

- Giro screenshots.yml su main (run 101): 610 test, 3 rossi, tutti nei giri fotografici.
  - ScreenshotTour (chiaro e scuro): "21_note_con_scadenza" mancante. Il titolo "Prossimi giorni"
    sta fuori dalla card cliccabile; il giro ora apre la pagina toccando una riga. Non era un bug dell'app.
  - YouTubeTour player: dalla 1.268 il video è una TextureView che Robolectric non sa disegnare;
    il giro la nasconde solo per la foto.
- Aggiunto SpotifyAliasTest (1.262: stato dal server con l'uri alias del brano), PR #220.
- Foto controllate: Note, Il punto del giorno ("Ascolta" in cima), Meteo, Impostazioni e sottopagine
  (Media, Notifiche, Account), Aggiornamenti. Testi su una riga, nessuna schermata vuota.

### Corretto

- **Aggiornamenti (1.271)**: il mini player in basso copriva l'unico tasto "Controlla e aggiorna".
  Ora la barra lascia lo spazio del mini player (MiniDock.bottomReserveDp). Rilasciato in 1.280.0 (#226).
- Giri fotografici di nuovo verdi (#223).

### Problemi aperti

- Nessuno. Le versioni 1.274-1.279 uscite durante questo giro restano da provare al prossimo.

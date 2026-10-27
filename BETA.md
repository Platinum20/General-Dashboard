# Beta test di Canvas

Stato del beta tester automatico ("Canvas: beta tester", ogni 4 ore). La prima riga dice l'ultima
versione già provata: se l'ultima release GitHub è questa, il giro si ferma subito.

**Ultima versione testata: 1.273.0** (più la correzione 1.280.0; 1.274-1.279 da provare)

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

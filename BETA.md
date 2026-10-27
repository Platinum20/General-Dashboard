# Beta test di Canvas

Stato del beta tester automatico ("Canvas: beta tester", ogni 4 ore). La prima riga dice l'ultima
versione già provata: se l'ultima release GitHub è questa, il giro si ferma subito.

**Ultima versione testata: 1.273.0**

## 2026-10-27 · da 1.261.0 a 1.273.0

- Giro screenshots.yml su main (run 101): 610 test, 3 rossi, tutti nei giri fotografici.
  - ScreenshotTour (chiaro e scuro): "21_note_con_scadenza" mancante. Il titolo "Prossimi giorni"
    sta fuori dalla card cliccabile; il giro ora apre la pagina toccando una riga. Non era un bug dell'app.
  - YouTubeTour player: dalla 1.268 il video è una TextureView che Robolectric non sa disegnare;
    il giro la nasconde solo per la foto.
- Aggiunto SpotifyAliasTest (1.262: stato dal server con l'uri alias del brano), PR #220.
- Foto controllate: Note, Il punto del giorno ("Ascolta" in cima), Meteo, Impostazioni e sottopagine
  (Media, Notifiche, Account), Aggiornamenti. Testi su una riga, nessuna schermata vuota.

### Problemi aperti

- **Aggiornamenti (1.271)**: tolta l'icona in alto, l'unico tasto "Controlla e aggiorna" sta in fondo
  ed è coperto dal mini player quando qualcosa suona (foto 41_aggiornamenti). Correzione pronta
  (spazio del mini player sotto la barra, ~76 dp se il mini player è in basso) ma non applicata:
  UpdatesScreen.kt era in mano al branch claude/project-thread-3kv4wu (aggiornamento automatico).
  Da fare al prossimo giro se quel branch è chiuso.

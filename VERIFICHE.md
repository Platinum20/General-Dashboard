# Verifiche di Canvas

Tenuto dal "verificatore": una riga per ogni versione controllata dopo la release.
I problemi trovati sono in BACKLOG.md, sezione "Problemi trovati dal verificatore".

| Versione | Data | Giro fotografico | Esito |
|---|---|---|---|
| 1.81.0 | 2026-10-22 | `main/29` | Funziona: link nella pagina interna, calendario nativo (solo dal codice e dai test, non fotografato). Problemi: V1 media, V3 V4 bassa |
| 1.80.0 | 2026-10-22 | `main/29` | Funziona: Impostazioni riorganizzate (foto 40 e 42), blocco pubblicità in Impostazioni › Media, Licenze. Problemi: V7 bassa |
| 1.74.0 | 2026-10-22 | `main/29` | Funziona: Da fare mirato con "+ altri N in Note", titoli dei temi del punto del giorno, "Non ora" in Agenda. Problemi: V5 bassa |
| 1.73.0 | 2026-10-22 | `main/29` | Funziona: pulsante con la chiave in YouTube e Spotify, Client ID già inserito. Nessun problema |
| 1.72.0 | 2026-10-22 | `main/29` | Funziona: card "Compila da solo" in Oggi e riga in Impostazioni; logica coperta da AutoFillExtractorTest/AutoFillRulesTest. Problemi: V1 media (proposte non fotografate) |

Base della prima verifica: 1.71.0 (giro `main/26`). Test del giro `main/29`: 138, 0 falliti, 1 ignorato.

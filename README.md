# Canvas

Canvas è la tua dashboard personale per Android, in Kotlin con Jetpack Compose e design Material You.

## Come si pubblica una nuova versione

1. Aumenta `VERSION_NAME` e `VERSION_CODE` in `version.properties`.
2. Aggiungi in cima a `CHANGELOG.md` una sezione `## <nuova versione>` con le novità.
3. Fai push su `main`.

GitHub Actions compila l'APK firmato e crea la release `v<versione>` con le note del changelog.
Dall'app: **Impostazioni → Aggiornamenti → Aggiorna**.

## Firma

La release viene firmata con una chiave fissa salvata nei secret della repository
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
La chiave deve restare sempre la stessa, altrimenti Android rifiuta gli aggiornamenti.

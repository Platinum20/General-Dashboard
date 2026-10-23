package com.platinum20.dashboard.net

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ultimi dati scaricati (notizie, feed di YouTube e Spotify) salvati su file:
 * all'avvio l'app li mostra subito e intanto scarica quelli nuovi.
 * Se non è stato inizializzato (o il file è rovinato) si comporta come una cache vuota.
 */
object DiskCache {
    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.cacheDir, "dati")
    }

    /** Solo per i test: usa una cartella qualsiasi. */
    internal fun initAt(folder: File?) {
        dir = folder
    }

    suspend fun read(name: String): String? = withContext(Dispatchers.IO) {
        val file = dir?.resolve(name)?.takeIf { it.exists() } ?: return@withContext null
        runCatching { AtomicFile(file).readFully().decodeToString() }.getOrNull()
    }

    suspend fun write(name: String, text: String) = withContext(Dispatchers.IO) {
        val folder = dir ?: return@withContext
        runCatching {
            folder.mkdirs()
            val file = AtomicFile(File(folder, name))
            val out = file.startWrite()
            try {
                out.write(text.toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    /**
     * Cancella i file che iniziano con [prefix] (es. quando si scollega un account).
     * Subito, non in sottofondo: un caricamento che parte subito dopo non deve ritrovare i dati vecchi.
     */
    fun delete(prefix: String) {
        dir?.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { runCatching { it.delete() } }
    }
}

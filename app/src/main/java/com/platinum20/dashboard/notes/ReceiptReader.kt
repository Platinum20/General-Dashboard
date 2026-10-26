package com.platinum20.dashboard.notes

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Dove la fotocamera della graffetta salva la foto da leggere (la lettura è in capture/PhotoAttach). */
object ReceiptReader {

    /** Un solo file in cache, sovrascritto ogni volta e cancellato dopo la lettura. */
    fun photoUri(context: Context): Uri {
        val file = File(context.cacheDir, "receipts/scontrino.jpg").apply { parentFile?.mkdirs() }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

package com.platinum20.dashboard.media

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest

/**
 * Riceve video, audio e link condivisi da altre app (o aperti con "Apri con")
 * e li gira alla sezione Media: i link YouTube al player YouTube, i file al player video.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> sharedStream() ?: sharedLink()
            else -> null
        }
        if (target != null) {
            val forward = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            OpenRequest(OpenRequest.PLAY, url = target.toString()).writeTo(forward)
            // Il permesso di leggere il file passa all'app insieme all'indirizzo.
            if (target.scheme == "content") {
                forward.setDataAndType(target, intent.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(forward)
        }
        finish()
    }

    private fun sharedStream(): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    /** Il primo indirizzo web nel testo condiviso (YouTube condivide "titolo + link"). */
    private fun sharedLink(): Uri? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("""https?://\S+""").find(text)?.value?.let(Uri::parse)
    }
}

package com.platinum20.dashboard.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class GmailPasswordException : IOException("Gmail non accetta più la password per app")

/**
 * Gmail con la password per app di Google, via IMAP in sola lettura (EXAMINE: niente viene segnato come letto).
 * A differenza dell'accesso Google con l'app "in prova", non scade ogni 7 giorni.
 * La password resta solo sul telefono, cifrata con una chiave del Keystore di Android.
 */
object GmailImap {
    private const val HOST = "imap.gmail.com"
    private const val PREFS = "gmail_imap"
    private const val KEY_ADDRESS = "address"
    private const val KEY_SECRET = "secret"
    private const val KEY_REJECTED = "rejected"
    private const val ALIAS = "canvas_gmail_imap"

    private val _linked = MutableStateFlow(false)
    /** C'è una password per app salvata. */
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    private val _rejected = MutableStateFlow(false)
    /** Gmail ha rifiutato la password salvata (revocata o cambiata): va inserita di nuovo. */
    val rejected: StateFlow<Boolean> = _rejected.asStateFlow()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val p = prefs(context)
        _address.value = p.getString(KEY_ADDRESS, "").orEmpty()
        _linked.value = p.contains(KEY_SECRET)
        _rejected.value = p.getBoolean(KEY_REJECTED, false)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Prova l'accesso e, se va, salva indirizzo e password. Lancia l'errore da mostrare. */
    suspend fun link(context: Context, address: String, password: String) {
        init(context)
        val addr = address.trim()
        // Google la mostra a gruppi di quattro con gli spazi: gli spazi non fanno parte della password.
        val pass = password.replace(" ", "").trim()
        withContext(Dispatchers.IO) { open(addr, pass).use { } }
        prefs(context).edit()
            .putString(KEY_ADDRESS, addr)
            .putString(KEY_SECRET, encrypt(pass))
            .putBoolean(KEY_REJECTED, false)
            .apply()
        _address.value = addr
        _rejected.value = false
        _linked.value = true
    }

    fun unlink(context: Context) {
        prefs(context).edit().clear().apply()
        _linked.value = false
        _rejected.value = false
        _address.value = ""
    }

    /** Sessione con le credenziali salvate, già sulla cartella con tutte le mail. Null se non collegato. */
    suspend fun session(context: Context): Session? {
        init(context)
        val p = prefs(context)
        val addr = p.getString(KEY_ADDRESS, null) ?: return null
        val pass = p.getString(KEY_SECRET, null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: run {
            markRejected(context)
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                open(addr, pass).also { it.selectAllMail() }
            } catch (e: GmailPasswordException) {
                markRejected(context)
                throw e
            }
        }
    }

    private fun markRejected(context: Context) {
        _rejected.value = true
        prefs(context).edit().putBoolean(KEY_REJECTED, true).apply()
    }

    private fun open(address: String, password: String): Session {
        val socket = SSLSocketFactory.getDefault().createSocket(HOST, 993) as SSLSocket
        socket.soTimeout = 30_000
        val session = Session(socket)
        try {
            session.greeting()
            session.command("LOGIN ${ImapParse.quote(address)} ${ImapParse.quote(password)}")
        } catch (e: Exception) {
            session.close()
            throw e
        }
        return session
    }

    /** Una connessione IMAP aperta: comandi uno alla volta, risposte con i letterali già letti. */
    class Session(private val socket: SSLSocket) : Closeable {
        private val input: InputStream = BufferedInputStream(socket.inputStream)
        private val output: OutputStream = socket.outputStream
        private var tag = 0

        internal fun greeting() {
            val line = readLine()
            if (!line.startsWith("* OK")) throw IOException("Gmail non risponde, riprova più tardi")
        }

        internal fun selectAllMail() {
            val box = ImapParse.allMailbox(command("LIST \"\" \"*\""))
            command("EXAMINE $box")
        }

        /** UID delle mail che corrispondono a una ricerca Gmail (stessa sintassi della casella). */
        fun search(query: String): List<Long> = ImapParse.search(command("UID SEARCH X-GM-RAW ${ImapParse.quote(query)}"))

        /** Id Gmail (esadecimale, come l'API) degli UID dati. */
        fun ids(uids: List<Long>): Map<Long, String> {
            if (uids.isEmpty()) return emptyMap()
            return ImapParse.fetched(command("UID FETCH ${uids.joinToString(",")} (UID X-GM-MSGID)"))
                .associate { it.uid to it.gmailId }
        }

        /** La mail intera (solo l'inizio: gli allegati grossi non servono), senza segnarla come letta. */
        fun message(uid: Long): MailMessage? =
            ImapParse.fetched(command("UID FETCH $uid (UID X-GM-MSGID X-GM-LABELS FLAGS INTERNALDATE BODY.PEEK[]<0.$MAX_BYTES>)"))
                .firstOrNull { it.uid == uid }?.let { ImapParse.message(it) }

        /** La mail intera con gli allegati (il PDF del biglietto), fino a [max] byte. */
        fun raw(uid: Long, max: Int = 4_000_000): String? =
            ImapParse.fetched(command("UID FETCH $uid (UID BODY.PEEK[]<0.$max>)"))
                .firstOrNull { it.uid == uid }?.raw

        @Synchronized
        fun command(cmd: String): List<ImapLine> {
            val t = "C" + (++tag)
            output.write("$t $cmd\r\n".toByteArray(Charsets.UTF_8))
            output.flush()
            val lines = mutableListOf<ImapLine>()
            while (true) {
                val first = readLine()
                if (first.startsWith("$t ")) {
                    val status = first.substring(t.length + 1)
                    if (status.startsWith("OK", true)) return lines
                    if (ImapParse.authFailed(status)) throw GmailPasswordException()
                    throw IOException("Gmail ha rifiutato la richiesta: " + status.substringAfter(' ').take(80))
                }
                lines += readResponse(first)
            }
        }

        /** Una risposta può continuare dopo ogni letterale {n}: si legge tutto insieme. */
        private fun readResponse(first: String): ImapLine {
            val text = StringBuilder()
            val literals = mutableListOf<String>()
            var line = first
            while (true) {
                val m = literal.find(line)
                if (m == null) {
                    text.append(line)
                    return ImapLine(text.toString(), literals)
                }
                text.append(line, 0, m.range.first).append(ImapLine.mark(literals.size))
                literals += readBytes(m.groupValues[1].toInt())
                line = readLine()
            }
        }

        private fun readLine(): String {
            val out = ByteArrayOutputStream(128)
            while (true) {
                val b = input.read()
                if (b < 0) throw IOException("Gmail ha chiuso la connessione")
                if (b == '\n'.code) break
                out.write(b)
            }
            return out.toString("ISO-8859-1").removeSuffix("\r")
        }

        private fun readBytes(n: Int): String {
            val buf = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = input.read(buf, read, n - read)
                if (r < 0) throw IOException("Gmail ha chiuso la connessione")
                read += r
            }
            return String(buf, Charsets.ISO_8859_1)
        }

        override fun close() {
            runCatching {
                output.write("Z LOGOUT\r\n".toByteArray())
                output.flush()
            }
            runCatching { socket.close() }
        }

        private companion object {
            val literal = Regex("\\{(\\d+)\\}$")
            const val MAX_BYTES = 200_000
        }
    }

    // --- Password cifrata col Keystore ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }
}

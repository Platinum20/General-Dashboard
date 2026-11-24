package com.platinum20.dashboard.net

import android.content.Context
import com.platinum20.dashboard.security.Vault
import org.json.JSONObject

/**
 * L'accesso che si rifà da solo (Antonio, 2026-11-24: "mantenerlo sempre, sempre"). I siti (WindTre,
 * Creators Pro) chiudono la sessione dopo un po': prima toccava rientrare a mano. Ora, nella schermata
 * d'accesso visibile, Canvas tiene da parte utente e password mentre li scrivi e li salva cifrati col
 * Keystore ([Vault]) solo dopo una lettura riuscita (così una password sbagliata non resta). Nella
 * rilettura nascosta, se il sito mostra il modulo d'accesso, lo compila e lo invia da solo.
 * L'accesso con Google non si può compilare: lì resta "Rientra".
 */
open class AutoLogin(private val prefsName: String) {
    @Volatile private var pendingUser: String? = null
    @Volatile private var pendingPass: String? = null
    /** Quante volte la rilettura in corso ha già inviato il modulo: oltre [MAX_TRIES] si smette (password cambiata). */
    @Volatile private var tries = 0
    /** L'ultima volta che una pagina ha mostrato il modulo d'accesso. */
    @Volatile var loginSeenAt = 0L
        private set

    /** Dal modulo visibile, mentre scrivi (qualsiasi thread). Vuoto = quel campo non c'era in questa pagina. */
    fun typed(user: String, pass: String) {
        if (user.isNotBlank()) pendingUser = user.trim()
        if (pass.isNotEmpty()) pendingPass = pass
    }

    /** Lettura riuscita: quello scritto era giusto, si salva. */
    fun confirm(context: Context) {
        val user = pendingUser
        val pass = pendingPass ?: return
        val old = saved(context)
        val keepUser = user ?: old?.first ?: return
        if (old == keepUser to pass) return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        Vault.put(prefs.edit(), KEY, JSONObject().put("u", keepUser).put("p", pass).toString()).apply()
        pendingPass = null
    }

    fun saved(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val json = runCatching { JSONObject(Vault.read(prefs, KEY) ?: return null) }.getOrNull() ?: return null
        val u = json.optString("u"); val p = json.optString("p")
        return if (usable(u, p)) u to p else null
    }

    fun hasSaved(context: Context) = saved(context) != null

    fun clear(context: Context) {
        pendingUser = null; pendingPass = null
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** All'inizio di ogni rilettura nascosta. */
    fun begin() { tries = 0; code = null; codeAskedAt = 0L }

    /** Il modulo d'accesso è sulla pagina. */
    fun seen() { loginSeenAt = System.currentTimeMillis() }

    /** Il codice di conferma trovato nella posta, per la pagina che lo chiede ([codeScript]). */
    @Volatile var code: String? = null
    /** Da quando la pagina chiede il codice (0 = non lo chiede): le mail più vecchie non valgono. */
    @Volatile var codeAskedAt = 0L
        private set

    /** La pagina chiede il codice: true solo la prima volta, per far partire una sola ricerca nella posta. */
    fun codeSeen(): Boolean {
        if (codeAskedAt != 0L) return false
        codeAskedAt = System.currentTimeMillis()
        return true
    }

    /** La pagina chiede se può inviare il modulo ancora una volta. */
    fun mayFill(): Boolean = tryAgain(tries).also { if (it) tries++ }

    companion object {
        private const val KEY = "login"
        const val MAX_TRIES = 3

        internal fun usable(user: String?, pass: String?) = !user.isNullOrBlank() && !pass.isNullOrEmpty()
        internal fun tryAgain(done: Int) = done < MAX_TRIES

        /** Pezzi JS comuni: campo visibile, campo utente (quello prima della password). */
        private const val FIELDS = """
            function vis(e) { return !!e && !e.disabled && !e.readOnly && (e.offsetWidth > 0 || e.offsetHeight > 0); }
            function all(q) { return [].slice.call(document.querySelectorAll(q)).filter(vis); }
            function hinted(e) { return /user|mail|login|phone|tel|cell|numero|msisdn|account|utente/i.test([e.name, e.id, e.autocomplete, e.type, e.placeholder].join(' ')); }
            function pass() { return all('input[type=password]')[0] || null; }
            function user(p) {
              var ins = all('input').filter(function (e) { return /^(text|email|tel|)$/i.test(e.type || ''); });
              if (p) {
                var before = ins.filter(function (e) { return e.compareDocumentPosition(p) & 4; });
                return before[before.length - 1] || null;
              }
              return ins.filter(hinted)[0] || null;
            }
        """

        /** Scrivere in un campo come se lo facessi tu (anche per React/Angular) e premere il tasto giusto. */
        private const val PUT = """
              function put(e, v) {
                try {
                  e.focus();
                  var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                  d.set.call(e, v);
                  e.dispatchEvent(new Event('input', { bubbles: true }));
                  e.dispatchEvent(new Event('change', { bubbles: true }));
                  e.dispatchEvent(new Event('blur', { bubbles: true }));
                } catch (x) { e.value = v; }
              }
              function send(e) {
                var scope = e.form || document;
                var bs = [].slice.call(scope.querySelectorAll('button, input[type=submit], [role=button]')).filter(vis);
                var b = bs.filter(function (b) { return /accedi|entra|login|log in|continua|avanti|prosegui|sign in|next|conferma/i.test(b.innerText || b.value || ''); })[0]
                  || bs.filter(function (b) { return b.type === 'submit' && (b.innerText || b.value || '').trim(); })[0];
                if (b) b.click();
                else if (e.form && e.form.requestSubmit) e.form.requestSubmit();
                else e.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
              }
        """

        /** Il campo (o le caselline, una per cifra) del codice di conferma; mai su una pagina con la password. */
        private const val CODE_FIELDS = """
            function codeFields() {
              if (pass()) return [];
              var ins = all('input').filter(function (e) { return !/^(password|hidden|checkbox|radio|submit|button)$/i.test(e.type || ''); });
              var one = ins.filter(function (e) { return e.maxLength === 1; });
              if (one.length >= 4) return one;
              var c = ins.filter(function (e) {
                return e.autocomplete === 'one-time-code' || /otp|codice|code|verific|pin/i.test([e.name, e.id, e.placeholder, e.getAttribute('aria-label')].join(' '));
              });
              return c.length ? [c[0]] : [];
            }
        """

        /**
         * Il codice di conferma mandato per mail: quando la pagina lo chiede, Canvas lo cerca nella posta
         * ([bridge].codeSeen) e appena c'è ([bridge].code) lo scrive e conferma. Solo cifre, quindi niente da quotare.
         */
        fun codeScript(bridge: String): String = """
            (function () {
              if (window.__cvCode) return; window.__cvCode = true;
              $FIELDS
              $CODE_FIELDS
              $PUT
              setInterval(function () {
                try {
                  var f = codeFields();
                  if (!f.length || f.some(function (e) { return e.value; })) return;
                  $bridge.codeSeen();
                  var c = String($bridge.code() || '');
                  if (!/^\d{4,8}$/.test(c)) return;
                  if (f.length > 1) f.forEach(function (e, i) { put(e, c.charAt(i)); }); else put(f[0], c);
                  setTimeout(function () { send(f[f.length - 1]); }, 400);
                } catch (e) {}
              }, 1500);
            })();
        """.trimIndent()

        /** Nella schermata d'accesso visibile: passa a Canvas quello che scrivi (solo lì, mai altrove). */
        fun captureScript(bridge: String): String = """
            (function () {
              if (window.__cvCred) return; window.__cvCred = true;
              $FIELDS
              function grab() {
                try {
                  var p = pass(), u = user(p);
                  var uv = u && (p || hinted(u)) ? u.value : '', pv = p ? p.value : '';
                  if (uv || pv) $bridge.typed(uv, pv);
                } catch (e) {}
              }
              ['input', 'change', 'submit', 'click'].forEach(function (t) { document.addEventListener(t, grab, true); });
              document.addEventListener('keydown', function (e) { if (e.key === 'Enter') grab(); }, true);
            })();
        """.trimIndent()

        /**
         * Nella rilettura nascosta: se compare il modulo lo compila (anche in due passi, prima utente poi
         * password) e lo invia. Senza dati salvati (vuoti) dice solo che il modulo c'è. I valori entrano come stringhe JS quotate ([JSONObject.quote]), mai come codice.
         */
        fun fillScript(bridge: String, user: String, pass: String): String = """
            (function (U, P) {
              if (window.__cvFill) return; window.__cvFill = true;
              $FIELDS
              $PUT
              function step() {
                try {
                  var p = pass(), u = user(p);
                  if (!p && !(u && hinted(u))) return;
                  $bridge.loginSeen();
                  if (!U || !P) return;
                  if (p && p.value) return;
                  if (!p && u.value) return;
                  if (!$bridge.mayFill()) return;
                  if (u && !u.value) put(u, U);
                  if (p) put(p, P);
                  setTimeout(function () { send(p || u); }, 400);
                } catch (e) {}
              }
              setInterval(step, 1500);
            })(${JSONObject.quote(user)}, ${JSONObject.quote(pass)});
        """.trimIndent()
    }
}

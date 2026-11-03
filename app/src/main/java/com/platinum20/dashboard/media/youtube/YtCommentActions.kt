package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.media.youtube.player.YtComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Mi piace e risposte ai commenti col tuo account YouTube. L'API ufficiale non permette il Mi piace
 * ai commenti: si usa l'accesso fatto nell'app (come [YtSync]) e i comandi che YouTube stesso mette
 * accanto a ogni commento, letti di nuovo dalla pagina dei commenti già vista.
 */
object YtCommentActions {
    private const val PREFS = "yt_comments"
    private const val KEY_LIKED = "liked"
    /** Tante bastano: servono solo a ricordare il pollice pieno sui commenti già visti. */
    private const val MAX_LIKED = 500

    private var prefs: SharedPreferences? = null
    private val _liked = MutableStateFlow<Set<String>>(emptySet())
    /** Commenti a cui hai messo Mi piace da Canvas. */
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    fun load(context: Context) {
        prefs(context)
    }

    private fun prefs(context: Context): SharedPreferences =
        prefs ?: context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            prefs = it
            _liked.value = it.getString(KEY_LIKED, "").orEmpty().split(',').filter { id -> id.isNotBlank() }.toSet()
        }

    private fun remember(context: Context, id: String, liked: Boolean) {
        val next = remembered(_liked.value, id, liked, MAX_LIKED)
        _liked.value = next
        prefs(context).edit().putString(KEY_LIKED, next.joinToString(",")).apply()
    }

    /** Mette o toglie il Mi piace; se YouTube rifiuta lo stato torna com'era e arriva l'errore. */
    suspend fun like(context: Context, videoId: String, comment: YtComment, like: Boolean) {
        prefs(context)
        val before = comment.id in _liked.value
        remember(context, comment.id, like)
        try {
            YouTubeWeb.withAccount(context) { page -> page.run(script(videoId, comment, if (like) "like" else "unlike", "")) }
        } catch (e: Exception) {
            remember(context, comment.id, before)
            throw friendly(e)
        }
    }

    /** Risponde a [comment] col testo dato. */
    suspend fun reply(context: Context, videoId: String, comment: YtComment, text: String) {
        try {
            YouTubeWeb.withAccount(context) { page -> page.run(script(videoId, comment, "reply", text.trim())) }
        } catch (e: Exception) {
            throw friendly(e)
        }
    }

    /** Un nuovo commento sotto al video. */
    suspend fun post(context: Context, videoId: String, text: String) {
        try {
            YouTubeWeb.withAccount(context) { page -> page.run(script(videoId, null, "post", text.trim())) }
        } catch (e: Exception) {
            throw friendly(e)
        }
    }

    private fun friendly(e: Exception): Exception = when (e) {
        is YouTubeWeb.NotLoggedIn -> IOException("Accedi a YouTube da Impostazioni › Account per commentare")
        else -> IOException(e.message?.removePrefix("YouTube: ")?.let { "YouTube: $it" } ?: "YouTube non risponde, riprova")
    }

    /** L'insieme dei Mi piace dopo un tocco, tenendo solo i più recenti. */
    internal fun remembered(current: Set<String>, id: String, liked: Boolean, max: Int): Set<String> {
        if (id.isBlank()) return current
        val list = current.filter { it != id }.toMutableList()
        if (liked) list += id
        return LinkedHashSet(list.takeLast(max))
    }

    /** Il numero da mostrare: quello letto più il tuo Mi piace se l'hai messo dopo averlo letto. */
    internal fun shownLikes(read: Int, likedNow: Boolean, likedWhenRead: Boolean): Int =
        (read + (if (likedNow) 1 else 0) - (if (likedWhenRead) 1 else 0)).coerceAtLeast(0)

    /** Il testo che parte rispondendo a una risposta: come su YouTube, si cita chi l'ha scritta. */
    internal fun replyPrefill(author: String, toReply: Boolean): String =
        if (toReply && author.isNotBlank()) "@" + author.removePrefix("@") + " " else ""

    /**
     * Lo script per la pagina dell'account: rilegge la pagina dei commenti da cui veniva [comment]
     * (o la prima), trova i comandi di quel commento e li esegue.
     */
    internal fun script(videoId: String, comment: YtComment?, action: String, text: String): String =
        "(" + ACTION_FN + ")(" +
            js(videoId) + "," +
            js(comment?.id.orEmpty()) + "," +
            (comment?.source?.let { js(it) } ?: "null") + "," +
            js(action) + "," +
            js(text) + ")"

    /** Una stringa JavaScript tra virgolette: testo libero (accenti, a capo, emoji) senza rompere lo script. */
    internal fun js(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c < ' ' || c == '\u2028' || c == '\u2029' || c == '<' -> append("\\u").append(String.format("%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    // Cerca il commento sia nel formato nuovo (commentViewModel + entità della pagina) sia nel vecchio
    // (commentRenderer): YouTube cambia spesso la struttura, i nomi dei comandi molto meno.
    private const val ACTION_FN = """
async function(videoId, commentId, from, action, text) {
  function walk(n, f, d) {
    if (!n || typeof n !== 'object' || d > 80) return false;
    if (f(n)) return true;
    for (var k in n) { if (walk(n[k], f, d + 1)) return true; }
    return false;
  }
  function pick(o, re) { var m = JSON.stringify(o || {}).match(re); return m ? m[1] : null; }
  async function first() {
    var watch = await __cvApi('next', {videoId: videoId});
    var tok = null;
    walk(watch, function(n) {
      var s = n.itemSectionRenderer;
      if (s && (s.sectionIdentifier === 'comment-item-section' || s.targetId === 'comments-section')) {
        tok = pick(s, /"token":"([^"]+)"/);
        return !!tok;
      }
      return false;
    }, 0);
    if (!tok) walk(watch, function(n) {
      var p = n.engagementPanelSectionListRenderer;
      if (p && /comment/.test(p.panelIdentifier || '')) { tok = pick(p, /"token":"([^"]+)"/); return !!tok; }
      return false;
    }, 0);
    if (!tok) throw new Error('commenti non trovati');
    return await __cvApi('next', {continuation: tok});
  }
  var page = from ? await __cvApi('next', {continuation: from}) : await first();
  if (action === 'post') {
    var params = pick(page, /"createCommentParams":"([^"]+)"/);
    if (!params) throw new Error('qui non si può commentare');
    var made = await __cvApi('comment/create_comment', {createCommentParams: params, commentText: text});
    if (/STATUS_FAILED/.test(JSON.stringify(made.actionResult || {}))) throw new Error('commento non pubblicato');
    return true;
  }
  var ents = {};
  var muts = (page.frameworkUpdates && page.frameworkUpdates.entityBatchUpdate && page.frameworkUpdates.entityBatchUpdate.mutations) || [];
  muts.forEach(function(m) {
    var p = m.payload || {};
    for (var k in p) { if (p[k] && typeof p[k] === 'object') { if (p[k].key) ents[p[k].key] = p[k]; if (m.entityKey) ents[m.entityKey] = p[k]; } }
  });
  var vm = null, old = null;
  walk(page, function(n) {
    if (n.commentId === commentId && n.toolbarSurfaceKey) { vm = n; return true; }
    if (n.commentRenderer && n.commentRenderer.commentId === commentId) { old = n.commentRenderer; return true; }
    return false;
  }, 0);
  var cmd;
  if (vm) {
    var surf = ents[vm.toolbarSurfaceKey] || {};
    var state = ents[vm.toolbarStateKey] || {};
    cmd = {like: surf.likeCommand, unlike: surf.unlikeCommand, reply: surf.replyCommand,
           liked: state.likeState === 'TOOLBAR_LIKE_STATE_LIKED'};
  } else if (old) {
    var b = (old.actionButtons && old.actionButtons.commentActionButtonsRenderer) || {};
    var t = (b.likeButton && b.likeButton.toggleButtonRenderer) || {};
    cmd = {like: t.defaultServiceEndpoint, unlike: t.toggledServiceEndpoint, reply: b.replyButton, liked: !!t.isToggled};
  } else {
    throw new Error('commento non trovato, riapri i commenti');
  }
  if (action === 'like' || action === 'unlike') {
    var want = action === 'like';
    if (want === cmd.liked) return want;
    var token = pick(want ? cmd.like : cmd.unlike, /"action":"([^"]+)"/);
    if (!token) throw new Error('Mi piace non disponibile');
    var res = await __cvApi('comment/perform_comment_action', {actions: [token]});
    if (/STATUS_FAILED/.test(JSON.stringify(res.actionResults || res.actionResult || {}))) throw new Error('Mi piace non salvato');
    return want;
  }
  var rp = pick(cmd.reply, /"createReplyParams":"([^"]+)"/);
  if (!rp) throw new Error('a questo commento non si può rispondere');
  var sent = await __cvApi('comment/create_comment_reply', {createReplyParams: rp, commentText: text});
  if (/STATUS_FAILED/.test(JSON.stringify(sent.actionResult || {}))) throw new Error('risposta non pubblicata');
  return true;
}
"""
}

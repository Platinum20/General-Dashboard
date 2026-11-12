package com.platinum20.dashboard.shifts

import com.platinum20.dashboard.net.WebSession

/**
 * L'accesso a Creators Pro che resta acceso: cookie di sessione e sessionStorage della pagina
 * salvati cifrati dopo ogni lettura riuscita e rimessi in ogni WebView nuova (vedi [WebSession]).
 * Senza, ogni riavvio dell'app o rilettura nascosta ripartiva senza sessione e chiedeva di rientrare.
 */
object CreatorsSession : WebSession(
    "shifts_session",
    listOf("https://app.creatorspro.com", "https://api.creatorspro.com", "https://creatorspro.com", "https://www.creatorspro.com"),
)

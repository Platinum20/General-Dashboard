package com.platinum20.dashboard.bank

import com.platinum20.dashboard.net.WebSession

/**
 * L'accesso a Revolut che resta acceso: cookie di sessione e sessionStorage dell'app web salvati
 * cifrati dopo ogni lettura riuscita e rimessi prima di aprire la pagina (vedi [WebSession]).
 * Se Revolut li ha scaduti davvero risponde 401 e serve il nuovo accesso.
 */
object RevolutSession : WebSession(
    "revolut",
    listOf("https://app.revolut.com", "https://sso.revolut.com", "https://www.revolut.com"),
)

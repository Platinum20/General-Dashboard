package com.platinum20.dashboard.sim

import com.platinum20.dashboard.net.WebSession

/**
 * L'accesso all'area clienti WindTre che resta acceso: come per Revolut, cookie e sessionStorage
 * salvati cifrati dopo ogni lettura riuscita e rimessi in ogni WebView nuova (vedi [WebSession]).
 */
object WindTreSession : WebSession(
    "sim",
    listOf("https://ac.windtre.it", "https://www.windtre.it", "https://apigw.windtre.it", "https://login.windtre.it"),
)

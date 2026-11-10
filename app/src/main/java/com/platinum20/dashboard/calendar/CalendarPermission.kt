package com.platinum20.dashboard.calendar

/** Com'è andata l'ultima richiesta di un permesso del calendario. */
enum class PermissionStatus { GRANTED, DENIED, BLOCKED }

/**
 * Le risposte ai permessi del calendario: Android, dopo due "No", non mostra più il dialog
 * e l'unica strada resta Info app. Funzioni pure, così si provano senza il telefono.
 */
object CalendarPermission {
    /**
     * Dopo la risposta al dialog: se è un no e Android non chiede più spiegazioni
     * ([showRationale] falso), il permesso è negato per sempre.
     */
    fun afterRequest(granted: Boolean, showRationale: Boolean): PermissionStatus = when {
        granted -> PermissionStatus.GRANTED
        showRationale -> PermissionStatus.DENIED
        else -> PermissionStatus.BLOCKED
    }

    /** Il pulsante sotto il messaggio: "Consenti" finché il dialog può ancora comparire. */
    fun actionLabel(status: PermissionStatus): String =
        if (status == PermissionStatus.BLOCKED) "Apri impostazioni" else "Consenti"

    /** Il messaggio quando "Modifica" o "Elimina" non hanno il permesso di scrittura. */
    fun writeDeniedMessage(status: PermissionStatus): String? = when (status) {
        PermissionStatus.GRANTED -> null
        PermissionStatus.DENIED -> "Serve il permesso di scrittura del calendario"
        PermissionStatus.BLOCKED -> "Serve il permesso di scrittura del calendario: attivalo in Info app › Autorizzazioni"
    }

    /**
     * Cosa mostra "Nuovo evento" (1.402): senza permesso sempre il messaggio con "Consenti", anche mentre il
     * dialog di Android è aperto o se non è mai comparso. Prima restava una pagina vuota con "Salva" grigio.
     */
    fun editPage(granted: Boolean, loaded: Boolean, hasDraft: Boolean): EditPage = when {
        !granted -> EditPage.ASK
        hasDraft -> EditPage.FORM
        !loaded -> EditPage.LOADING
        else -> EditPage.MISSING
    }

    const val DELETE_FAILED = "Non è stato possibile eliminare l'evento"
}

enum class EditPage { ASK, LOADING, MISSING, FORM }

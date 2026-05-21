package com.matteo.rosterenhancer.domain.model

object CcnlTables {

    /**
     * Paga Mensile Base (Lorda) valida dal 2024 per le Guardie Giurate (Ruolo Tecnico Operativo)
     */
    val mensilitaBase2024 = mapOf(
        1 to 2050.00, // Stima per coerenza (Alto)
        2 to 1940.00, // Stima per coerenza
        3 to 1840.00, // Stima per coerenza
        4 to 1750.00, // Stima per coerenza
        5 to 1690.00, // Stima per coerenza
        6 to 1640.00, // Stima per coerenza
        7 to 1601.86  // Base corretta fornita dall'utente (corrisponde a 9.26/h)
    )

    /**
     * Valore del singolo Scatto triennale (Mensile, Lordo).
     * I lavoratori hanno diritto fino a un massimo di 6 scatti di anzianità.
     */
    val scattiAnzianitaMensili = mapOf(
        1 to 26.12,
        2 to 23.83,
        3 to 22.46,
        4 to 22.55, // Aggiornato da cedolino
        5 to 20.52,
        6 to 19.66,
        7 to 18.00
    )

    const val MONTHLY_DIVISOR = 173.0

    fun calcolaPagaOrariaBase(livello: Int, numeroScatti: Int): Double {
        val pagaMensile = mensilitaBase2024[livello] ?: mensilitaBase2024[6]!! 
        val valoreScatto = scattiAnzianitaMensili[livello] ?: scattiAnzianitaMensili[6]!!
        val scattiEffettivi = numeroScatti.coerceIn(0, 6) 

        val mensilitaTotale = pagaMensile + (valoreScatto * scattiEffettivi)
        return mensilitaTotale / MONTHLY_DIVISOR
    }
}



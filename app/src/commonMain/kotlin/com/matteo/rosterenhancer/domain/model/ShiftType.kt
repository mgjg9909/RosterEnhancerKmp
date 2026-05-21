package com.matteo.rosterenhancer.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShiftType {
    WORK,           // Turno lavorativo
    REST_1,         // R1 - Riposo 1
    REST_2,         // R2 - Riposo 2
    DAY_OFF,        // RO - Riposo Ordinario
    ABSENT,         // ASSENTE
    PARENTAL_LEAVE, // CP - Congedo Parentale
    HOLIDAY,        // Festività
    INTERVENTO,     // Intervento (ore raddoppiate)
    MANCATO_R1,     // Lavoro in giorno R1 (maggiorato)
    MANCATO_R2,     // Lavoro in giorno R2 (maggiorato 40%)
    OTHER           // Altro codice non riconosciuto
}



package com.matteo.rosterenhancer.util

/**
 * Raggruppa i codici mansione in gruppi logici per la visualizzazione.
 *
 * Regole di accorpamento:
 *   BAG, BAG_S              → "BAG"
 *   COF                     → Invariato
 *   FAS                     → Invariato
 *   MER                     → Invariato
 *   PAX, PAM, PAF           → "PAX"
 *   PER, PEM, PES           → "PER"
 *   SBH                     → Invariato
 *   SER, SEM, SEF, MBL, MBS → "SER"
 *   SPV                     → Invariato
 *   TAM, TAF, TAM_S, TAF_S  → "TAG"
 *   TOJ                     → Invariato
 *
 * Tutti gli altri codici vengono mostrati invariati (uppercase).
 */
object RoleGroups {

    /**
     * Mappa ogni codice raw → nome del gruppo visualizzato.
     * La chiave è uppercase per confronto case-insensitive.
     */
    private val roleToGroup: Map<String, String> = mapOf(
        // BAG
        "BAG"   to "BAG",
        "BAG_S" to "BAG",
        
        // COF
        "COF"   to "COF",
        
        // FAS
        "FAS"   to "FAS",
        
        // MER
        "MER"   to "MER",
        
        // PAX
        "PAX"   to "PAX",
        "PAM"   to "PAX",
        "PAF"   to "PAX",
        
        // PER
        "PER"   to "PER",
        "PEM"   to "PER",
        "PES"   to "PER",
        
        // SBH
        "SBH"   to "SBH",
        
        // SER
        "SER"   to "SER",
        "SEM"   to "SER",
        "SEF"   to "SER",
        "MBL"   to "SER",
        "MBS"   to "SER",
        
        // SPV
        "SPV"   to "SPV",
        
        // TAG
        "TAM"   to "TAG",
        "TAF"   to "TAG",
        "TAM_S" to "TAG",
        "TAF_S" to "TAG",
        
        // TOJ
        "TOJ"   to "TOJ"
    )

    /**
     * Restituisce il nome del gruppo per un codice raw.
     * Se il codice non ha un gruppo definito, lo restituisce così com'è (uppercase).
     * Se null, restituisce "—".
     */
    fun normalize(rawRole: String?): String {
        if (rawRole == null) return "—"
        return roleToGroup[rawRole.trim().uppercase()] ?: rawRole.trim().uppercase()
    }

    /**
     * Restituisce true se il codice raw appartiene al gruppo dato.
     * Utile per i filtri.
     */
    fun matches(rawRole: String?, groupName: String): Boolean =
        normalize(rawRole) == groupName

    /**
     * Restituisce tutti i codici raw che appartengono al gruppo dato,
     * ordinati alfabeticamente.
     * Utile per costruire i sub-chip di un gruppo selezionato.
     */
    fun getCodesForGroup(groupName: String): List<String> =
        roleToGroup.entries
            .filter { it.value == groupName }
            .map { it.key }
            .sorted()
}




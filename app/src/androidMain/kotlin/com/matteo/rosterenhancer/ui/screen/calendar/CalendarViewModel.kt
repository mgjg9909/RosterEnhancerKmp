package com.matteo.rosterenhancer.ui.screen.calendar
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.plusHours

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.util.DataStoreManager
import com.matteo.rosterenhancer.util.RoleGroups

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import com.matteo.rosterenhancer.util.YearMonth
import com.matteo.rosterenhancer.domain.model.ShiftNote


enum class CalendarSortMode {
    ALPHABETICAL,   // A → Z per cognome
    BY_SHIFT_TIME,  // per orario di inizio turno
    BY_ROLE         // per mansione
}

enum class CalendarShiftFilter {
    ALL, WORK_ONLY, REST_ONLY
}

data class CalendarUiState(
    val sortMode: CalendarSortMode = CalendarSortMode.ALPHABETICAL,
    val shiftFilter: CalendarShiftFilter = CalendarShiftFilter.ALL,
    val activeRoleFilter: String? = null,     // gruppo (es. "PAX")
    val activeSubRoleFilter: String? = null,   // codice specifico (es. "PAF")
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)

class CalendarViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager,
    private val credentialsManager: com.matteo.rosterenhancer.data.security.CredentialsManager
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        if (com.matteo.rosterenhancer.BuildConfig.DEBUG) println("CalendarViewModel" + ": " + "Costruttore avviato")
        // [DIAGNOSTICA] Disattivata sincronizzazione automatica all'avvio del tab
        /*
        viewModelScope.launch {
            if (credentialsManager.hasCredentials()) {
                _syncStatus.value = "Sincronizzazione turni dal portale..."
                repository.syncWithWeb()
                    .onSuccess { msg -> 
                        _syncStatus.value = "Roster aggiornato correttamente"
                        kotlinx.coroutines.delay(3000)
                        _syncStatus.value = null
                    }
                    .onFailure { err -> 
                        _syncStatus.value = "Sync fallito: ${err.localizedMessage}"
                        kotlinx.coroutines.delay(5000)
                        _syncStatus.value = null
                    }
            }
        }
        */
    }

    // Turni del mese per l'utente (per i dot nel calendario)
    val myShiftsThisMonth: StateFlow<List<Shift>> = combine(
        _currentMonth,
        dataStoreManager.selfMatricola
    ) { month, matricola -> 
        if (com.matteo.rosterenhancer.BuildConfig.DEBUG) println("CalendarViewModel" + ": " + "FLOW_DEBUG: Mese=$month, Matricola='$matricola'")
        Pair(month, matricola) 
    }
        .flatMapLatest { (month, matricola) ->
            if (matricola.isBlank()) {
                if (com.matteo.rosterenhancer.BuildConfig.DEBUG) println("CalendarViewModel" + ": " + "DIAGNOSTICA: Matricola VUOTA, cerco turni per ID 'self' come fallback")
                repository.getShiftsForEmployeeInRange(
                    employeeId = "self",
                    from = month.atDay(1),
                    to = month.atEndOfMonth()
                )
            } else {
                repository.getShiftsForEmployeeInRange(
                    employeeId = matricola,
                    from = month.atDay(1),
                    to = month.atEndOfMonth()
                ).onEach { list ->
                    if (com.matteo.rosterenhancer.BuildConfig.DEBUG) println("CalendarViewModel" + ": " + "DIAGNOSTICA: Mese=${month}, Matricola=${matricola}, TurniTrovati=${list.size}")
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Turni del giorno selezionato — con sort e filtri applicati
    val shiftsForSelectedDay: StateFlow<List<Shift>> = combine(
        _selectedDate,
        _uiState
    ) { date, ui -> Pair(date, ui) }
        .flatMapLatest { (date, ui) ->
            if (date == null) flowOf(emptyList())
            else repository.getShiftsForDate(date).map { shifts ->
                val filtered = applyFiltersAndSort(shifts, ui)
                if (com.matteo.rosterenhancer.BuildConfig.DEBUG) println("CalendarViewModel" + ": " + "DIAGNOSTICA: Giorno=${date}, Totali=${shifts.size}, Filtrati=${filtered.size}")
                filtered
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Note per i turni del giorno selezionato
    val notesForSelectedDay: StateFlow<Map<String, ShiftNote>> = _selectedDate
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyMap())
            else repository.getNotesInRangeFlow(date, date).map { list ->
                list.associateBy { it.employeeName.uppercase().trim() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Gruppi mansione presenti nel giorno (chip di primo livello)
    val availableRoles: StateFlow<List<String>> = _selectedDate
        .flatMapLatest { date ->
            if (date == null) flowOf(emptyList())
            else repository.getShiftsForDate(date).map { shifts ->
                shifts
                    .filter { it.shiftType == ShiftType.WORK }
                    .mapNotNull { it.role }
                    .map { RoleGroups.normalize(it) }
                    .distinct()
                    .sorted()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Codici specifici presenti nel giorno per il gruppo selezionato (chip di secondo livello)
    // Es: gruppo "PAX" selezionato → ["PAF", "PAM", "PAX"] (solo quelli effettivamente presenti)
    val availableSubRoles: StateFlow<List<String>> = combine(
        _selectedDate,
        _uiState
    ) { date, ui -> Pair(date, ui.activeRoleFilter) }
        .flatMapLatest { (date, group) ->
            if (date == null || group == null) flowOf(emptyList())
            else repository.getShiftsForDate(date).map { shifts ->
                val codesInGroup = RoleGroups.getCodesForGroup(group).toSet()
                shifts
                    .filter { it.shiftType == ShiftType.WORK }
                    .mapNotNull { it.role?.trim()?.uppercase() }
                    .filter { it in codesInGroup }
                    .distinct()
                    .sorted()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Applicazione sort + filtri ───────────────────────────────────────────

    private fun applyFiltersAndSort(shifts: List<Shift>, ui: CalendarUiState): List<Shift> {
        var result = shifts

        // 0. Ricerca per nome
        if (ui.searchQuery.isNotBlank()) {
            result = result.filter { it.employeeName.contains(ui.searchQuery, ignoreCase = true) }
        }

        // 1. Filtro per tipo (lavoro / riposo / tutti)
        result = when (ui.shiftFilter) {
            CalendarShiftFilter.ALL       -> result
            CalendarShiftFilter.WORK_ONLY -> result.filter { it.shiftType == ShiftType.WORK }
            CalendarShiftFilter.REST_ONLY -> result.filter {
                it.shiftType == ShiftType.REST_1 ||
                it.shiftType == ShiftType.REST_2 ||
                it.shiftType == ShiftType.DAY_OFF
            }
        }

        // 2. Filtro mansione: prima il sub-codice (più specifico), poi il gruppo
        when {
            ui.activeSubRoleFilter != null ->
                result = result.filter {
                    it.role?.trim()?.uppercase() == ui.activeSubRoleFilter
                }
            ui.activeRoleFilter != null ->
                result = result.filter { RoleGroups.matches(it.role, ui.activeRoleFilter) }
        }

        // 3. Ordinamento
        result = when (ui.sortMode) {
            CalendarSortMode.ALPHABETICAL  ->
                result.sortedBy { it.employeeName.trim().uppercase() }
            CalendarSortMode.BY_SHIFT_TIME ->
                result.sortedWith(compareBy(
                    { it.startTime == null },
                    { it.startTime }
                ))
            CalendarSortMode.BY_ROLE       ->
                result.sortedWith(compareBy(
                    { RoleGroups.normalize(it.role) },  // raggruppa per gruppo normalizzato
                    { it.startTime == null },            // riposi in fondo dentro il gruppo
                    { it.startTime }                     // poi per orario di arrivo
                ))
        }

        return result
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    fun previousMonth() { _currentMonth.update { it.minusMonths(1) } }
    fun nextMonth()     { _currentMonth.update { it.plusMonths(1) } }
    fun setMonth(month: YearMonth) { _currentMonth.value = month }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    fun setSortMode(mode: CalendarSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun setShiftFilter(filter: CalendarShiftFilter) {
        _uiState.update { it.copy(shiftFilter = filter) }
    }

    fun setRoleFilter(role: String?) {
        // Quando si cambia gruppo, resetta il sub-filtro
        _uiState.update { it.copy(activeRoleFilter = role, activeSubRoleFilter = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSubRoleFilter(subRole: String?) {
        _uiState.update { it.copy(activeSubRoleFilter = subRole) }
    }

    // ─── Note Turni ────────────────────────────────────────────────────────
    
    fun saveShiftNote(shift: Shift, text: String, extraMinutes: Int, otStart: LocalTime? = null, otEnd: LocalTime? = null) {
        viewModelScope.launch {
            repository.upsertNote(shift.employeeName, shift.date, text, extraMinutes)
            // Aggiorna anche il turno vero e proprio per il calcolo dello stipendio
            repository.upsertManualShift(shift.copy(
                overtimeMinutes = extraMinutes,
                overtimeStartTime = otStart,
                overtimeEndTime = otEnd,
                notes = text.trim()
            ))
        }
    }

    // ─── Turni Manuali & Straordinari ────────────────────────────────────────

    fun addManualShift(
        date: LocalDate,
        startTime: String,
        duration: Int,
        role: String,
        shiftType: ShiftType = ShiftType.WORK
    ) {
        viewModelScope.launch {
            val h = try { LocalTime.parse(startTime) } catch (e: Exception) { LocalTime(8, 0) }
            val newShift = Shift(
                employeeId = "", // Gestito dal repository via DataStore
                employeeName = "", // Gestito dal repository via DataStore
                date = date,
                startTime = h,
                durationHours = duration,
                endTime = h.plusHours(duration.toLong()),
                role = role,
                shiftType = shiftType,
                isManual = true
            )
            repository.upsertManualShift(newShift)
        }
    }

    fun updateShift(shift: Shift) {
        viewModelScope.launch {
            // Un update manuale segna sempre il turno come "Manual" per protezione
            repository.upsertManualShift(shift.copy(isManual = true))
        }
    }

    fun deleteShift(shiftId: Long) {
        // Opzionale: implementare se necessario, per ora usiamo upsert manuale
    }
}













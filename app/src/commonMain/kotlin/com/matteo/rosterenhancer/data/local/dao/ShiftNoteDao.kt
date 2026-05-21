package com.matteo.rosterenhancer.data.local.dao

import androidx.room.*
import com.matteo.rosterenhancer.data.local.entity.ShiftNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface ShiftNoteDao {

    @Query("SELECT * FROM shift_notes WHERE employeeName = :name AND date = :date LIMIT 1")
    fun getNoteForShift(name: String, date: LocalDate): Flow<ShiftNoteEntity?>

    @Query("SELECT * FROM shift_notes WHERE employeeName = :name AND date = :date LIMIT 1")
    suspend fun getNoteForShiftAsync(name: String, date: LocalDate): ShiftNoteEntity?

    @Query("SELECT * FROM shift_notes WHERE date BETWEEN :from AND :to")
    suspend fun getNotesInRange(from: LocalDate, to: LocalDate): List<ShiftNoteEntity>

    @Query("SELECT * FROM shift_notes WHERE date BETWEEN :from AND :to")
    fun getNotesInRangeFlow(from: LocalDate, to: LocalDate): Flow<List<ShiftNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: ShiftNoteEntity)

    @Query("DELETE FROM shift_notes WHERE employeeName = :name AND date = :date")
    suspend fun delete(name: String, date: LocalDate)
}



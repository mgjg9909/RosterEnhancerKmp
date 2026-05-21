package com.matteo.rosterenhancer.data.local.dao

import androidx.room.*
import com.matteo.rosterenhancer.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Dao
interface ShiftDao {

    @Query("SELECT * FROM shifts WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getShiftForEmployee(employeeId: String, date: LocalDate): ShiftEntity?

    @Query("SELECT * FROM shifts WHERE employeeId = :employeeId AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun getShiftsForEmployeeInRange(employeeId: String, from: LocalDate, to: LocalDate): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE date = :date ORDER BY startTime ASC")
    fun getShiftsForDate(date: LocalDate): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE employeeId = :employeeId ORDER BY date ASC")
    fun getAllShiftsForEmployee(employeeId: String): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE date BETWEEN :from AND :to AND shiftType = 'WORK'")
    fun getAllWorkShiftsInRange(from: LocalDate, to: LocalDate): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE date BETWEEN :from AND :to")
    suspend fun getShiftsInRangeAsync(from: LocalDate, to: LocalDate): List<ShiftEntity>

    @Query("SELECT * FROM shifts WHERE (employeeId = :matricola OR employeeId = 'self') AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getMyShiftsInRangeAsync(matricola: String, from: LocalDate, to: LocalDate): List<ShiftEntity>

    @Query("""
        SELECT * FROM shifts 
        WHERE shiftType = 'WORK'
        AND (
            (date = :today AND (
                (startTime <= endTime AND :currentTime >= startTime AND :currentTime <= endTime)
                OR
                (startTime > endTime AND :currentTime >= startTime)
            ))
            OR
            (date = :yesterday AND (
                startTime > endTime AND :currentTime <= endTime
            ))
        )
        ORDER BY startTime ASC
    """)
    fun getShiftsWorkingNow(today: LocalDate, yesterday: LocalDate, currentTime: LocalTime): kotlinx.coroutines.flow.Flow<List<ShiftEntity>>

    @Query("""
        SELECT * FROM shifts
        WHERE date = :date
        AND shiftType NOT IN ('WORK')
        ORDER BY employeeName ASC
    """)
    fun getEmployeesOffToday(date: LocalDate): Flow<List<ShiftEntity>>

    @Query("SELECT SUM(durationHours) FROM shifts WHERE employeeId = :employeeId AND shiftType = 'WORK' AND date BETWEEN :from AND :to")
    suspend fun getTotalHoursForEmployee(employeeId: String, from: LocalDate, to: LocalDate): Int?

    @Query("SELECT COUNT(*) FROM shifts WHERE employeeId = :employeeId AND shiftType = :type AND date BETWEEN :from AND :to")
    suspend fun countShiftsByType(employeeId: String, type: String, from: LocalDate, to: LocalDate): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shifts: List<ShiftEntity>)

    @Delete
    suspend fun delete(shift: ShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShift(shift: ShiftEntity)

    @Query("DELETE FROM shifts WHERE monthRosterId = :rosterId")
    suspend fun deleteByRoster(rosterId: Long)

    @Query("DELETE FROM shifts WHERE date = :date AND (employeeId = :matricola OR employeeId = 'self' OR (isManual = 1 AND employeeName = :name) OR (isManual = 1 AND employeeName LIKE 'Matteo%'))")
    suspend fun deleteAnyMine(date: LocalDate, matricola: String, name: String)

    @Query("DELETE FROM shifts WHERE employeeName = :employeeName AND date = :date")
    suspend fun deleteByEmployeeNameAndDate(employeeName: String, date: LocalDate)

    @Query("DELETE FROM shifts WHERE employeeId = :employeeId AND date = :date")
    suspend fun deleteByEmployeeIdAndDate(employeeId: String, date: LocalDate)

    @Query("DELETE FROM shifts WHERE employeeId = :employeeId AND date BETWEEN :from AND :to")
    suspend fun deleteShiftsByEmployeeIdAndRange(employeeId: String, from: LocalDate, to: LocalDate)

    @Query("SELECT DISTINCT role FROM shifts WHERE shiftType = 'WORK' AND role IS NOT NULL ORDER BY role ASC")
    suspend fun getAllRoles(): List<String>
}


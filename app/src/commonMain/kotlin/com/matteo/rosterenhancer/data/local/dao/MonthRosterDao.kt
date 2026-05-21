package com.matteo.rosterenhancer.data.local.dao

import androidx.room.*
import com.matteo.rosterenhancer.data.local.entity.MonthRosterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthRosterDao {

    @Query("SELECT * FROM month_rosters ORDER BY year DESC, month DESC")
    fun getAllRosters(): Flow<List<MonthRosterEntity>>

    @Query("SELECT * FROM month_rosters WHERE month = :month AND year = :year LIMIT 1")
    suspend fun getRosterForMonth(month: Int, year: Int): MonthRosterEntity?

    @Query("SELECT * FROM month_rosters ORDER BY year DESC, month DESC LIMIT 1")
    suspend fun getLatestRoster(): MonthRosterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(roster: MonthRosterEntity): Long

    @Update
    suspend fun update(roster: MonthRosterEntity)

    @Delete
    suspend fun delete(roster: MonthRosterEntity)

    @Query("SELECT COUNT(*) FROM month_rosters")
    suspend fun count(): Int
}


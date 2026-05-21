package com.matteo.rosterenhancer.data.local.dao

import androidx.room.*
import com.matteo.rosterenhancer.data.local.entity.LearningLogEntity
import com.matteo.rosterenhancer.data.local.entity.PayslipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PayslipDao {
    @Query("SELECT * FROM payslips ORDER BY year DESC, month DESC")
    fun getAllPayslips(): Flow<List<PayslipEntity>>

    @Query("SELECT * FROM payslips WHERE month = :month AND year = :year LIMIT 1")
    suspend fun getPayslipForMonth(month: Int, year: Int): PayslipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayslip(payslip: PayslipEntity)

    @Delete
    suspend fun deletePayslip(payslip: PayslipEntity)

    @Query("SELECT * FROM learning_logs ORDER BY timestamp DESC")
    fun getAllLearningLogs(): Flow<List<LearningLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearningLog(log: LearningLogEntity)

    @Delete
    suspend fun deleteLearningLog(log: LearningLogEntity)
}


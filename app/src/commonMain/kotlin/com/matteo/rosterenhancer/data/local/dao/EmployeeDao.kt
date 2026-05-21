package com.matteo.rosterenhancer.data.local.dao

import androidx.room.*
import com.matteo.rosterenhancer.data.local.entity.EmployeeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {

    @Query("SELECT * FROM employees ORDER BY fullName ASC")
    fun getAllEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE fullName LIKE '%' || :query || '%' ORDER BY fullName ASC")
    fun searchEmployees(query: String): Flow<List<EmployeeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(employees: List<EmployeeEntity>)

    @Update
    suspend fun update(employee: EmployeeEntity)

    @Query("UPDATE employees SET isSelf = 0")
    suspend fun clearSelf()

    @Query("UPDATE employees SET isSelf = 1 WHERE id = :matricola")
    suspend fun setSelf(matricola: String)

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun count(): Int

    @Query("DELETE FROM employees WHERE fullName IN (:names)")
    suspend fun deleteByNames(names: List<String>)
}

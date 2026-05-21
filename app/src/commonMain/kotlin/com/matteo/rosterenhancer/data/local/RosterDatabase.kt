package com.matteo.rosterenhancer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.ConstructedBy
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.migration.Migration
import com.matteo.rosterenhancer.data.local.dao.*
import com.matteo.rosterenhancer.data.local.entity.*

@Database(
    entities = [
        EmployeeEntity::class,
        ShiftEntity::class,
        MonthRosterEntity::class,
        ShiftNoteEntity::class,
        PayslipEntity::class,
        LearningLogEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(RosterDatabaseConstructor::class)
abstract class RosterDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun shiftDao(): ShiftDao
    abstract fun monthRosterDao(): MonthRosterDao
    abstract fun shiftNoteDao(): ShiftNoteDao
    abstract fun payslipDao(): PayslipDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE shifts ADD COLUMN overtimeStartTime TEXT")
                connection.execSQL("ALTER TABLE shifts ADD COLUMN overtimeEndTime TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE payslips ADD COLUMN filePath TEXT")
            }
        }
    }
}

// In KMP Room 2.7.0, usiamo expect per dire al compilatore che ci sarà un costruttore specifico
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RosterDatabaseConstructor : RoomDatabaseConstructor<RosterDatabase>

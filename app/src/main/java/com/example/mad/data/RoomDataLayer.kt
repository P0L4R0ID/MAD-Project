package com.example.mad.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * User roles used by the application.
 * Stored as an enum as part of the Room type-conversion layer.
 */
enum class UserRole {
    EMPLOYEE,
    MANAGER
}

/**
 * Leave application workflow states.
 */
enum class ApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED
}

/**
 * User account table.
 *
 * Relationships:
 * - Referenced by LeaveApplication.employeeId for the employee who submitted the request.
 * - Referenced by LeaveApplication.managerId for the manager who approved or rejected it.
 */
@Entity(
    tableName = "user",
    indices = [
        Index(value = ["userName"], unique = true),
        Index(value = ["email"], unique = true),
        Index(value = ["phoneNumber"], unique = true),
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val userId: Int = 0,
    val userName: String,
    val fullName: String,
    val password: String,
    val email: String,
    val phoneNumber: String,
    val profilePicture: String? = null,
    val role: UserRole,
    val ptoBalance: Double,
    val createdAt: Long,
)

/**
 * Leave type lookup table.
 * Populates the dropdown options used in the application form.
 */
@Entity(tableName = "leave_type")
data class LeaveTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val leaveTypeId: Int = 0,
    val typeName: String,
)

/**
 * Leave application table.
 *
 * Relationships:
 * - employeeId links to the User table for the applicant.
 * - leaveTypeId links to LeaveType for the selected leave category.
 * - managerId links to the User table for the manager action.
 */
@Entity(
    tableName = "leave_application",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LeaveTypeEntity::class,
            parentColumns = ["leaveTypeId"],
            childColumns = ["leaveTypeId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["managerId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["employeeId"]),
        Index(value = ["leaveTypeId"]),
        Index(value = ["managerId"]),
        Index(value = ["startDate"]),
        Index(value = ["endDate"]),
        Index(value = ["status"]),
    ]
)
data class LeaveApplicationEntity(
    @PrimaryKey(autoGenerate = true)
    val applicationId: Int = 0,
    val employeeId: Int,
    val leaveTypeId: Int,
    val startDate: Long,
    val endDate: Long,
    val totalDuration: Double,
    val reason: String,
    val attachmentPath: String? = null,
    val status: ApplicationStatus,
    val managerId: Int? = null,
    val approvalDate: Long? = null,
    val rejectReason: String? = null,
)

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUsers(users: List<UserEntity>): List<Long>

    @androidx.room.Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM user WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("SELECT * FROM user WHERE userName = :userName LIMIT 1")
    suspend fun getUserByUserName(userName: String): UserEntity?

    @Query("SELECT * FROM user WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM user WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getUserByPhoneNumber(phoneNumber: String): UserEntity?

    @Query("SELECT * FROM user WHERE role = :role ORDER BY createdAt DESC")
    suspend fun getUsersByRole(role: UserRole): List<UserEntity>

    @Query("SELECT * FROM user")
    suspend fun getAllUsers(): List<UserEntity>

    @Delete
    suspend fun deleteUser(user: UserEntity): Int
}

@Dao
interface LeaveTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaveType(leaveType: LeaveTypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaveTypes(leaveTypes: List<LeaveTypeEntity>): List<Long>

    @Query("SELECT * FROM leave_type ORDER BY typeName ASC")
    suspend fun getAllLeaveTypes(): List<LeaveTypeEntity>

    @Query("SELECT * FROM leave_type WHERE leaveTypeId = :leaveTypeId LIMIT 1")
    suspend fun getLeaveTypeById(leaveTypeId: Int): LeaveTypeEntity?

    @Query("SELECT * FROM leave_type WHERE typeName = :typeName LIMIT 1")
    suspend fun getLeaveTypeByName(typeName: String): LeaveTypeEntity?
}

/**
 * DAO used for leave history, approvals, and detail screens.
 * All history queries are sorted with the newest application first.
 */
@Dao
interface LeaveApplicationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApplication(application: LeaveApplicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApplications(applications: List<LeaveApplicationEntity>): List<Long>

    @Query("SELECT * FROM leave_application WHERE applicationId = :applicationId LIMIT 1")
    suspend fun getApplicationById(applicationId: Int): LeaveApplicationEntity?

    @Query("SELECT * FROM leave_application WHERE employeeId = :employeeId ORDER BY startDate DESC, applicationId DESC")
    suspend fun getApplicationsForEmployee(employeeId: Int): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application WHERE status = :status ORDER BY startDate DESC, applicationId DESC")
    suspend fun getApplicationsByStatus(status: ApplicationStatus): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application ORDER BY startDate DESC, applicationId DESC")
    suspend fun getAllApplicationsNewestFirst(): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application WHERE employeeId = :employeeId AND status = :status ORDER BY startDate DESC, applicationId DESC")
    suspend fun getApplicationsForEmployeeByStatus(employeeId: Int, status: ApplicationStatus): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application WHERE managerId = :managerId ORDER BY approvalDate DESC, applicationId DESC")
    suspend fun getApplicationsHandledByManager(managerId: Int): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application WHERE status = 'PENDING' ORDER BY startDate DESC, applicationId DESC")
    suspend fun getPendingApplicationsNewestFirst(): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application WHERE status = 'REJECTED' ORDER BY approvalDate DESC, applicationId DESC")
    suspend fun getRejectedApplicationsNewestFirst(): List<LeaveApplicationEntity>

    @Query("SELECT * FROM leave_application WHERE status = 'APPROVED' ORDER BY approvalDate DESC, applicationId DESC")
    suspend fun getApprovedApplicationsNewestFirst(): List<LeaveApplicationEntity>

    @Query("UPDATE leave_application SET status = 'COMPLETED' WHERE status = 'APPROVED' AND endDate < :currentTimeMillis")
    suspend fun autoCompletePastLeaves(currentTimeMillis: Long)

    @Query("UPDATE leave_application SET status = :status, managerId = :managerId, approvalDate = :actionDate, rejectReason = :rejectReason WHERE applicationId = :applicationId")
    suspend fun updateApplicationStatus(
        applicationId: Int,
        status: ApplicationStatus,
        managerId: Int?,
        actionDate: Long?,
        rejectReason: String?,
    )

    @Query("DELETE FROM leave_application WHERE applicationId = :applicationId")
    suspend fun deleteApplication(applicationId: Int)

    @Query("SELECT * FROM leave_application ORDER BY startDate DESC, applicationId DESC")
    fun getAllApplicationsLive(): kotlinx.coroutines.flow.Flow<List<LeaveApplicationEntity>>

}

/**
 * Parent Room database that exposes all application tables through their DAOs.
 */
@Database(
    entities = [
        UserEntity::class,
        LeaveTypeEntity::class,
        LeaveApplicationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LeaveEaseDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun leaveTypeDao(): LeaveTypeDao
    abstract fun leaveApplicationDao(): LeaveApplicationDao

    companion object {
        @Volatile
        private var INSTANCE: LeaveEaseDatabase? = null

        fun getDatabase(context: Context): LeaveEaseDatabase {
            val existingInstance = INSTANCE
            if (existingInstance != null) {
                return existingInstance
            }
            synchronized(this) {
                val newInstance = INSTANCE
                if (newInstance != null) {
                    return newInstance
                }
                val created = Room.databaseBuilder(
                    context.applicationContext,
                    LeaveEaseDatabase::class.java,
                    "leave_ease_database"
                ).build()
                INSTANCE = created
                return created
            }
        }
    }
}

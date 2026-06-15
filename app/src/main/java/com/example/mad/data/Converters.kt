package com.example.mad.data

import androidx.room.TypeConverter

/**
 * Room converters for enum-backed database columns.
 *
 * The project stores enums as strings to keep the schema readable and stable.
 */
class Converters {
    @TypeConverter
    fun fromUserRole(value: UserRole?): String? {
        return when (value) {
            null -> null
            UserRole.EMPLOYEE -> UserRole.EMPLOYEE.name
            UserRole.MANAGER -> UserRole.MANAGER.name
        }
    }

    @TypeConverter
    fun toUserRole(value: String?): UserRole? {
        return when (value) {
            null -> null
            UserRole.EMPLOYEE.name -> UserRole.EMPLOYEE
            UserRole.MANAGER.name -> UserRole.MANAGER
            else -> null
        }
    }

    @TypeConverter
    fun fromApplicationStatus(value: ApplicationStatus?): String? {
        return when (value) {
            null -> null
            ApplicationStatus.PENDING -> ApplicationStatus.PENDING.name
            ApplicationStatus.APPROVED -> ApplicationStatus.APPROVED.name
            ApplicationStatus.REJECTED -> ApplicationStatus.REJECTED.name
            ApplicationStatus.COMPLETED -> ApplicationStatus.COMPLETED.name // <-- Added this
        }
    }

    @TypeConverter
    fun toApplicationStatus(value: String?): ApplicationStatus? {
        return when (value) {
            null -> null
            ApplicationStatus.PENDING.name -> ApplicationStatus.PENDING
            ApplicationStatus.APPROVED.name -> ApplicationStatus.APPROVED
            ApplicationStatus.REJECTED.name -> ApplicationStatus.REJECTED
            ApplicationStatus.COMPLETED.name -> ApplicationStatus.COMPLETED // <-- Added this
            else -> null
        }
    }
}

package com.example.mad.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LeaveEaseRepository(
    private val userDao: UserDao,
    private val leaveTypeDao: LeaveTypeDao,
    private val leaveApplicationDao: LeaveApplicationDao,
) {
    suspend fun getUserByUserName(userName: String): UserEntity? = userDao.getUserByUserName(userName)
    suspend fun getUserByEmail(email: String): UserEntity? = userDao.getUserByEmail(email)
    suspend fun getUserById(userId: Int): UserEntity? = userDao.getUserById(userId)
    suspend fun getAllLeaveTypes(): List<LeaveTypeEntity> = leaveTypeDao.getAllLeaveTypes()
    suspend fun getApplicationsForEmployee(employeeId: Int): List<LeaveApplicationEntity> = leaveApplicationDao.getApplicationsForEmployee(employeeId)
    suspend fun getApplicationsForEmployeeByStatus(employeeId: Int, status: ApplicationStatus): List<LeaveApplicationEntity> = leaveApplicationDao.getApplicationsForEmployeeByStatus(employeeId, status)
    suspend fun getPendingApplicationsNewestFirst(): List<LeaveApplicationEntity> = leaveApplicationDao.getPendingApplicationsNewestFirst()
    suspend fun insertLeaveApplication(application: LeaveApplicationEntity): Long = leaveApplicationDao.insertApplication(application)
    suspend fun updateApplicationStatus(applicationId: Int, status: ApplicationStatus, managerId: Int?, actionDate: Long?, rejectReason: String?) {
        leaveApplicationDao.updateApplicationStatus(applicationId, status, managerId, actionDate, rejectReason)
    }
    suspend fun withdrawApplication(applicationId: Int) {
        leaveApplicationDao.deleteApplication(applicationId)
    }
}

sealed class AuthRoute {
    data object Login : AuthRoute()
    data object EmployeeHome : AuthRoute()
    data object ManagerHome : AuthRoute()
}

data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val route: AuthRoute = AuthRoute.Login,
    val forgotPasswordRequested: Boolean = false,
    val loggedInUser: UserEntity? = null,
)

class AuthViewModel(
    private val repository: LeaveEaseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onUsernameChange(username: String) { _uiState.update { it.copy(username = username, errorMessage = null) } }
    fun onPasswordChange(password: String) { _uiState.update { it.copy(password = password, errorMessage = null) } }

    fun login() {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username and password are required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val user = repository.getUserByUserName(username)
            if (user == null || user.password != password) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid username or password.") }
                return@launch
            }
            val route = when (user.role) {
                UserRole.EMPLOYEE -> AuthRoute.EmployeeHome
                UserRole.MANAGER -> AuthRoute.ManagerHome
            }
            _uiState.update { it.copy(isLoading = false, route = route, loggedInUser = user) }
        }
    }

    fun requestForgotPassword() { _uiState.update { it.copy(forgotPasswordRequested = true) } }
    fun clearForgotPasswordRequest() { _uiState.update { it.copy(forgotPasswordRequested = false) } }
    fun resetNavigation() { _uiState.update { it.copy(route = AuthRoute.Login, loggedInUser = null, password = "") } }
}

enum class LeaveRecordsTab { ALL, PENDING, APPROVED, REJECTED }

data class LeaveApplicationFormState(
    val selectedLeaveType: LeaveTypeEntity? = null,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val reason: String = "",
    val attachmentPath: String? = null,
    val totalDuration: Double = 0.0,
    val selectedTab: LeaveRecordsTab = LeaveRecordsTab.ALL,
    val errorMessage: String? = null,
    val isSubmitted: Boolean = false,
)

data class EmployeeUiState(
    val currentEmployee: UserEntity? = null,
    val leaveTypes: List<LeaveTypeEntity> = emptyList(),
    val allRecords: List<LeaveApplicationEntity> = emptyList(),
    val filteredRecords: List<LeaveApplicationEntity> = emptyList(),
    val formState: LeaveApplicationFormState = LeaveApplicationFormState(),
)

class EmployeeViewModel(
    private val repository: LeaveEaseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmployeeUiState())
    val uiState: StateFlow<EmployeeUiState> = _uiState.asStateFlow()

    fun loadEmployee(employeeId: Int) {
        viewModelScope.launch {
            val employee = repository.getUserById(employeeId)
            val leaveTypes = repository.getAllLeaveTypes()
            val records = repository.getApplicationsForEmployee(employeeId)
            _uiState.update { state ->
                state.copy(
                    currentEmployee = employee,
                    leaveTypes = leaveTypes,
                    allRecords = records,
                    filteredRecords = filterRecords(records, state.formState.selectedTab),
                )
            }
        }
    }

    fun onTabSelected(tab: LeaveRecordsTab) {
        _uiState.update { state ->
            state.copy(
                formState = state.formState.copy(selectedTab = tab),
                filteredRecords = filterRecords(state.allRecords, tab),
            )
        }
    }

    fun onLeaveTypeSelected(leaveType: LeaveTypeEntity) { _uiState.update { it.copy(formState = it.formState.copy(selectedLeaveType = leaveType, errorMessage = null)) } }
    fun onStartDateSelected(startDateMillis: Long) { _uiState.update { it.copy(formState = it.formState.copy(startDateMillis = startDateMillis, errorMessage = null)) }; recalculateDuration() }
    fun onEndDateSelected(endDateMillis: Long) { _uiState.update { it.copy(formState = it.formState.copy(endDateMillis = endDateMillis, errorMessage = null)) }; recalculateDuration() }
    fun onReasonChanged(reason: String) { _uiState.update { it.copy(formState = it.formState.copy(reason = reason, errorMessage = null)) } }
    fun onAttachmentSelected(path: String?) { _uiState.update { it.copy(formState = it.formState.copy(attachmentPath = path)) } }

    fun withdrawApplication(applicationId: Int) {
        viewModelScope.launch {
            repository.withdrawApplication(applicationId)
            val employeeId = _uiState.value.currentEmployee?.userId ?: return@launch
            val records = repository.getApplicationsForEmployee(employeeId)
            _uiState.update { state -> state.copy(allRecords = records, filteredRecords = filterRecords(records, state.formState.selectedTab)) }
        }
    }

    fun submitApplication() {
        val state = _uiState.value
        val form = state.formState
        val employee = state.currentEmployee
        val leaveType = form.selectedLeaveType
        val start = form.startDateMillis
        val end = form.endDateMillis
        if (employee == null) { setError("No employee session found."); return }
        if (leaveType == null) { setError("Please select a leave type."); return }
        if (start == null || end == null) { setError("Please select both start and end dates."); return }
        if (start > end) { setError("Start date cannot be later than end date."); return }
        if (form.reason.trim().isEmpty()) { setError("Reason is required."); return }
        viewModelScope.launch {
            val application = LeaveApplicationEntity(employeeId = employee.userId, leaveTypeId = leaveType.leaveTypeId, startDate = start, endDate = end, totalDuration = calculateDurationDays(start, end), reason = form.reason.trim(), attachmentPath = form.attachmentPath, status = ApplicationStatus.PENDING)
            repository.insertLeaveApplication(application)
            val refreshed = repository.getApplicationsForEmployee(employee.userId)
            _uiState.update { it.copy(allRecords = refreshed, filteredRecords = filterRecords(refreshed, form.selectedTab), formState = LeaveApplicationFormState(isSubmitted = true)) }
        }
    }

    private fun recalculateDuration() {
        val start = _uiState.value.formState.startDateMillis
        val end = _uiState.value.formState.endDateMillis
        if (start == null || end == null) return
        if (start > end) { setError("Start date cannot be later than end date."); return }
        _uiState.update { it.copy(formState = it.formState.copy(totalDuration = calculateDurationDays(start, end))) }
    }

    private fun calculateDurationDays(startMillis: Long, endMillis: Long): Double {
        val oneDayMillis = 24L * 60L * 60L * 1000L
        return ((endMillis - startMillis) / oneDayMillis).toDouble() + 1.0
    }

    private fun filterRecords(records: List<LeaveApplicationEntity>, tab: LeaveRecordsTab): List<LeaveApplicationEntity> = when (tab) {
        LeaveRecordsTab.ALL -> records.sortedByDescending { it.startDate }
        LeaveRecordsTab.PENDING -> records.filter { it.status == ApplicationStatus.PENDING }.sortedByDescending { it.startDate }
        LeaveRecordsTab.APPROVED -> records.filter { it.status == ApplicationStatus.APPROVED }.sortedByDescending { it.startDate }
        LeaveRecordsTab.REJECTED -> records.filter { it.status == ApplicationStatus.REJECTED }.sortedByDescending { it.startDate }
    }

    private fun setError(message: String) { _uiState.update { it.copy(formState = it.formState.copy(errorMessage = message)) } }
}

data class ManagerUiState(
    val pendingApplications: List<LeaveApplicationEntity> = emptyList(),
    val selectedApplicationId: Int? = null,
    val rejectReason: String = "",
    val errorMessage: String? = null,
)

class ManagerViewModel(
    private val repository: LeaveEaseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManagerUiState())
    val uiState: StateFlow<ManagerUiState> = _uiState.asStateFlow()

    fun loadPendingApprovals() {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingApplications = repository.getPendingApplicationsNewestFirst()) }
        }
    }

    fun selectApplication(applicationId: Int) { _uiState.update { it.copy(selectedApplicationId = applicationId, errorMessage = null, rejectReason = "") } }
    fun onRejectReasonChanged(reason: String) { _uiState.update { it.copy(rejectReason = reason, errorMessage = null) } }

    fun approveSelected(managerId: Int) {
        val selectedId = _uiState.value.selectedApplicationId ?: run { _uiState.update { it.copy(errorMessage = "Please select a request to approve.") }; return }
        viewModelScope.launch {
            repository.updateApplicationStatus(selectedId, ApplicationStatus.APPROVED, managerId, System.currentTimeMillis(), null)
            loadPendingApprovals()
        }
    }

    fun rejectSelected(managerId: Int) {
        val selectedId = _uiState.value.selectedApplicationId ?: run { _uiState.update { it.copy(errorMessage = "Please select a request to reject.") }; return }
        val reason = _uiState.value.rejectReason.trim()
        if (reason.isEmpty()) { _uiState.update { it.copy(errorMessage = "Rejection reason is required.") }; return }
        viewModelScope.launch {
            repository.updateApplicationStatus(selectedId, ApplicationStatus.REJECTED, managerId, System.currentTimeMillis(), reason)
            loadPendingApprovals()
        }
    }
}

package com.example.mad.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mad.data.AuthUiState
import com.example.mad.data.EmployeeUiState
import com.example.mad.data.LeaveApplicationEntity
import com.example.mad.data.LeaveApplicationFormState
import com.example.mad.data.LeaveRecordsTab
import com.example.mad.data.LeaveTypeEntity
import com.example.mad.data.ManagerUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("LeaveEase", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = uiState.username, onValueChange = onUsernameChange, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = uiState.password, onValueChange = onPasswordChange, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        uiState.errorMessage?.let { Text(it, color = Color.Red) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) { Text("Login") }
        TextButton(onClick = onForgotPasswordClick) { Text("Forgot Password") }
    }
}

@Composable
fun ForgotPasswordScreen(
    isSubmitted: Boolean,
    onSubmitRequest: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Password Recovery", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Corporate Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Employee ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (isSubmitted) Text("Request Submitted", color = Color(0xFF2E7D32))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSubmitRequest, modifier = Modifier.fillMaxWidth()) { Text("Request Submitted") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun EmployeeDashboardScreen(
    uiState: EmployeeUiState,
    onApplyClick: () -> Unit,
    onRecordsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Employee Dashboard", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Remaining PTO", fontWeight = FontWeight.Bold)
                Text("${uiState.currentEmployee?.ptoBalance ?: 0.0} days")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onApplyClick, modifier = Modifier.fillMaxWidth()) { Text("+ Apply for Leave") }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Upcoming Leaves", fontWeight = FontWeight.Bold)
                uiState.filteredRecords.take(3).forEach { record ->
                    Text("${record.reason} • ${record.status.name}")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRecordsClick) { Text("Leave Records") }
            TextButton(onClick = onLogoutClick) { Text("Log out") }
        }
    }
}

@Composable
fun LeaveApplicationFormScreen(
    uiState: LeaveApplicationFormState,
    leaveTypes: List<LeaveTypeEntity>,
    onLeaveTypeSelected: (LeaveTypeEntity) -> Unit,
    onStartDateSelected: (Long) -> Unit,
    onEndDateSelected: (Long) -> Unit,
    onReasonChanged: (String) -> Unit,
    onAttachmentSelected: (String?) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun showDatePicker(onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onPicked(picked)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Leave Application", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Box {
            OutlinedTextField(
                value = uiState.selectedLeaveType?.typeName ?: "Select leave type",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Leave Type") },
                trailingIcon = { TextButton(onClick = { expanded = true }) { Text("▼") } }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                leaveTypes.forEach { type ->
                    DropdownMenuItem(text = { Text(type.typeName) }, onClick = { onLeaveTypeSelected(type); expanded = false })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.startDateMillis?.let(dateFormat::format).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Start Date") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { showDatePicker(onStartDateSelected) }) { Text("Pick") } }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.endDateMillis?.let(dateFormat::format).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("End Date") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { showDatePicker(onEndDateSelected) }) { Text("Pick") } }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = uiState.reason, onValueChange = onReasonChanged, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        uiState.errorMessage?.let { Text(it, color = Color.Red) }
        Text("Duration: ${uiState.totalDuration} days")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) { Text("Submit Application") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun LeaveRecordsTabScreen(
    uiState: EmployeeUiState,
    onTabSelected: (LeaveRecordsTab) -> Unit,
    onWithdrawClick: (LeaveApplicationEntity) -> Unit,
    onBack: () -> Unit,
) {
    val tabs = listOf(LeaveRecordsTab.ALL, LeaveRecordsTab.APPROVED, LeaveRecordsTab.PENDING, LeaveRecordsTab.REJECTED)
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        ScrollableTabRow(selectedTabIndex = tabs.indexOf(uiState.formState.selectedTab)) {
            tabs.forEach { tab ->
                Tab(selected = uiState.formState.selectedTab == tab, onClick = { onTabSelected(tab) }, text = { Text(tab.name) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(uiState.filteredRecords) { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(record.reason, fontWeight = FontWeight.Bold)
                        Text(record.status.name)
                        if (record.status == com.example.mad.data.ApplicationStatus.PENDING) {
                            TextButton(onClick = { onWithdrawClick(record) }) { Text("Withdraw", color = Color.Red) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun ManagerDashboardScreen(
    uiState: ManagerUiState,
    onRefresh: () -> Unit,
    onSelectApplication: (Int) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Manager Dashboard", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRefresh) { Text("Refresh") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(uiState.pendingApplications) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.reason, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSelectApplication(item.applicationId); onReject() }) { Text("X Reject") }
                            Button(onClick = { onSelectApplication(item.applicationId); onApprove() }) { Text("✓ Approve") }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onLogout) { Text("Log out") }
    }
}

@Composable
fun RejectionModalDialog(
    rejectionReason: String,
    errorMessage: String?,
    onReasonChange: (String) -> Unit,
    onConfirmReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text("Reject Request", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = rejectionReason, onValueChange = onReasonChange, label = { Text("Rejection Reason") }, modifier = Modifier.fillMaxWidth())
        errorMessage?.let { Text(it, color = Color.Red) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = onConfirmReject) { Text("Confirm") }
        }
    }
}

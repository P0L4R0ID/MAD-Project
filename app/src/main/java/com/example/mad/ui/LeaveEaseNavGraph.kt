package com.example.mad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mad.data.AuthRoute
import com.example.mad.data.AuthViewModel
import com.example.mad.data.EmployeeViewModel
import com.example.mad.data.ManagerViewModel
import com.example.mad.data.UserRole

object LeaveEaseRoutes {
    const val LOGIN = "login"
    const val FORGOT_PASSWORD = "forgot_password"
    const val EMPLOYEE_DASHBOARD = "employee_dashboard"
    const val LEAVE_APPLICATION_FORM = "leave_application_form"
    const val LEAVE_RECORDS = "leave_records"
    const val MANAGER_DASHBOARD = "manager_dashboard"
    const val REJECTION_DIALOG = "rejection_dialog"
}

@Composable
fun LeaveEaseNavGraph(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(),
    employeeViewModel: EmployeeViewModel = viewModel(),
    managerViewModel: ManagerViewModel = viewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val employeeState by employeeViewModel.uiState.collectAsState()
    val managerState by managerViewModel.uiState.collectAsState()

    LaunchedEffect(authState.route, authState.loggedInUser) {
        val user = authState.loggedInUser ?: return@LaunchedEffect
        when (authState.route) {
            AuthRoute.EmployeeHome -> navController.navigate(LeaveEaseRoutes.EMPLOYEE_DASHBOARD) {
                popUpTo(LeaveEaseRoutes.LOGIN) { inclusive = true }
                launchSingleTop = true
            }
            AuthRoute.ManagerHome -> navController.navigate(LeaveEaseRoutes.MANAGER_DASHBOARD) {
                popUpTo(LeaveEaseRoutes.LOGIN) { inclusive = true }
                launchSingleTop = true
            }
            AuthRoute.Login -> Unit
        }
        if (user.role == UserRole.EMPLOYEE) {
            employeeViewModel.loadEmployee(user.userId)
        } else {
            managerViewModel.loadPendingApprovals()
        }
    }

    NavHost(
        navController = navController,
        startDestination = LeaveEaseRoutes.LOGIN,
    ) {
        composable(LeaveEaseRoutes.LOGIN) {
            LoginScreen(
                uiState = authState,
                onUsernameChange = authViewModel::onUsernameChange,
                onPasswordChange = authViewModel::onPasswordChange,
                onLoginClick = authViewModel::login,
                onForgotPasswordClick = {
                    authViewModel.requestForgotPassword()
                    navController.navigate(LeaveEaseRoutes.FORGOT_PASSWORD)
                },
            )
        }

        composable(LeaveEaseRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                isSubmitted = authState.forgotPasswordRequested,
                onSubmitRequest = authViewModel::requestForgotPassword,
                onBack = { navController.popBackStack() },
            )
        }

        composable(LeaveEaseRoutes.EMPLOYEE_DASHBOARD) {
            EmployeeDashboardScreen(
                uiState = employeeState,
                onApplyClick = { navController.navigate(LeaveEaseRoutes.LEAVE_APPLICATION_FORM) },
                onRecordsClick = { navController.navigate(LeaveEaseRoutes.LEAVE_RECORDS) },
                onLogoutClick = {
                    authViewModel.resetNavigation()
                    navController.navigate(LeaveEaseRoutes.LOGIN) {
                        popUpTo(0)
                    }
                },
            )
        }

        composable(LeaveEaseRoutes.LEAVE_APPLICATION_FORM) {
            LeaveApplicationFormScreen(
                uiState = employeeState.formState,
                leaveTypes = employeeState.leaveTypes,
                onLeaveTypeSelected = employeeViewModel::onLeaveTypeSelected,
                onStartDateSelected = employeeViewModel::onStartDateSelected,
                onEndDateSelected = employeeViewModel::onEndDateSelected,
                onReasonChanged = employeeViewModel::onReasonChanged,
                onAttachmentSelected = employeeViewModel::onAttachmentSelected,
                onSubmit = employeeViewModel::submitApplication,
                onBack = { navController.popBackStack() },
            )
        }

        composable(LeaveEaseRoutes.LEAVE_RECORDS) {
            LeaveRecordsTabScreen(
                uiState = employeeState,
                onTabSelected = employeeViewModel::onTabSelected,
                onWithdrawClick = { application -> employeeViewModel.withdrawApplication(application.applicationId) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(LeaveEaseRoutes.MANAGER_DASHBOARD) {
            ManagerDashboardScreen(
                uiState = managerState,
                onRefresh = managerViewModel::loadPendingApprovals,
                onSelectApplication = managerViewModel::selectApplication,
                onApprove = { managerViewModel.approveSelected(managerId = 1) },
                onReject = { navController.navigate(LeaveEaseRoutes.REJECTION_DIALOG) },
                onLogout = {
                    authViewModel.resetNavigation()
                    navController.navigate(LeaveEaseRoutes.LOGIN) {
                        popUpTo(0)
                    }
                },
            )
        }

        composable(LeaveEaseRoutes.REJECTION_DIALOG) {
            RejectionModalDialog(
                rejectionReason = managerState.rejectReason,
                errorMessage = managerState.errorMessage,
                onReasonChange = managerViewModel::onRejectReasonChanged,
                onConfirmReject = { managerViewModel.rejectSelected(managerId = 1) },
                onDismiss = { navController.popBackStack() },
            )
        }
    }
}

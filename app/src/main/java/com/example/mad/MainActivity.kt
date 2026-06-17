package com.example.mad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mad.data.*
import com.example.mad.ui.theme.MADTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private val leaveTypes = listOf(
    LeaveTypeEntity(1, "Annual Leave"),
    LeaveTypeEntity(2, "Medical Leave"),
    LeaveTypeEntity(3, "Emergency Leave"),
    LeaveTypeEntity(4, "Compassionate Leave"),
    LeaveTypeEntity(5, "Replacement Leave")
)

private fun daysFromNow(days: Int): Long = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

private val seedApplications = listOf(
    LeaveApplicationEntity(1, 101, 1, daysFromNow(4), daysFromNow(7), 4.0, "Family vacation", status = ApplicationStatus.PENDING),
    LeaveApplicationEntity(2, 101, 2, daysFromNow(-10), daysFromNow(-8), 3.0, "Clinic follow-up", status = ApplicationStatus.APPROVED, managerId = 201, approvalDate = daysFromNow(-9)),
    LeaveApplicationEntity(3, 103, 3, daysFromNow(-2), daysFromNow(0), 3.0, "Insufficient documentation provided.", status = ApplicationStatus.REJECTED, managerId = 201, approvalDate = daysFromNow(-1), rejectReason = "Please attach formal clinic slip."),

    LeaveApplicationEntity(
        applicationId = 4,
        employeeId = 101,
        leaveTypeId = 1,
        startDate = daysFromNow(-40),
        endDate = daysFromNow(-35),
        totalDuration = 6.0,
        reason = "Holiday trip to Tokyo",
        status = ApplicationStatus.COMPLETED,
        managerId = 201,
        approvalDate = daysFromNow(-45)
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MADTheme { AppRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val db = remember { LeaveEaseDatabase.getDatabase(context) }
    val userDao = db.userDao()
    val leaveTypeDao = db.leaveTypeDao()
    val leaveApplicationDao = db.leaveApplicationDao()
    val scope = rememberCoroutineScope()

    val testAccounts = remember {
        listOf(
            UserEntity(101, "sarah", "Sarah Jenkins", "123", "sarah@example.com", "0123456789", null, UserRole.EMPLOYEE, 15.0, System.currentTimeMillis()),
            UserEntity(201, "david", "David Chen", "123", "david@example.com", "0987654321", null, UserRole.MANAGER, 15.0, System.currentTimeMillis())
        )
    }
    var currentUser by remember { mutableStateOf(testAccounts.first()) }
    var screen by rememberSaveable { mutableStateOf("splash") }
    var activeRejectRecord by remember { mutableStateOf<LeaveApplicationEntity?>(null) }
    var activeDetailRecord by remember { mutableStateOf<LeaveApplicationEntity?>(null) }
    var detailBackRoute by rememberSaveable { mutableStateOf("records") }
    val recordsList by leaveApplicationDao.getAllApplicationsLive().collectAsState(initial = emptyList())
    var loginError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {

            if (userDao.getAllUsers().isEmpty()) {
                userDao.insertUsers(testAccounts)
                if (leaveTypeDao.getAllLeaveTypes().isEmpty()) {
                    leaveTypeDao.insertLeaveTypes(leaveTypes)
                }
            }
            if (leaveApplicationDao.getAllApplicationsNewestFirst().isEmpty()) {
                try { leaveApplicationDao.insertApplications(seedApplications) } catch (_: Exception) {}
            }

            leaveApplicationDao.autoCompletePastLeaves(System.currentTimeMillis())
        }
    }

    val showBottomBar = screen != "splash" && screen != "login" && screen != "recovery" && screen != "recoverySuccess"
    val bottomItems = remember(currentUser.role) {
        if (currentUser.role == UserRole.MANAGER) {
            listOf(
                BottomNavItem("Dashboard", "dashboard", Icons.Filled.Home),
                BottomNavItem("Approval", "approvals", Icons.Filled.Check),
                BottomNavItem("History", "records", Icons.Filled.List),
                BottomNavItem("Apply", "apply", Icons.Filled.Add),
                BottomNavItem("Profile", "edit", Icons.Filled.Person),
            )
        } else {
            listOf(
                BottomNavItem("Dashboard", "dashboard", Icons.Filled.Home),
                BottomNavItem("My Records", "records", Icons.Filled.List),
                BottomNavItem("Apply", "apply", Icons.Filled.Add),
                BottomNavItem("Profile", "edit", Icons.Filled.Person),
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showBottomBar) {
                LeaveEaseTopBar(currentUser = currentUser)
            }
        },
        bottomBar = {
            if (showBottomBar) NavigationBar { bottomItems.forEach { item -> NavigationBarItem(selected = screen == item.route, onClick = { screen = item.route }, icon = { Icon(item.icon, contentDescription = item.label) }, label = { Text(item.label) }, alwaysShowLabel = true) } }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                "splash" -> SplashScreen(
                    onTimeout = { screen = "login" }
                )

                "login" -> LoginScreen(
                    errorMessage = loginError,
                    onLogin = { inputUser, inputPass ->
                        scope.launch {
                            val user =
                                withContext(Dispatchers.IO) { userDao.getUserByUserName(inputUser) }; if (user != null && user.password == inputPass) {
                            currentUser = user; loginError = null; screen = "dashboard"
                        } else {
                            loginError = "Invalid username or password"
                        }
                        }
                    },
                    onForgotPassword = { screen = "recovery" })

                "recovery" -> PasswordRecoveryScreen(
                    onBack = { screen = "login" },
                    onSuccess = { screen = "recoverySuccess" }
                )
                "recoverySuccess" -> RecoverySuccessScreen(
                    onReturn = { screen = "login" }
                )
                "dashboard" -> EmployeeProfileScreen(
                    userName = currentUser.fullName,
                    userId = currentUser.userId,
                    basePto = currentUser.ptoBalance,
                    records = recordsList,
                    onApply = { screen = "apply" },
                    onEdit = { screen = "edit" },
                    onRecords = { screen = "records" }
                )
                "edit" -> EditProfileScreen(
                    currentUser = currentUser,
                    onSave = { updatedName, updatedPhone, updatedPhotoPath ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val existing = userDao.getUserById(currentUser.userId) ?: return@withContext
                                userDao.updateUser(existing.copy(fullName = updatedName, phoneNumber = updatedPhone, profilePicture = updatedPhotoPath))
                            }
                            currentUser = currentUser.copy(fullName = updatedName, phoneNumber = updatedPhone, profilePicture = updatedPhotoPath)
                        }
                    },
                    onLogout = { screen = "login" }
                )
                "apply" -> {
                    val usedPto = recordsList
                        .filter { it.employeeId == currentUser.userId && (it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.COMPLETED) }
                        .sumOf { it.totalDuration }
                    val currentBalance = currentUser.ptoBalance - usedPto

                    LeaveApplicationScreen(
                        availableBalance = currentBalance,
                        onSubmit = { leaveType, startMillis, endMillis, reasonText, attachedFile ->
                            val newId = (recordsList.maxOfOrNull { it.applicationId } ?: 0) + 1
                            val newRecord = LeaveApplicationEntity(
                                applicationId = newId,
                                employeeId = currentUser.userId,
                                leaveTypeId = leaveTypes.firstOrNull { it.typeName == leaveType }?.leaveTypeId ?: 1,
                                startDate = startMillis,
                                endDate = endMillis,
                                totalDuration = ((endMillis - startMillis) / 86_400_000L + 1).toDouble(),
                                reason = reasonText,
                                attachmentPath = attachedFile,
                                status = ApplicationStatus.PENDING
                            )
                            scope.launch {
                                withContext(Dispatchers.IO) { leaveApplicationDao.insertApplication(newRecord) }
                                screen = "applySuccess"
                            }
                        },
                        onBack = { screen = "dashboard" }
                    )
                }

                "applySuccess" -> SuccessDialogScreen(onDone = { screen = "records" })

                "records" -> {
                    val visibleRecords =
                        recordsList.filter { it.employeeId == currentUser.userId || currentUser.role == UserRole.MANAGER }
                    LeaveRecordsScreen(
                        currentUserName = currentUser.fullName,
                        currentUserRole = currentUser.role,
                        currentUserId = currentUser.userId,
                        allRecords = visibleRecords,
                        userDao = userDao,
                        onWithdraw = { recordToWithdraw ->
                            scope.launch {
                                withContext(Dispatchers.IO) { leaveApplicationDao.deleteApplication(recordToWithdraw.applicationId) }
                            }
                        },
                        onBack = { screen = "dashboard" },
                        onRecordClick = { record ->
                            activeDetailRecord = record
                            detailBackRoute = "records"
                            screen = "detail"
                        }
                    )
                }

                "detail" -> activeDetailRecord?.let { record ->
                    LeaveDetailScreen(
                        record = record,
                        userDao = userDao,
                        onBack = { screen = detailBackRoute }
                    )
                }

                "approvals" -> LeaveApprovalsScreen(
                    records = recordsList,
                    userDao = userDao,
                    onNavigateToReject = { record ->
                        activeRejectRecord = record; screen = "reject"
                    },
                    onApprove = { record ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                leaveApplicationDao.updateApplicationStatus(
                                    applicationId = record.applicationId,
                                    status = ApplicationStatus.APPROVED,
                                    managerId = currentUser.userId,
                                    actionDate = System.currentTimeMillis(),
                                    rejectReason = null
                                )
                            }
                            screen = "approveSuccess"
                        }
                    },
                    onRecordClick = { record ->
                        activeDetailRecord = record
                        detailBackRoute = "approvals"
                        screen = "detail"
                    },
                    onBack = { screen = "dashboard" }
                )

                "approveSuccess" -> SuccessDialogScreen(
                    title = "Success!",
                    message = "This application has been approved successfully.",
                    buttonText = "Back to Approval",
                    onDone = { screen = "approvals" }
                )

                "reject" -> activeRejectRecord?.let { record ->
                    var employeeName by remember(record.employeeId) { mutableStateOf("Loading...") }
                    LaunchedEffect(record.employeeId) {
                        userDao.getUserById(record.employeeId)?.let { employeeName = it.fullName }
                    }

                    RejectRequestScreen(
                        applicantName = employeeName,
                        onCancel = { screen = "approvals" },
                        onConfirm = { reasonString ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    leaveApplicationDao.updateApplicationStatus(
                                        record.applicationId,
                                        ApplicationStatus.REJECTED,
                                        currentUser.userId,
                                        System.currentTimeMillis(),
                                        reasonString
                                    )
                                }
                            }
                            activeRejectRecord = null
                            screen = "approvals"
                        }
                    )
                }
            }
        }
    }
}

private data class BottomNavItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.leaveease_logo),
            contentDescription = "LeaveEase Logo",
            modifier = Modifier.size(100.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "LeaveEase",
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            color = Color.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        )
    }
}

@Composable
fun LoginScreen(errorMessage: String?, onLogin: (String, String) -> Unit, onForgotPassword: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.leaveease_logo),
            contentDescription = "LeaveEase Logo",
            modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "LeaveEase",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = Color.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Welcome back. Please enter your details.",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(40.dp))
        Text(
            text = "Username",
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF333333)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            placeholder = { Text("Enter your username", color = Color.LightGray) },
            leadingIcon = {
                Icon(Icons.Filled.Person, contentDescription = "User", tint = Color.LightGray)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE0E0E0)
            )
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Password", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF333333))
            Text(
                text = "Forgot Password?",
                color = Color(0xFF1976D2),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onForgotPassword() }
            )
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("••••••••", color = Color.LightGray) },
            leadingIcon = {
                Icon(Icons.Filled.Lock, contentDescription = "Lock", tint = Color.LightGray)
            },
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "Hide" else "Show",
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable { passwordVisible = !passwordVisible }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            singleLine = true,
            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE0E0E0)
            )
        )

        Spacer(Modifier.height(8.dp))
        errorMessage?.let { Text(it, color = Color.Red, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { if (username.isNotBlank() && password.isNotBlank()) onLogin(username.trim(), password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF005EB8)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text("Login", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PasswordRecoveryScreen(onBack: () -> Unit, onSuccess: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var email by rememberSaveable { mutableStateOf("") }
    var employeeId by rememberSaveable { mutableStateOf("") }

    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F0))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Forgot password",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Color.Black
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = Color(0xFF3B82F6)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "If you have forgotten your password, please submit a request. The system administrator will reset your password and contact you.",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Text("Your Company's Email", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF333333))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Text("Employee ID", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF333333))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = employeeId,
            onValueChange = { employeeId = it },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            singleLine = true
        )

        Spacer(Modifier.height(32.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Button(
            onClick = {
                if (email.isBlank() || employeeId.isBlank()) {
                    errorMessage = "Please enter both your Email and Employee ID."
                } else {
                    errorMessage = null
                    onSuccess()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF005EB8)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Text("Submit Request", color = Color.White, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "For urgent access, please contact your HR or system administrator directly.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveEaseTopBar(currentUser: UserEntity) {
    androidx.compose.material3.TopAppBar(
        title = {
            Text(
                text = "LeaveEase",
                color = Color(0xFF0277BD),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                letterSpacing = 1.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
            )
        },
        actions = {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFFEAF2FF),
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(36.dp)
            ) {

                ProfileAvatar(user = currentUser, modifier = Modifier.fillMaxSize())
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White
        )
    )
}

@Composable
fun EmployeeProfileScreen(userName: String, userId: Int, basePto: Double, records: List<LeaveApplicationEntity>, onApply: () -> Unit, onEdit: () -> Unit, onRecords: () -> Unit) {
    val usedPto = records
        .filter { it.employeeId == userId && (it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.COMPLETED) }
        .sumOf { it.totalDuration }

    val balance = basePto - usedPto
    val progressRatio = if (basePto > 0) (balance / basePto).toFloat().coerceIn(0f, 1f) else 0f
    val upcomingLeaves = records
        .filter { it.employeeId == userId && it.status == ApplicationStatus.APPROVED && it.endDate >= System.currentTimeMillis() }
        .sortedBy { it.startDate }
        .take(3)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome back, $userName", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PTO Balance", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2B36))
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color(0xFFEAF2FF),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Calendar",
                            tint = Color(0xFF005EB8),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${balance.toInt()}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005EB8),
                        lineHeight = 48.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Days Available",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF005EB8),
                    trackColor = Color(0xFFE0E0E0)
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Used: ${usedPto.toInt()}", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                    Text("Total: ${basePto.toInt()}", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onApply() },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF2E8CE5), Color(0xFF005EB8))
                        )
                    )
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Apply",
                            tint = Color(0xFF005EB8),
                            modifier = Modifier.padding(8.dp).fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Apply for Leave",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Upcoming Leaves", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onRecords) { Text("View All") }
        }
        Spacer(Modifier.height(8.dp))

        if (upcomingLeaves.isNotEmpty()) {
            upcomingLeaves.forEach { leave ->
                val typeName = leaveTypes.firstOrNull { it.leaveTypeId == leave.leaveTypeId }?.typeName ?: "Leave"
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = leave.startDate }
                val monthStr = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(cal.time).uppercase()
                val dayStr = cal.get(java.util.Calendar.DAY_OF_MONTH).toString()

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onRecords() },
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = Color(0xFFF4F6F9)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(monthStr, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(dayStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2B36))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(typeName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1C2B36))
                            Spacer(Modifier.height(4.dp))
                            Text("${formatDate(leave.startDate)} - ${formatDate(leave.endDate)} (${leave.totalDuration.toInt()} Days)", color = Color.Gray, fontSize = 12.sp)
                        }
                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = Color(0xFF4CFF4C)) {
                            Text("Approved", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C2B36))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        } else {
            Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No upcoming approved leaves.", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun EditProfileScreen(currentUser: UserEntity, onSave: (String, String, String?) -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var fullName by remember(currentUser.fullName) { mutableStateOf(currentUser.fullName) }
    var phoneNumber by rememberSaveable { mutableStateOf(currentUser.phoneNumber) }
    var profilePicturePath by rememberSaveable { mutableStateOf(currentUser.profilePicture) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = java.io.File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg")
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            profilePicturePath = file.absolutePath
        }
    }

    if (isEditing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = Color(0xFFE4E6EB)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(80.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                    ) {
                        ProfileAvatar(user = currentUser.copy(profilePicture = profilePicturePath), modifier = Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "CHANGE PHOTO",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { launcher.launch("image/*") }
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("Personal Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF333333))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Update your account information and contact details.",
                        color = Color(0xFF555555),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Gray,
                    focusedBorderColor = Color(0xFF506489)
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Gray,
                    focusedBorderColor = Color(0xFF506489)
                )
            )

            Spacer(Modifier.height(32.dp))

            val slateBlueColor = Color(0xFF506489)

            Button(
                onClick = { if (fullName.isNotBlank()) onSave(fullName, phoneNumber, profilePicturePath); isEditing = false },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005EB8))
            ) {
                Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = { fullName = currentUser.fullName; phoneNumber = currentUser.phoneNumber; profilePicturePath = currentUser.profilePicture; isEditing = false },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) {
                Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F7FA))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(88.dp)) {
                        ProfileAvatar(user = currentUser, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(currentUser.fullName, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF1C2B36))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(Modifier.width(6.dp))
                        Text(currentUser.role.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color.Gray, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = Color(0xFFEAF2FF),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF005EB8), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Active", color = Color(0xFF005EB8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFF4F6F9), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Email Address", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(currentUser.email, color = Color(0xFF1C2B36), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFF4F6F9), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Employee ID", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("EE-${currentUser.userId}", color = Color(0xFF1C2B36), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFF4F6F9), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Phone Number", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(currentUser.phoneNumber, color = Color(0xFF1C2B36), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Button(
                onClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005EB8)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit Profile", fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD63B3B)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text("Log out", color = Color.White, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveApplicationScreen(
    availableBalance: Double,
    onSubmit: (leaveType: String, startMillis: Long, endMillis: Long, reason: String, attachmentPath: String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(leaveTypes.first().typeName) }
    var expanded by remember { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var attachmentName by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentPath by rememberSaveable { mutableStateOf<String?>(null) }

    val fileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val isPdf = mimeType == "application/pdf"
            attachmentName = if (isPdf) "Document.pdf" else "Image_Attachment.jpg"

            val inputStream = context.contentResolver.openInputStream(uri)
            val extension = if (isPdf) ".pdf" else ".jpg"
            val file = java.io.File(context.filesDir, "attachment_${System.currentTimeMillis()}$extension")
            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            attachmentPath = file.absolutePath
        }
    }

    val todayMillis = remember {
        Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val futureDatesOnly = remember {
        object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean { return utcTimeMillis >= todayMillis }
        }
    }

    val startPickerState = rememberDatePickerState(selectableDates = futureDatesOnly)
    val endPickerState = rememberDatePickerState(selectableDates = futureDatesOnly)
    val startText = start.toLongOrNull()?.let { formatDate(it) }.orEmpty()
    val endText = end.toLongOrNull()?.let { formatDate(it) }.orEmpty()

    val automatedDays = remember(start, end) {
        val sMillis = start.toLongOrNull()
        val eMillis = end.toLongOrNull()
        if (sMillis != null && eMillis != null && sMillis <= eMillis) {
            ((eMillis - sMillis) / 86_400_000L + 1).toInt()
        } else {
            0
        }
    }

    if (showStartPicker) { DatePickerDialog(onDismissRequest = { showStartPicker = false }, confirmButton = { TextButton(onClick = { startPickerState.selectedDateMillis?.let { start = it.toString() }; showStartPicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }) { DatePicker(state = startPickerState) } }
    if (showEndPicker) { DatePickerDialog(onDismissRequest = { showEndPicker = false }, confirmButton = { TextButton(onClick = { endPickerState.selectedDateMillis?.let { end = it.toString() }; showEndPicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }) { DatePicker(state = endPickerState) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7FA))
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))

        Text("Leave Application Form", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color(0xFF1C2B36))
        Spacer(Modifier.height(4.dp))
        Text("Available PTO Balance: ", color = Color.Gray, fontSize = 14.sp)
        Text("$availableBalance Days", color = Color(0xFF005EB8), fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Leave Type", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = selected,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        readOnly = true,
                        enabled = false,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = Color(0xFFE0E0E0),
                            disabledTrailingIconColor = Color.Gray
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dropdown") }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        leaveTypes.forEach { type ->
                            DropdownMenuItem(text = { Text(type.typeName) }, onClick = { selected = type.typeName; expanded = false })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Duration", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = startText,
                    onValueChange = {},
                    label = { Text("Start Date") },
                    modifier = Modifier.fillMaxWidth().clickable { showStartPicker = true },
                    readOnly = true,
                    enabled = false,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(disabledTextColor = Color.Black, disabledBorderColor = Color(0xFFE0E0E0), disabledLabelColor = Color.Gray),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar", tint = Color(0xFF005EB8)) }
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = endText,
                    onValueChange = {},
                    label = { Text("End Date") },
                    modifier = Modifier.fillMaxWidth().clickable { showEndPicker = true },
                    readOnly = true,
                    enabled = false,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(disabledTextColor = Color.Black, disabledBorderColor = Color(0xFFE0E0E0), disabledLabelColor = Color.Gray),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar", tint = Color(0xFF005EB8)) }
                )

                if (automatedDays > 0) {
                    Spacer(Modifier.height(16.dp))
                    val isExceeding = automatedDays > availableBalance
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isExceeding) Color(0xFFFFEBEE) else Color(0xFFEAF2FF),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = if (isExceeding) Color(0xFFD32F2F) else Color(0xFF005EB8)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Total Duration: $automatedDays Days",
                                fontWeight = FontWeight.Bold,
                                color = if (isExceeding) Color(0xFFD32F2F) else Color(0xFF005EB8)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Additional Details", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("Provide reason for leave...", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE0E0E0))
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { fileLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF005EB8)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF005EB8))
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Upload")
                    Spacer(Modifier.width(8.dp))
                    Text(attachmentName ?: "Attach Supporting Document (Optional)", fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        error?.let {
            Text(it, color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val s = start.toLongOrNull()
                val e = end.toLongOrNull()
                error = when {
                    s == null || e == null -> "Please select your leave dates."
                    s > e -> "Start date cannot be later than end date."
                    automatedDays > availableBalance -> "Cannot apply: Duration ($automatedDays days) exceeds your balance."
                    reason.isBlank() -> "Please provide a reason for your leave."
                    else -> null
                }
                if (error == null) onSubmit(selected, s!!, e!!, reason, attachmentPath)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005EB8)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
        ) {
            Text("Submit Application", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SuccessDialogScreen(
    title: String = "Success!",
    message: String = "This application has been submitted successfully.",
    buttonText: String = "Back to My Records",
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color(0xFF1C2B36)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = message,
            color = Color.Gray,
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF005EB8)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(buttonText, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LeaveRecordsScreen(
    currentUserName: String,
    currentUserRole: UserRole,
    currentUserId: Int,
    allRecords: List<LeaveApplicationEntity>,
    userDao: UserDao,
    onWithdraw: (LeaveApplicationEntity) -> Unit,
    onBack: () -> Unit,
    onRecordClick: (LeaveApplicationEntity) -> Unit
) {
    var parentTab by rememberSaveable { mutableIntStateOf(if (currentUserRole == UserRole.MANAGER) 0 else 1) }
    var myRecordsTab by rememberSaveable { mutableIntStateOf(0) }
    var approvalTab by rememberSaveable { mutableIntStateOf(0) }

    val myRecords = allRecords.filter { it.employeeId == currentUserId }
    val approvalHistory = allRecords.filter { it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.REJECTED }
    val visibleParentTab = if (currentUserRole == UserRole.MANAGER) parentTab else 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp)
    ) {
        if (currentUserRole == UserRole.MANAGER) {
            androidx.compose.material3.TabRow(selectedTabIndex = parentTab, containerColor = Color.Transparent) {
                Tab(selected = parentTab == 0, onClick = { parentTab = 0 }, text = { Text("Approval History") })
                Tab(selected = parentTab == 1, onClick = { parentTab = 1 }, text = { Text("My Records") })
            }
            Spacer(Modifier.height(16.dp))
        }

        if (visibleParentTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All Decisions", "Approved", "Rejected")
                filters.forEachIndexed { index, title ->
                    val isSelected = approvalTab == index

                    val activeColor = when (index) {
                        1 -> Color(0xFF2E7D32)
                        2 -> Color(0xFFD32F2F)
                        else -> Color(0xFF005EB8)
                    }

                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        color = if (isSelected) activeColor else Color.White,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .clickable { approvalTab = index }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            if (isSelected) {
                                val icon = if (index == 2) Icons.Default.Close else Icons.Default.Check
                                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color.DarkGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            val filtered = when (approvalTab) {
                1 -> approvalHistory.filter { it.status == ApplicationStatus.APPROVED }
                2 -> approvalHistory.filter { it.status == ApplicationStatus.REJECTED }
                else -> approvalHistory
            }.sortedByDescending { it.startDate }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered) { record ->
                    ApprovalHistoryCard(record = record, userDao = userDao, onRecordClick = onRecordClick)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

        } else {
            Text("My Records", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF1C2B36))
            Spacer(Modifier.height(12.dp))

            val tabTitles = listOf("All", "Approved", "Pending", "Rejected")
            androidx.compose.material3.TabRow(selectedTabIndex = myRecordsTab, containerColor = Color.Transparent, contentColor = Color(0xFF1976D2)) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(selected = myRecordsTab == index, onClick = { myRecordsTab = index }, text = { Text(title, fontWeight = if (myRecordsTab == index) FontWeight.Bold else FontWeight.Normal, color = if (myRecordsTab == index) Color(0xFF1976D2) else Color.Gray) })
                }
            }
            Spacer(Modifier.height(16.dp))

            val filtered = when (myRecordsTab) { 1 -> myRecords.filter { it.status == ApplicationStatus.APPROVED }; 2 -> myRecords.filter { it.status == ApplicationStatus.PENDING }; 3 -> myRecords.filter { it.status == ApplicationStatus.REJECTED }; else -> myRecords }.sortedByDescending { it.startDate }
            val recentApplications = filtered.filter { it.status == ApplicationStatus.PENDING }
            val pastApplications = filtered.filter { it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.REJECTED || it.status == ApplicationStatus.COMPLETED }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                if (recentApplications.isNotEmpty()) { item { Text("Recent Applications:", color = Color.DarkGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)) }; items(recentApplications) { record -> RecordCard(record = record, onWithdraw = onWithdraw, onRecordClick = onRecordClick) } }
                if (pastApplications.isNotEmpty()) { item { Text("Past Applications:", color = Color.DarkGray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)) }; items(pastApplications) { record -> RecordCard(record = record, onWithdraw = null, onRecordClick = onRecordClick) } }
                if (recentApplications.isEmpty() && pastApplications.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No records found.", color = Color.Gray) } } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun LeaveDetailScreen(record: LeaveApplicationEntity, userDao: UserDao, onBack: () -> Unit) {
    val context = LocalContext.current

    var employeeName by remember(record.employeeId) { mutableStateOf("Loading...") }
    LaunchedEffect(record.employeeId) {
        userDao.getUserById(record.employeeId)?.let { employeeName = it.fullName }
    }

    val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Leave"
    val statusColor = when(record.status) {
        ApplicationStatus.PENDING -> Color(0xFFE65100)
        ApplicationStatus.APPROVED -> Color(0xFF1B5E20)
        ApplicationStatus.REJECTED -> Color(0xFFB71C1C)
        ApplicationStatus.COMPLETED -> Color(0xFF8C8C8C)
    }
    val statusBg = when(record.status) {
        ApplicationStatus.PENDING -> Color(0xFFFFE0B2)
        ApplicationStatus.APPROVED -> Color(0xFFC8E6C9)
        ApplicationStatus.REJECTED -> Color(0xFFFFCDD2)
        ApplicationStatus.COMPLETED -> Color(0xFF8C8C8C)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Leave Application Detail", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.align(Alignment.CenterHorizontally), color = Color(0xFF1C2B36))
        Spacer(Modifier.height(24.dp))

        Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = statusBg) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                Text(record.status.name, color = statusColor, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(24.dp))

        Text("APPLICANT", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(employeeName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1C2B36))
        Text("Employee ID: EE-${record.employeeId}", color = Color(0xFF546E7A), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE0E0E0))
        Spacer(Modifier.height(16.dp))

        Text("LEAVE TYPE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(typeName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1C2B36))
        Spacer(Modifier.height(16.dp))

        Text("${formatDate(record.startDate)} - ${formatDate(record.endDate)}", color = Color(0xFF1C2B36), fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            Text("Leave Period: ", fontWeight = FontWeight.Bold, color = Color(0xFF1C2B36))
            Text("${record.totalDuration.toInt()} Days", color = Color(0xFF546E7A))
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE0E0E0))
        Spacer(Modifier.height(16.dp))

        Text("REASON", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(record.reason, color = Color(0xFF546E7A), lineHeight = 20.sp)

        Spacer(Modifier.height(24.dp))

        if (record.attachmentPath != null) {
            val fileName = java.io.File(record.attachmentPath).name

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("ATTACHED FILE:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("📎", fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = fileName,
                        fontSize = 14.sp,
                        color = Color(0xFF1C2B36),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        try {
                            val file = java.io.File(record.attachmentPath)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                val mimeType = if (file.name.endsWith(".pdf", true)) "application/pdf" else "image/*"
                                setDataAndType(uri, mimeType)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Cannot open file.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("View")
                }
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider(color = Color(0xFFE0E0E0))
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                try {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    val titlePaint = android.graphics.Paint().apply {
                        textSize = 24f
                        isFakeBoldText = true
                        color = android.graphics.Color.BLACK
                    }
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 16f
                        color = android.graphics.Color.DKGRAY
                    }

                    canvas.drawText("LeaveEase Application Report", 50f, 80f, titlePaint)
                    canvas.drawText("Applicant: $employeeName (EE-${record.employeeId})", 50f, 130f, textPaint)
                    canvas.drawText("Reference ID: #${record.applicationId}", 50f, 170f, textPaint)
                    canvas.drawText("Leave Type: $typeName", 50f, 210f, textPaint)
                    canvas.drawText("Status: ${record.status.name}", 50f, 250f, textPaint)
                    canvas.drawText("Duration: ${record.totalDuration.toInt()} Days", 50f, 290f, textPaint)
                    canvas.drawText("Reason: ${record.reason}", 50f, 330f, textPaint)

                    pdfDocument.finishPage(page)

                    val fileName = "Leave_Report_${record.applicationId}.pdf"

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                pdfDocument.writeTo(outputStream)
                            }
                        }
                    } else {
                        val targetDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val file = java.io.File(targetDir, fileName)
                        file.outputStream().use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                    }

                    pdfDocument.close()
                    android.widget.Toast.makeText(context, "Saved to Downloads folder!", android.widget.Toast.LENGTH_LONG).show()

                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to generate PDF.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA1F2))
        ) {
            Text("Download as PDF  ↓", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.weight(1f))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
fun LeaveApprovalsScreen(
    records: List<LeaveApplicationEntity>,
    userDao: UserDao,
    onNavigateToReject: (LeaveApplicationEntity) -> Unit,
    onApprove: (LeaveApplicationEntity) -> Unit,
    onRecordClick: (LeaveApplicationEntity) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Text(
            text = "Pending Approvals",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = Color(0xFF1C2B36),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val pendingRecords = records.filter { it.status == ApplicationStatus.PENDING }

            if (pendingRecords.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No pending approvals.", color = Color.Gray)
                }
            } else {
                pendingRecords.forEach { record ->
                    var applicantName by remember(record.employeeId) { mutableStateOf("Loading...") }
                    LaunchedEffect(record.employeeId) {
                        userDao.getUserById(record.employeeId)?.let { applicantName = it.fullName }
                    }
                    val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Unknown Leave"

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable{ onRecordClick(record) },
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF9FAFC)),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(applicantName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1C2B36))
                                    Text("Employee ID: ${record.employeeId}", color = Color.Gray, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFFFF3E0),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("⏳", fontSize = 12.sp)
                                        Text("Pending", color = Color(0xFFE65100), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Card(
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF3F5F7)),
                                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Type", color = Color.Gray, fontSize = 13.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(typeName, fontWeight = FontWeight.Bold, color = Color(0xFF1C2B36))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Duration", color = Color.Gray, fontSize = 13.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text("${formatDate(record.startDate)} - ${formatDate(record.endDate)}", fontWeight = FontWeight.Bold, color = Color(0xFF1C2B36))
                                        Text("(${record.totalDuration.toInt()} Days)", fontWeight = FontWeight.Bold, color = Color(0xFF1C2B36))
                                    }
                                }
                            }

                            Column {
                                Text("Reason", color = Color.Gray, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(record.reason, color = Color(0xFF333333), fontSize = 14.sp)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onNavigateToReject(record) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC62828)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text("✕ Reject", color = Color(0xFFC62828), fontWeight = FontWeight.Medium)
                                }
                                Button(
                                    onClick = { onApprove(record) },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Text("✓ Approve", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text("Back to Dashboard", color = Color.DarkGray, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RejectRequestScreen(
    applicantName: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var isError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(24.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Reject",
                tint = Color(0xFFC62828),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Reject Request",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1C2B36)
            )
        }

        Spacer(Modifier.height(16.dp))

        val subtitle = androidx.compose.ui.text.buildAnnotatedString {
            append("You are about to reject the leave request for ")
            withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) {
                append(applicantName)
            }
            append(". Please provide a reason below.")
        }
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = Color.DarkGray,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Reason (Required)",
            fontSize = 14.sp,
            color = Color(0xFF546E7A)
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = reason,
            onValueChange = {
                reason = it
                if (it.isNotBlank()) isError = false
            },
            placeholder = { Text("E.g., Project deadline conflict...", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            isError = isError,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFFF4F6F9),
                focusedContainerColor = Color(0xFFF4F6F9),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedBorderColor = Color(0xFF005EB8)
            )
        )

        if (isError) {
            Text(
                text = "A reason is required to reject an application.",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.DarkGray)
            ) {
                Text("Cancel", fontSize = 15.sp)
            }

            Spacer(Modifier.width(16.dp))

            Button(
                onClick = {
                    if (reason.isBlank()) {
                        isError = true
                    } else {
                        onConfirm(reason)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Confirm Rejection", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun formatDate(timeInMillis: Long): String {
    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
    return "%04d-%02d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
}

@Composable
fun ProfileAvatar(user: UserEntity, modifier: Modifier = Modifier) {
    val fallbackRes = when (user.userName) {
        "sarah" -> R.drawable.sarah
        "david" -> R.drawable.david
        else -> android.R.drawable.ic_menu_camera
    }

    var bitmap by remember(user.profilePicture) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    LaunchedEffect(user.profilePicture) {
        if (user.profilePicture != null) {
            try {
                bitmap = android.graphics.BitmapFactory.decodeFile(user.profilePicture)?.let {
                    it.asImageBitmap()
                }
            } catch (e: Exception) {}
        } else {
            bitmap = null
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = "Profile Picture",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = fallbackRes),
            contentDescription = "Profile Picture",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@Composable
fun RecoverySuccessScreen(onReturn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFFD3E3FD)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = Color(0xFF005EB8),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Request Submitted",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.Black
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Your request has been submitted. Please contact your administrator or wait for further instructions.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onReturn,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF005EB8)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Text("Return to Login", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RecordCard(
    record: LeaveApplicationEntity,
    onWithdraw: ((LeaveApplicationEntity) -> Unit)?,
    onRecordClick: (LeaveApplicationEntity) -> Unit
) {
    val dateFormatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    val startStr = dateFormatter.format(java.util.Date(record.startDate))
    val endStr = dateFormatter.format(java.util.Date(record.endDate))
    val dateDisplay = if (startStr == endStr) startStr else "$startStr - $endStr"

    val (statusBg, statusText, statusLabel) = when(record.status) {
        ApplicationStatus.PENDING -> Triple(Color(0xFFFDECC8), Color(0xFFD97706), "Pending")
        ApplicationStatus.APPROVED -> Triple(Color(0xFFD0E4FF), Color(0xFF005EB8), "Approved")
        ApplicationStatus.REJECTED -> Triple(Color(0xFFFFCDD2), Color(0xFFC62828), "Rejected")
        ApplicationStatus.COMPLETED -> Triple(Color(0xFFF0F0F0), Color(0xFF616161), "Completed")
    }

    val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Leave Application"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onRecordClick(record) },
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(typeName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1C2B36))
                    Spacer(Modifier.height(4.dp))
                    Text(dateDisplay, color = Color.Gray, fontSize = 13.sp)
                }
                Surface(color = statusBg, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                    Text(
                        text = statusLabel,
                        color = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = "Duration", tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("${record.totalDuration.toInt()} Day${if (record.totalDuration > 1) "s" else ""}", color = Color.DarkGray, fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))

            Text(record.reason, color = Color(0xFF333333), fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

            if (record.status == ApplicationStatus.REJECTED && !record.rejectReason.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Rejected",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Manager's Note:", color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(record.rejectReason!!, color = Color(0xFFC62828), fontSize = 13.sp)
                        }
                    }
                }
            }

            if (record.status == ApplicationStatus.PENDING && onWithdraw != null) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { onWithdraw(record) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Withdraw", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Withdraw", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}


@Composable
fun ApprovalHistoryCard(
    record: LeaveApplicationEntity,
    userDao: UserDao,
    onRecordClick: (LeaveApplicationEntity) -> Unit
) {
    var employeeName by remember(record.employeeId) { mutableStateOf("Loading...") }
    LaunchedEffect(record.employeeId) {
        userDao.getUserById(record.employeeId)?.let { employeeName = it.fullName }
    }

    val isApproved = record.status == ApplicationStatus.APPROVED
    val accentColor = if (isApproved) Color(0xFF00C853) else Color(0xFFD50000)
    val statusText = if (isApproved) "Approved" else "Rejected"
    val statusBg = if (isApproved) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val statusContent = if (isApproved) Color(0xFF2E7D32) else Color(0xFFC62828)

    val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Leave"
    val dateDisplay = "${formatDate(record.startDate)} - ${formatDate(record.endDate)} (${record.totalDuration.toInt()} Days)"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onRecordClick(record) },
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor))

            Column(Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(employeeName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1C2B36))
                        Text("Employee ID: ${record.employeeId}", color = Color.Gray, fontSize = 13.sp)
                    }
                    Surface(color = statusBg, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isApproved) Icons.Default.Check else Icons.Default.Close, contentDescription = null, tint = statusContent, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(statusText, color = statusContent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Surface(color = Color(0xFFF4F6F9), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(typeName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1C2B36))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(dateDisplay, color = Color.DarkGray, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp))

                        if (!isApproved && !record.rejectReason.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("\"${record.rejectReason}\"", color = Color(0xFFD32F2F), fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Decision: ${record.approvalDate?.let { formatDate(it) } ?: "Recently"}", color = Color.Gray, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF005EB8), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
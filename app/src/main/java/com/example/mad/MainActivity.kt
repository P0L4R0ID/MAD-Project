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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    LeaveApplicationEntity(3, 103, 3, daysFromNow(-2), daysFromNow(0), 3.0, "Insufficient documentation provided.", status = ApplicationStatus.REJECTED, managerId = 201, approvalDate = daysFromNow(-1), rejectReason = "Please attach formal clinic slip.")
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
    var screen by rememberSaveable { mutableStateOf("login") }
    var activeRejectRecord by remember { mutableStateOf<LeaveApplicationEntity?>(null) }
    var activeDetailRecord by remember { mutableStateOf<LeaveApplicationEntity?>(null) }
    val recordsList by leaveApplicationDao.getAllApplicationsLive().collectAsState(initial = emptyList())
    var loginError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 1. Your existing initialization code
            if (userDao.getAllUsers().isEmpty()) {
                userDao.insertUsers(testAccounts)
                if (leaveTypeDao.getAllLeaveTypes().isEmpty()) {
                    leaveTypeDao.insertLeaveTypes(leaveTypes)
                }
            }
            if (leaveApplicationDao.getAllApplicationsNewestFirst().isEmpty()) {
                try { leaveApplicationDao.insertApplications(seedApplications) } catch (_: Exception) {}
            }

            // 2. ADD THIS NEW LINE: Run the automated status sweep!
            leaveApplicationDao.autoCompletePastLeaves(System.currentTimeMillis())
        }
    }

    val showBottomBar = screen != "login" && screen != "recovery" && screen != "recoverySuccess"
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
            // This ensures it ONLY shows up after logging in!
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
                    onSuccess = { screen = "recoverySuccess" } // Goes to our new screen!
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
                                // Now it safely edits the row without triggering the CASCADE delete!
                                userDao.updateUser(existing.copy(fullName = updatedName, phoneNumber = updatedPhone, profilePicture = updatedPhotoPath))
                            }
                            currentUser = currentUser.copy(fullName = updatedName, phoneNumber = updatedPhone, profilePicture = updatedPhotoPath)
                        }
                    },
                    onLogout = { screen = "login" }
                )
                "apply" -> {
                    // Calculate the user's current exact balance
                    val usedPto = recordsList
                        .filter { it.employeeId == currentUser.userId && (it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.COMPLETED) }
                        .sumOf { it.totalDuration }
                    val currentBalance = currentUser.ptoBalance - usedPto

                    LeaveApplicationScreen(
                        availableBalance = currentBalance,
                        // Notice the new 'attachedFile' parameter here!
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
                                attachmentPath = attachedFile, // <-- Save the file path to the database!
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

                "applySuccess" -> SuccessDialogScreen(
                    message = "Success! Application delivered.",
                    onDone = { screen = "records" })

                "records" -> {
                    val visibleRecords =
                        recordsList.filter { it.employeeId == currentUser.userId || currentUser.role == UserRole.MANAGER }
                    LeaveRecordsScreen(
                        currentUserName = currentUser.fullName,
                        currentUserRole = currentUser.role,
                        currentUserId = currentUser.userId,
                        allRecords = visibleRecords,
                        onWithdraw = { recordToWithdraw ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    leaveApplicationDao.deleteApplication(
                                        recordToWithdraw.applicationId
                                    )
                                }
                                // Removed the recordsList.remove() - Flow handles it!
                            }
                        },
                        onBack = { screen = "dashboard" },
                        onRecordClick = { record ->
                            activeDetailRecord = record
                            screen = "detail"
                        }
                    )
                }

                "detail" -> activeDetailRecord?.let { record ->
                    LeaveDetailScreen(
                        record = record,
                        userDao = userDao, // <-- Add this new line!
                        onBack = { screen = "records" }
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
                            // Removed recordsList.clear() and addAll() - Flow handles it!
                        }
                    },
                    onBack = { screen = "dashboard" }
                )

                "reject" -> RejectRequestScreen(
                    onCancel = { screen = "approvals" },
                    onSubmit = { reasonString ->
                        activeRejectRecord?.let { record ->
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
                                // Removed recordsList.clear() and addAll() - Flow handles it!
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

private data class BottomNavItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun LoginScreen(errorMessage: String?, onLogin: (String, String) -> Unit, onForgotPassword: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // NEW: We need a state to remember if the password should be shown as text or dots
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // --- 1. Your Custom Logo ---
        // Change 'your_logo_name' to the actual name of your image in the res > drawable folder!
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.leaveease_logo),
            contentDescription = "LeaveEase Logo",
            modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(16.dp))

        // --- 2. Title & Subtitle ---
        Text(
            text = "LeaveEase",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = Color.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif // Gives it that classic look from your mockup
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Welcome back. Please enter your details.",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(40.dp))

        // --- 3. Username Field (External Label) ---
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
                unfocusedBorderColor = Color(0xFFE0E0E0) // Gives it that soft, light border when not clicked
            )
        )

        Spacer(Modifier.height(20.dp))

        // --- 4. Password Row (Label + Forgot Password Link) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Password", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF333333))
            Text(
                text = "Forgot Password?",
                color = Color(0xFF1976D2), // Blue link
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onForgotPassword() }
            )
        }
        Spacer(Modifier.height(8.dp))

        // --- 5. Password Field ---
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("••••••••", color = Color.LightGray) },
            leadingIcon = {
                Icon(Icons.Filled.Lock, contentDescription = "Lock", tint = Color.LightGray)
            },
            trailingIcon = {
                // A clean, crash-proof text button to toggle the password visibility
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

        // --- 6. The Login Button ---
        Button(
            onClick = { if (username.isNotBlank() && password.isNotBlank()) onLogin(username.trim(), password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp), // Taller button to match mockup
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF005EB8) // Nice deep blue
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp) // Adds a tiny drop shadow
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

    // Variable to hold error messages for validation
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

        // Show the error message if they leave fields blank
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
                // VALIDATION: Check if either box is completely empty
                if (email.isBlank() || employeeId.isBlank()) {
                    errorMessage = "Please enter both your Email and Employee ID."
                } else {
                    errorMessage = null // Clear error
                    onSuccess() // Navigate to the new success screen!
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
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
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
                // We drop our new helper right here!
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

    // Calculate the percentage for the progress bar (between 0.0 and 1.0)
    val progressRatio = if (basePto > 0) (balance / basePto).toFloat().coerceIn(0f, 1f) else 0f

    val upcomingLeaves = records
        .filter { it.employeeId == userId && it.status == ApplicationStatus.APPROVED && it.endDate >= System.currentTimeMillis() }
        .sortedBy { it.startDate }
        .take(3)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome back, $userName", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        // --- 1. NEW PTO BALANCE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                // Header Row with Icon
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

                // Big Number Row
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${balance.toInt()}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005EB8),
                        lineHeight = 48.sp // Keeps the giant text from pushing margins down
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Days Available",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 10.dp) // Aligns the text with the bottom of the numbers
                    )
                }
                Spacer(Modifier.height(16.dp))

                // The Progress Bar
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progressRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF005EB8), // The blue filled section
                    trackColor = Color(0xFFE0E0E0) // The gray empty track
                )
                Spacer(Modifier.height(8.dp))

                // Footer Stats
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

        // --- 2. NEW APPLY FOR LEAVE CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onApply() }, // Makes the whole card a button!
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The beautiful gradient background
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF2E8CE5), Color(0xFF005EB8))
                        )
                    )
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // White Circle Icon
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

        // --- 3. UPCOMING LEAVES (Unchanged) ---
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

    // THE GALLERY LAUNCHER: Opens the phone's gallery and copies the selected image to safe internal storage
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
            profilePicturePath = file.absolutePath // Save the path!
        }
    }

    if (isEditing) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                    // Show the Avatar!
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(80.dp)) {
                        // Pass a temporary UserEntity with the new photo path so it previews instantly
                        ProfileAvatar(user = currentUser.copy(profilePicture = profilePicturePath), modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(12.dp))

                    // Make the text clickable to launch the gallery
                    Text(
                        text = "CHANGE PHOTO",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.clickable { launcher.launch("image/*") }
                    )

                    Spacer(Modifier.height(16.dp))
                    Text("Personal Details", fontWeight = FontWeight.Bold)
                    Text("Update your account information and contact details.")
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            // Pass the new photo path into the Save button!
            Button(onClick = { if (fullName.isNotBlank()) onSave(fullName, phoneNumber, profilePicturePath); isEditing = false }, modifier = Modifier.fillMaxWidth()) { Text("Save Changes") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { fullName = currentUser.fullName; phoneNumber = currentUser.phoneNumber; profilePicturePath = currentUser.profilePicture; isEditing = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                    // Show the Avatar!
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(80.dp)) {
                        ProfileAvatar(user = currentUser, modifier = Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(currentUser.fullName, fontWeight = FontWeight.Bold)
                    Text(currentUser.role.name)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {}) { Text("Active") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Email Address"); Text(currentUser.email) } }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Employee ID"); Text("EE-${currentUser.userId}") } }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Phone Number"); Text(currentUser.phoneNumber) } }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { isEditing = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit Profile") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Log Out", color = Color.White) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveApplicationScreen(availableBalance: Double, onSubmit: (leaveType: String, startMillis: Long, endMillis: Long, reason: String, attachmentPath: String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(leaveTypes.first().typeName) }
    var expanded by remember { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // NEW: Variables to hold the selected file's info
    var attachmentName by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentPath by rememberSaveable { mutableStateOf<String?>(null) }

    // NEW: The Document Picker Launcher
    val fileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentName = "Document Attached" // A simple label for the UI

            // Securely copy the PDF/Image into the app's internal storage
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = java.io.File(context.filesDir, "attachment_${System.currentTimeMillis()}.pdf")
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
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Leave Application", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Available Balance: $availableBalance Days", color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        Box { OutlinedTextField(selected, {}, label = { Text("Leave Type") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { TextButton(onClick = { expanded = true }) { Text("▼") } }); DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { leaveTypes.forEach { type -> DropdownMenuItem(text = { Text(type.typeName) }, onClick = { selected = type.typeName; expanded = false }) } } }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = startText, onValueChange = {}, label = { Text("Start Date") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { TextButton(onClick = { showStartPicker = true }) { Text("Pick") } })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = endText, onValueChange = {}, label = { Text("End Date") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { TextButton(onClick = { showEndPicker = true }) { Text("Pick") } })

        if (automatedDays > 0) {
            val durationColor = if (automatedDays > availableBalance) Color.Red else Color(0xFF1976D2)
            Text("Total Duration: $automatedDays Days", fontWeight = FontWeight.Bold, color = durationColor)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(12.dp))

        // NEW: The File Upload UI block
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = attachmentName ?: "",
                onValueChange = {},
                placeholder = { Text("Pdf, Png, Jpg files (Optional)") },
                modifier = Modifier.weight(1f),
                readOnly = true
            )
            Button(
                onClick = { fileLauncher.launch("*/*") }, // "*/*" allows picking PDFs and Images
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)) // Green button
            ) {
                Text("Upload")
            }
        }
        Spacer(Modifier.height(12.dp))

        error?.let { Text(it, color = Color.Red) }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val s = start.toLongOrNull()
                val e = end.toLongOrNull()
                error = when {
                    s == null || e == null -> "Please enter valid dates."
                    s > e -> "Start date cannot be later than end date."
                    automatedDays > availableBalance -> "Cannot apply: Duration ($automatedDays days) exceeds your balance ($availableBalance days)."
                    reason.isBlank() -> "Reason is required."
                    else -> null
                }

                // Pass the attachmentPath into the submit function!
                if (error == null) onSubmit(selected, s!!, e!!, reason, attachmentPath)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Application")
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun SuccessDialogScreen(message: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Text(message, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { Text("Continue") } }
}

@Composable
fun LeaveRecordsScreen(currentUserName: String, currentUserRole: UserRole, currentUserId: Int, allRecords: List<LeaveApplicationEntity>, onWithdraw: (LeaveApplicationEntity) -> Unit, onBack: () -> Unit, onRecordClick: (LeaveApplicationEntity) -> Unit) {
    var parentTab by rememberSaveable { mutableIntStateOf(if (currentUserRole == UserRole.MANAGER) 0 else 1) }
    var myRecordsTab by rememberSaveable { mutableIntStateOf(0) }
    var approvalTab by rememberSaveable { mutableIntStateOf(0) }
    val myRecords = allRecords.filter { it.employeeId == currentUserId }
    val approvalHistory = allRecords.filter { it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.REJECTED }
    val visibleParentTab = if (currentUserRole == UserRole.MANAGER) parentTab else 1
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (currentUserRole == UserRole.MANAGER) {
            ScrollableTabRow(selectedTabIndex = parentTab) { Tab(selected = parentTab == 0, onClick = { parentTab = 0 }, text = { Text("Approval History") }); Tab(selected = parentTab == 1, onClick = { parentTab = 1 }, text = { Text("My Records") }) }
            Spacer(Modifier.height(12.dp))
        }
        when (visibleParentTab) {
            0 -> {
                ScrollableTabRow(selectedTabIndex = approvalTab) { listOf("All Decisions", "Approved", "Rejected").forEachIndexed { index, title -> Tab(selected = approvalTab == index, onClick = { approvalTab = index }, text = { Text(title) }) } }
                Spacer(Modifier.height(12.dp))
                val filtered = when (approvalTab) { 1 -> approvalHistory.filter { it.status == ApplicationStatus.APPROVED }; 2 -> approvalHistory.filter { it.status == ApplicationStatus.REJECTED }; else -> approvalHistory }.sortedByDescending { it.startDate }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(filtered) { record ->
                        val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Leave Application"
                        Card(Modifier.fillMaxWidth().clickable { onRecordClick(record) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(typeName, fontWeight = FontWeight.Bold)
                                Text("Reference ID: #${record.applicationId}", color = Color.Gray)
                                Text("${formatDate(record.startDate)} to ${formatDate(record.endDate)}")
                                Text(if (record.status == ApplicationStatus.APPROVED) "Approved" else "Rejected", fontWeight = FontWeight.Bold, color = if (record.status == ApplicationStatus.APPROVED) Color(0xFF2E7D32) else Color(0xFFC62828))
                                Text("Decision: ${record.approvalDate?.let { formatDate(it) } ?: "Processed"}", color = Color.Gray)
                                if (record.status == ApplicationStatus.REJECTED && !record.rejectReason.isNullOrBlank()) Text(record.rejectReason, color = Color.Red)
                            }
                        }
                    }
                }
            }
            else -> {
                ScrollableTabRow(selectedTabIndex = myRecordsTab) { listOf("All", "Approved", "Pending", "Rejected").forEachIndexed { index, title -> Tab(selected = myRecordsTab == index, onClick = { myRecordsTab = index }, text = { Text(title) }) } }
                Spacer(Modifier.height(12.dp))
                val filtered = when (myRecordsTab) { 1 -> myRecords.filter { it.status == ApplicationStatus.APPROVED }; 2 -> myRecords.filter { it.status == ApplicationStatus.PENDING }; 3 -> myRecords.filter { it.status == ApplicationStatus.REJECTED }; else -> myRecords }.sortedByDescending { it.startDate }
                val recentApplications = filtered.filter { it.status == ApplicationStatus.PENDING }
                val pastApplications = filtered.filter { it.status == ApplicationStatus.APPROVED || it.status == ApplicationStatus.REJECTED }
                Column(Modifier.fillMaxSize()) {
                    Text("Recent Applications", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(recentApplications) { record ->
                            val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Leave Application"
                            Card(Modifier.fillMaxWidth().clickable { onRecordClick(record) }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(typeName, fontWeight = FontWeight.Bold)
                                    Text("Reference ID: #${record.applicationId}", color = Color.Gray)
                                    Text("${formatDate(record.startDate)} to ${formatDate(record.endDate)}")
                                    Text("Reason: ${record.reason}")
                                    TextButton(onClick = { onWithdraw(record) }) { Text("Withdraw", color = Color.Red) }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)); Text("Past Applications", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
                        items(pastApplications) { record ->
                            val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Leave Application"
                            Card(Modifier.fillMaxWidth().clickable { onRecordClick(record) }) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(typeName, fontWeight = FontWeight.Bold)
                                    Text("Reference ID: #${record.applicationId}", color = Color.Gray)
                                    Text("${formatDate(record.startDate)} to ${formatDate(record.endDate)}")
                                    Text("Reason: ${record.reason}")
                                    Text(if (record.status == ApplicationStatus.APPROVED) "Approved" else "Rejected", fontWeight = FontWeight.Bold, color = if (record.status == ApplicationStatus.APPROVED) Color(0xFF2E7D32) else Color(0xFFC62828))
                                    if (record.status == ApplicationStatus.REJECTED && !record.rejectReason.isNullOrBlank()) Text(record.rejectReason, color = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun LeaveDetailScreen(record: LeaveApplicationEntity, userDao: UserDao, onBack: () -> Unit) {
    val context = LocalContext.current

    // --- NEW: Fetch the employee's name from the database ---
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

        // --- NEW: APPLICANT INFO UI ---
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

        // --- THE ATTACHMENT UI BLOCK ---
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

        // --- THE DOWNLOAD PDF BUTTON ---
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
                    // We added the employee name to the PDF download here too!
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
fun LeaveApprovalsScreen(records: List<LeaveApplicationEntity>, userDao: UserDao, onNavigateToReject: (LeaveApplicationEntity) -> Unit, onApprove: (LeaveApplicationEntity) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pending Approvals", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        records.filter { it.status == ApplicationStatus.PENDING }.forEach { record ->
            var applicantName by remember(record.employeeId) { mutableStateOf("Loading...") }
            LaunchedEffect(record.employeeId) {
                userDao.getUserById(record.employeeId)?.let { applicantName = it.fullName }
            }
            val typeName = leaveTypes.firstOrNull { it.leaveTypeId == record.leaveTypeId }?.typeName ?: "Unknown Leave"
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF9FAFC))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.padding(end = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color(0xFFEAF2FF)
                            ) {
                                Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("SJ", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(applicantName, fontWeight = FontWeight.Bold)
                            Text("Team Member", color = Color.Gray)
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("⏳", color = Color(0xFFEF6C00))
                                Text("Pending", color = Color(0xFFEF6C00), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFF3F5F7))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Type", color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                Text(typeName, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Duration", color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                Text("${formatDate(record.startDate)} - ${formatDate(record.endDate)} (${record.totalDuration.toInt()} Days)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column {
                        Text("Reason", color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Text(record.reason)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onNavigateToReject(record) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC62828))
                        ) {
                            Text("✕ Reject", color = Color(0xFFC62828))
                        }
                        Button(
                            onClick = { onApprove(record) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            Text("✓ Approve", color = Color.White)
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun RejectRequestScreen(onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text("Reject Request", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(reason, { reason = it }, label = { Text("Rejection Reason") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { if (reason.isNotBlank()) onSubmit(reason) }, modifier = Modifier.weight(1f)) { Text("Confirm") }
        }
    }
}

private fun formatDate(timeInMillis: Long): String {
    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
    return "%04d-%02d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
}

@Composable
fun ProfileAvatar(user: UserEntity, modifier: Modifier = Modifier) {
    // 1. Set the default fallback images
    val fallbackRes = when (user.userName) {
        "sarah" -> R.drawable.sarah // Change this if you named the file differently
        "david" -> R.drawable.david // Change this if you named the file differently
        else -> android.R.drawable.ic_menu_camera
    }

    // 2. Remember the custom image bitmap
    var bitmap by remember(user.profilePicture) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }

    // 3. Try to load the custom image from internal storage
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

    // 4. Draw either the custom image or the default one
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
        // --- 1. The Blue Checkmark Circle ---
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFFD3E3FD)), // Light blue background
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = Color(0xFF005EB8), // Deep blue checkmark
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- 2. Title ---
        Text(
            text = "Request Submitted",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.Black
        )

        Spacer(Modifier.height(16.dp))

        // --- 3. Subtitle ---
        Text(
            text = "Your request has been submitted. Please contact your administrator or wait for further instructions.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(40.dp))

        // --- 4. Return Button ---
        Button(
            onClick = onReturn,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF005EB8) // Deep blue pill button
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        ) {
            Text("Return to Login", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

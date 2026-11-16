package com.example.ui.navigation

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.repository.AlertRepository
import com.example.data.repository.EmergencyAiRepository
import com.example.ui.components.SosQuickRaiseSheet
import com.example.ui.screens.AlertDetailScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MySafetyScreen
import com.example.ui.viewmodel.AlertDetailViewModel
import com.example.ui.viewmodel.RoadSafeViewModel
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import com.example.util.authStateFlow
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

const val ROUTE_HOME = "home"
const val ROUTE_MY_SAFETY = "my_safety"
const val ROUTE_ALERT_DETAIL = "alert_detail/{alertId}"

fun alertDetailRoute(alertId: String): String = "alert_detail/$alertId"

@Composable
fun AppNavigation(
    pendingAlertId: String?,
    onConsumePendingAlertId: () -> Unit,
    auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainAuthenticatedApp(
                currentUserId = user.uid,
                initialUserName = user.displayName ?: user.email?.substringBefore("@") ?: "RoadSafe Driver",
                pendingAlertId = pendingAlertId,
                onConsumePendingAlertId = onConsumePendingAlertId,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAuthenticatedApp(
    currentUserId: String,
    initialUserName: String,
    pendingAlertId: String?,
    onConsumePendingAlertId: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    val roadSafeViewModel: RoadSafeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RoadSafeViewModel(
                    repository = AlertRepository(db),
                    currentUserId = currentUserId,
                    initialUserName = initialUserName,
                )
            }
        }
    )

    val activeAlertsState by roadSafeViewModel.activeAlerts.collectAsStateWithLifecycle()
    val myAlertsState by roadSafeViewModel.myAlerts.collectAsStateWithLifecycle()
    val userProfile by roadSafeViewModel.userProfile.collectAsStateWithLifecycle()
    val bannerAlert by roadSafeViewModel.latestBannerAlert.collectAsStateWithLifecycle()
    val userLocation by roadSafeViewModel.userLocation.collectAsStateWithLifecycle()
    val isSubmittingSos by roadSafeViewModel.isSubmittingSos.collectAsStateWithLifecycle()
    val actionMessage by roadSafeViewModel.actionMessage.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(LocationHelper.hasLocationPermission(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var showSosSheet by remember { mutableStateOf(false) }

    fun refreshLocation() {
        if (!LocationHelper.hasLocationPermission(context)) return
        coroutineScope.launch {
            isFetchingLocation = true
            locationError = null
            val res = LocationHelper.fetchCurrentLocation(context)
            isFetchingLocation = false
            res.fold(
                onSuccess = { roadSafeViewModel.updateUserLocation(it) },
                onFailure = { locationError = it.localizedMessage }
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            refreshLocation()
        } else {
            locationError = "Location permission denied. Enter road/landmark manually."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) NotificationHelper.ensureChannel(context)
    }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannel(context)
        if (LocationHelper.hasLocationPermission(context)) {
            refreshLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasNotificationPermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Emit local system notification when another user raises a new SOS alert
    LaunchedEffect(roadSafeViewModel) {
        roadSafeViewModel.incomingSosEvent.collect { newAlert ->
            NotificationHelper.showSosAlertNotification(context, newAlert)
        }
    }

    // Handle notification tap deep link
    LaunchedEffect(pendingAlertId) {
        if (!pendingAlertId.isNullOrBlank()) {
            navController.navigate(alertDetailRoute(pendingAlertId)) {
                launchSingleTop = true
            }
            onConsumePendingAlertId()
        }
    }

    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(actionMessage!!)
            roadSafeViewModel.clearActionMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelRoute = currentRoute == ROUTE_HOME || currentRoute == ROUTE_MY_SAFETY

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (isTopLevelRoute) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        navigationIcon = {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "RoadSafe SOS",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute && !useRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) {
                                        Icons.Filled.NotificationsActive
                                    } else {
                                        Icons.Outlined.NotificationsActive
                                    },
                                    contentDescription = stringResource(R.string.nav_live_alerts),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_live_alerts)) },
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_MY_SAFETY,
                            onClick = {
                                navController.navigate(ROUTE_MY_SAFETY) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_MY_SAFETY) {
                                        Icons.Filled.Person
                                    } else {
                                        Icons.Outlined.Person
                                    },
                                    contentDescription = stringResource(R.string.nav_my_safety),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_my_safety)) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useRail) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) {
                                        Icons.Filled.NotificationsActive
                                    } else {
                                        Icons.Outlined.NotificationsActive
                                    },
                                    contentDescription = stringResource(R.string.nav_live_alerts),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_live_alerts)) },
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_MY_SAFETY,
                            onClick = {
                                navController.navigate(ROUTE_MY_SAFETY) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_MY_SAFETY) {
                                        Icons.Filled.Person
                                    } else {
                                        Icons.Outlined.Person
                                    },
                                    contentDescription = stringResource(R.string.nav_my_safety),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_my_safety)) },
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            currentUserId = currentUserId,
                            activeAlertsState = activeAlertsState,
                            bannerAlert = bannerAlert,
                            userLocation = userLocation,
                            hasLocationPermission = hasLocationPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestLocationPermission = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenRaiseSos = {
                                if (LocationHelper.hasLocationPermission(context)) {
                                    refreshLocation()
                                }
                                showSosSheet = true
                            },
                            onAlertClick = { alertId ->
                                navController.navigate(alertDetailRoute(alertId)) {
                                    launchSingleTop = true
                                }
                            },
                            onDismissBanner = { roadSafeViewModel.dismissBannerAlert() },
                        )
                    }

                    composable(ROUTE_MY_SAFETY) {
                        MySafetyScreen(
                            initialName = initialUserName,
                            userProfile = userProfile,
                            myAlertsState = myAlertsState,
                            onSaveProfile = { name, blood, vehicle, notes ->
                                roadSafeViewModel.saveSafetyProfile(name, blood, vehicle, notes)
                            },
                            onAlertClick = { alertId ->
                                navController.navigate(alertDetailRoute(alertId)) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(
                        route = ROUTE_ALERT_DETAIL,
                        arguments = listOf(navArgument("alertId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val alertId = backStackEntry.arguments?.getString("alertId").orEmpty()
                        val responderName = userProfile?.displayName?.takeIf { it.isNotBlank() }
                            ?: initialUserName
                        val detailViewModel: AlertDetailViewModel = viewModel(
                            key = "${currentUserId}_$alertId",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY]) as Application
                                    val databaseId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(databaseId)
                                    AlertDetailViewModel(
                                        alertRepository = AlertRepository(db),
                                        aiRepository = EmergencyAiRepository(),
                                        alertId = alertId,
                                        currentUserId = currentUserId,
                                        responderDisplayName = responderName,
                                    )
                                }
                            }
                        )

                        AlertDetailScreen(
                            viewModel = detailViewModel,
                            userLocation = userLocation,
                            onBack = { navController.popBackStack() },
                            onRefreshUserLocation = { refreshLocation() },
                        )
                    }
                }
            }
        }
    }

    if (showSosSheet) {
        SosQuickRaiseSheet(
            capturedLocation = userLocation,
            isFetchingLocation = isFetchingLocation,
            locationError = locationError,
            isSubmitting = isSubmittingSos,
            onRefreshLocation = {
                if (LocationHelper.hasLocationPermission(context)) {
                    refreshLocation()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            },
            onDismiss = { showSosSheet = false },
            onSubmitSos = { category, severity, description, lat, lng, label ->
                roadSafeViewModel.raiseSosAlert(
                    category = category,
                    severity = severity,
                    description = description,
                    latitude = lat,
                    longitude = lng,
                    locationLabel = label,
                    onCreated = { createdAlertId ->
                        showSosSheet = false
                        navController.navigate(alertDetailRoute(createdAlertId)) {
                            launchSingleTop = true
                        }
                    },
                )
            },
        )
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AlertCategory
import com.example.data.model.AlertResponse
import com.example.data.model.AlertSeverity
import com.example.data.model.EmergencyGuidance
import com.example.data.model.SafetyAlert
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.BeaconAmber
import com.example.ui.theme.CrimsonEmergency
import com.example.ui.theme.ResponderEmerald
import com.example.ui.viewmodel.AiGuidanceState
import com.example.ui.viewmodel.AlertDetailViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.CapturedLocation
import com.example.util.LocationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    viewModel: AlertDetailViewModel,
    userLocation: CapturedLocation?,
    onBack: () -> Unit,
    onRefreshUserLocation: () -> Unit,
) {
    val context = LocalContext.current
    val alertState by viewModel.alertState.collectAsStateWithLifecycle()
    val myResponse by viewModel.myResponse.collectAsStateWithLifecycle()
    val respondersForMyAlert by viewModel.respondersForMyAlert.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val isUpdatingAction by viewModel.isUpdatingAction.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Dispatch") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val state = alertState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .widthIn(max = 700.dp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                is UiState.Success -> {
                    val alert = state.data
                    if (alert == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This alert is no longer available.")
                        }
                    } else {
                        val isOwner = alert.reporterId == viewModel.currentUserId
                        val isResponder = myResponse != null
                        val isActive = alert.status == "active"

                        LaunchedEffect(alert.id, isOwner, isResponder) {
                            if (isOwner || isResponder) {
                                viewModel.ensureAiGuidanceIfAuthorized(
                                    alert = alert,
                                    isResponderOrOwner = true,
                                    responderLocation = userLocation,
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (actionError != null) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = actionError!!,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(14.dp),
                                        )
                                    }
                                }
                            }

                            // Incident Header Card
                            item {
                                IncidentSummaryCard(
                                    alert = alert,
                                    isOwner = isOwner,
                                    userLocation = userLocation,
                                    onOpenDirections = {
                                        LocationHelper.openMapsDirections(
                                            context = context,
                                            lat = alert.latitude,
                                            lng = alert.longitude,
                                            label = alert.locationLabel,
                                        )
                                    },
                                )
                            }

                            // Action Controls Card
                            item {
                                ResponseActionsCard(
                                    alert = alert,
                                    isOwner = isOwner,
                                    isActive = isActive,
                                    myResponse = myResponse,
                                    respondersForMyAlert = respondersForMyAlert,
                                    isUpdatingAction = isUpdatingAction,
                                    onConfirmHelp = {
                                        onRefreshUserLocation()
                                        viewModel.respondAndFetchAiGuidance(alert, userLocation)
                                    },
                                    onMarkArrived = { viewModel.markArrived() },
                                    onResolveAlert = { viewModel.resolveAlert() },
                                    onLoadAiWithoutResponding = {
                                        viewModel.fetchAiGuidance(alert, userLocation, force = true)
                                    },
                                )
                            }

                            // Gemini AI Emergency Dispatch Card
                            item {
                                GeminiDispatchSection(
                                    alert = alert,
                                    aiState = aiState,
                                    onRetry = {
                                        viewModel.fetchAiGuidance(alert, userLocation, force = true)
                                    },
                                    onDialNumber = { number ->
                                        LocationHelper.dialPhoneNumber(context, number)
                                    },
                                    onOpenHospitalsMap = {
                                        LocationHelper.openNearbyHospitalsInMaps(
                                            context = context,
                                            lat = alert.latitude,
                                            lng = alert.longitude,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentSummaryCard(
    alert: SafetyAlert,
    isOwner: Boolean,
    userLocation: CapturedLocation?,
    onOpenDirections: () -> Unit,
) {
    val category = AlertCategory.fromWire(alert.category)
    val severity = AlertSeverity.fromWire(alert.severity)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = category.label,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "${if (isOwner) "You" else alert.reporterName} • ${formatRelativeTimestamp(alert.createdAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SeverityBadge(severity = severity)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = "Location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = alert.locationLabel,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        text = buildString {
                            append("GPS: ${"%.5f".format(alert.latitude)}, ${"%.5f".format(alert.longitude)}")
                            if (userLocation != null && (alert.latitude != 0.0 || alert.longitude != 0.0)) {
                                append(" • ")
                                append(
                                    LocationHelper.formatDistanceKm(
                                        userLocation.latitude,
                                        userLocation.longitude,
                                        alert.latitude,
                                        alert.longitude,
                                    )
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (alert.description.isNotBlank()) {
                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(status = alert.status, responderCount = alert.responderCount)
                if (alert.latitude != 0.0 || alert.longitude != 0.0) {
                    OutlinedButton(
                        onClick = onOpenDirections,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Directions,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Navigate")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseActionsCard(
    alert: SafetyAlert,
    isOwner: Boolean,
    isActive: Boolean,
    myResponse: AlertResponse?,
    respondersForMyAlert: List<AlertResponse>,
    isUpdatingAction: Boolean,
    onConfirmHelp: () -> Unit,
    onMarkArrived: () -> Unit,
    onResolveAlert: () -> Unit,
    onLoadAiWithoutResponding: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isOwner) {
                Text(
                    text = "Your Active SOS Control",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (respondersForMyAlert.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Helpers Responding:",
                            style = MaterialTheme.typography.labelLarge,
                            color = ResponderEmerald,
                        )
                        respondersForMyAlert.forEach { helper ->
                            Surface(
                                color = ResponderEmerald.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "${helper.responderName} — ${if (helper.status == "arrived") "Arrived on scene" else "On the way"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Nearby app users have been notified. Keep calm and follow the AI first-aid checklist below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isActive) {
                    Button(
                        onClick = onResolveAlert,
                        enabled = !isUpdatingAction,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResponderEmerald,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("resolve_sos_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.resolve_button_label),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (myResponse == null) {
                Text(
                    text = "Ready to Help?",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Confirming lets ${alert.reporterName} know help is coming and immediately runs Gemini AI to fetch nearest hospitals, ambulance numbers & first-aid steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onConfirmHelp,
                    enabled = !isUpdatingAction && isActive,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonEmergency,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_help_button"),
                ) {
                    if (isUpdatingAction) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.VolunteerActivism,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "I Can Help — Fetch Location & AI Plan",
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    onClick = onLoadAiWithoutResponding,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Preview AI First-Aid & Hospital Info")
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = ResponderEmerald,
                    )
                    Text(
                        text = if (myResponse.status == "arrived") {
                            "You are on scene — thank you for helping!"
                        } else {
                            "You are responding to this SOS alert"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = ResponderEmerald,
                    )
                }
                if (myResponse.status == "responding" && isActive) {
                    OutlinedButton(
                        onClick = onMarkArrived,
                        enabled = !isUpdatingAction,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mark_arrived_button"),
                    ) {
                        Text("Mark Arrived on Scene")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiDispatchSection(
    alert: SafetyAlert,
    aiState: AiGuidanceState,
    onRetry: () -> Unit,
    onDialNumber: (String) -> Unit,
    onOpenHospitalsMap: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "AI Emergency Dispatch",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                if (aiState is AiGuidanceState.Success || aiState is AiGuidanceState.Error) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh AI guidance",
                        )
                    }
                }
            }

            when (aiState) {
                is AiGuidanceState.Idle -> {
                    Text(
                        text = "Tap \"I Can Help\" above (or Preview AI Info) to fetch local ambulance numbers, nearest hospitals, and immediate first-aid steps for ${alert.locationLabel}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is AiGuidanceState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Analyzing location (${alert.locationLabel}) for nearest hospitals, ambulance numbers & first-aid steps...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is AiGuidanceState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Retry AI Lookup")
                            }
                        }
                    }
                }

                is AiGuidanceState.Success -> {
                    val guidance: EmergencyGuidance = aiState.guidance

                    Text(
                        text = "${guidance.regionDetected}: ${guidance.summary}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Emergency Numbers Grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tap to Call Emergency Services",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            EmergencyDialCard(
                                label = "Ambulance",
                                number = guidance.ambulanceNumber,
                                accent = CrimsonEmergency,
                                onClick = { onDialNumber(guidance.ambulanceNumber) },
                                modifier = Modifier.weight(1f),
                            )
                            EmergencyDialCard(
                                label = "Police",
                                number = guidance.policeNumber,
                                accent = MaterialTheme.colorScheme.secondary,
                                onClick = { onDialNumber(guidance.policeNumber) },
                                modifier = Modifier.weight(1f),
                            )
                            EmergencyDialCard(
                                label = "Road Help",
                                number = guidance.roadHelplineNumber,
                                accent = BeaconAmber,
                                onClick = { onDialNumber(guidance.roadHelplineNumber) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Nearest Hospitals
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Nearest Hospitals",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            OutlinedButton(
                                onClick = onOpenHospitalsMap,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalHospital,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in Maps")
                            }
                        }

                        guidance.nearestHospitals.forEach { hospital ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = hospital.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${hospital.estimatedDistance} • ${hospital.note}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // What To Do Until Help Arrives
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "What To Do Until Help Arrives",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        guidance.immediateActions.forEachIndexed { index, step ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(ResponderEmerald.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ResponderEmerald,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Critical Warnings
                    if (guidance.doNotDoWarnings.isNotEmpty()) {
                        Surface(
                            color = BeaconAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ReportProblem,
                                        contentDescription = "Important safety warning",
                                        tint = BeaconAmber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Critical Safety Warnings",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                guidance.doNotDoWarnings.forEach { warning ->
                                    Text(
                                        text = "• $warning",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyDialCard(
    label: String,
    number: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Call $label",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = number,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

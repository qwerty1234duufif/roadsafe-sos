package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AlertCategory
import com.example.data.model.AlertSeverity
import com.example.data.model.SafetyAlert
import com.example.ui.components.IncomingSosBanner
import com.example.ui.components.SeverityBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatRelativeTimestamp
import com.example.ui.theme.BeaconAmber
import com.example.ui.theme.CrimsonEmergency
import com.example.ui.theme.ResponderEmerald
import com.example.ui.viewmodel.UiState
import com.example.util.CapturedLocation
import com.example.util.LocationHelper

@Composable
fun HomeScreen(
    currentUserId: String,
    activeAlertsState: UiState<List<SafetyAlert>>,
    bannerAlert: SafetyAlert?,
    userLocation: CapturedLocation?,
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenRaiseSos: () -> Unit,
    onAlertClick: (String) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                IncomingSosBanner(
                    alert = bannerAlert,
                    onRespondClick = { alert ->
                        onDismissBanner()
                        onAlertClick(alert.id)
                    },
                    onDismiss = onDismissBanner,
                )
            }

            // Permission quick-enable bar when needed
            if (!hasLocationPermission || !hasNotificationPermission) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BeaconAmber.copy(alpha = 0.13f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (!hasNotificationPermission) Icons.Filled.NotificationsOff else Icons.Filled.MyLocation,
                                    contentDescription = null,
                                    tint = BeaconAmber,
                                )
                                Text(
                                    text = "Enable GPS & Alert Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "Needed to share exact accident coordinates and notify you immediately when someone nearby raises an SOS.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!hasLocationPermission) {
                                    OutlinedButton(
                                        onClick = onRequestLocationPermission,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Enable GPS")
                                    }
                                }
                                if (!hasNotificationPermission) {
                                    OutlinedButton(
                                        onClick = onRequestNotificationPermission,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text("Enable Alerts")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hero SOS Trigger Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF1E293B),
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Road Emergency?",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = userLocation?.let { "GPS ready: ${it.label}" }
                                            ?: "Broadcast your location to nearby helpers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFCBD5E1),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(CrimsonEmergency.copy(alpha = 0.28f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(CrimsonEmergency),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Sos,
                                            contentDescription = "SOS Beacon",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onOpenRaiseSos,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonEmergency,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("raise_sos_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.sos_button_label),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Live Community Alerts",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Surface(
                        color = ResponderEmerald.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = "LIVE SYNC",
                            style = MaterialTheme.typography.labelMedium,
                            color = ResponderEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            when (activeAlertsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = activeAlertsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val alerts = activeAlertsState.data
                    if (alerts.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(ResponderEmerald.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Shield,
                                            contentDescription = "All clear",
                                            tint = ResponderEmerald,
                                            modifier = Modifier.size(30.dp),
                                        )
                                    }
                                    Text(
                                        text = "All Roads Clear Right Now",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "When anyone raises an accident or SOS alert, it appears here immediately with live notifications.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(alerts, key = { it.id }) { alert ->
                            AlertFeedCard(
                                alert = alert,
                                isOwnAlert = alert.reporterId == currentUserId,
                                userLocation = userLocation,
                                onClick = { onAlertClick(alert.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertFeedCard(
    alert: SafetyAlert,
    isOwnAlert: Boolean,
    userLocation: CapturedLocation?,
    onClick: () -> Unit,
) {
    val category = AlertCategory.fromWire(alert.category)
    val severity = AlertSeverity.fromWire(alert.severity)
    val distanceText = if (userLocation != null && (alert.latitude != 0.0 || alert.longitude != 0.0)) {
        LocationHelper.formatDistanceKm(
            userLocation.latitude,
            userLocation.longitude,
            alert.latitude,
            alert.longitude,
        )
    } else {
        null
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("alert_card_${alert.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = category.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                SeverityBadge(severity = severity)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Incident location",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = alert.locationLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (alert.description.isNotBlank()) {
                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (isOwnAlert) "Raised by You" else "Raised by ${alert.reporterName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = buildString {
                            append(formatRelativeTimestamp(alert.createdAt))
                            if (distanceText != null) {
                                append(" • ")
                                append(distanceText)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(status = alert.status, responderCount = alert.responderCount)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (isOwnAlert) {
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.testTag("manage_alert_button"),
                    ) {
                        Text("Manage SOS & AI First-Aid")
                    }
                } else {
                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.testTag("help_alert_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolunteerActivism,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.help_button_label))
                    }
                }
            }
        }
    }
}

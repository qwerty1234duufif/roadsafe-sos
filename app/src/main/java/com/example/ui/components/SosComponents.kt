package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AlertCategory
import com.example.data.model.AlertSeverity
import com.example.data.model.SafetyAlert
import com.example.ui.theme.BeaconAmber
import com.example.ui.theme.CrimsonEmergency
import com.example.ui.theme.ResponderEmerald
import com.example.util.CapturedLocation
import com.google.firebase.Timestamp

@Composable
fun categoryIcon(category: AlertCategory): ImageVector = when (category) {
    AlertCategory.ACCIDENT -> Icons.Filled.CarCrash
    AlertCategory.MEDICAL -> Icons.Filled.LocalHospital
    AlertCategory.HAZARD -> Icons.Filled.Warning
    AlertCategory.BREAKDOWN -> Icons.Filled.DirectionsCar
}

@Composable
fun severityColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.CRITICAL -> CrimsonEmergency
    AlertSeverity.HIGH -> BeaconAmber
    AlertSeverity.MODERATE -> Color(0xFF0284C7)
}

@Composable
fun SeverityBadge(severity: AlertSeverity, modifier: Modifier = Modifier) {
    val color = severityColor(severity)
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = severity.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun StatusPill(status: String, responderCount: Long, modifier: Modifier = Modifier) {
    val isActive = status == "active"
    val bg = if (isActive) {
        if (responderCount > 0) ResponderEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val fg = if (isActive) {
        if (responderCount > 0) ResponderEmerald else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val text = if (!isActive) {
        "Resolved"
    } else if (responderCount == 1L) {
        "1 helper responding"
    } else if (responderCount > 1L) {
        "$responderCount helpers responding"
    } else {
        "Needs help now"
    }

    Surface(color = bg, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

fun formatRelativeTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val millis = timestamp.toDate().time
    val now = System.currentTimeMillis()
    return if (now - millis < 45_000L) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(
            millis,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
}

@Composable
fun IncomingSosBanner(
    alert: SafetyAlert?,
    onRespondClick: (SafetyAlert) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        if (alert != null) {
            Surface(
                color = CrimsonEmergency,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Incoming SOS alert",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = "NEW SOS ALERT: ${AlertCategory.fromWire(alert.category).label.uppercase()}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss alert banner",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = "${alert.reporterName} near ${alert.locationLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = { onRespondClick(alert) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = CrimsonEmergency,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "I Can Help",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text("Later")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SosQuickRaiseSheet(
    capturedLocation: CapturedLocation?,
    isFetchingLocation: Boolean,
    locationError: String?,
    isSubmitting: Boolean,
    onRefreshLocation: () -> Unit,
    onDismiss: () -> Unit,
    onSubmitSos: (
        category: AlertCategory,
        severity: AlertSeverity,
        description: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf(AlertCategory.ACCIDENT) }
    var selectedSeverity by remember { mutableStateOf(AlertSeverity.CRITICAL) }
    var description by remember { mutableStateOf("") }
    var manualLocationNote by remember { mutableStateOf("") }

    val effectiveLabel = when {
        manualLocationNote.isNotBlank() -> manualLocationNote.trim()
        capturedLocation != null -> capturedLocation.label
        else -> "GPS location unavailable (manual fallback)"
    }
    val effectiveLat = capturedLocation?.latitude ?: 0.0
    val effectiveLng = capturedLocation?.longitude ?: 0.0
    val canBroadcast = !isSubmitting && (capturedLocation != null || manualLocationNote.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Raise Road Safety SOS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "All active RoadSafe users will be notified immediately with your location.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // GPS status card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = "GPS status",
                        tint = if (capturedLocation != null) ResponderEmerald else BeaconAmber,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isFetchingLocation -> "Acquiring GPS location..."
                                capturedLocation != null -> "Location locked"
                                else -> "GPS location needed"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = capturedLocation?.let {
                                "${it.label} (${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)})"
                            } ?: (locationError ?: "Tap refresh or enter a landmark below."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onRefreshLocation,
                        enabled = !isFetchingLocation,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh GPS",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (capturedLocation == null) {
                OutlinedTextField(
                    value = manualLocationNote,
                    onValueChange = { manualLocationNote = it },
                    label = { Text("Road / Highway / Landmark") },
                    placeholder = { Text("e.g., I-280 South near Exit 12") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Incident Type",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlertCategory.entries.forEach { category ->
                        val selected = selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = categoryIcon(category),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Severity",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AlertSeverity.entries.forEach { severity ->
                        val selected = selectedSeverity == severity
                        FilterChip(
                            selected = selected,
                            onClick = { selectedSeverity = severity },
                            label = { Text(severity.label) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = severityColor(severity).copy(alpha = 0.18f),
                                selectedLabelColor = severityColor(severity),
                            ),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 280) description = it },
                label = { Text("What happened? (optional)") },
                placeholder = { Text("e.g., 2 cars involved, 1 person injured, blocking left lane") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_description_input"),
            )

            Button(
                onClick = {
                    onSubmitSos(
                        selectedCategory,
                        selectedSeverity,
                        description,
                        effectiveLat,
                        effectiveLng,
                        effectiveLabel,
                    )
                },
                enabled = canBroadcast,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonEmergency,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("broadcast_sos_button"),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Broadcasting SOS...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        text = "BROADCAST SOS NOW",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

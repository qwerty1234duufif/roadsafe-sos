package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AlertCategory(val wireValue: String, val label: String) {
    ACCIDENT("accident", "Road Accident"),
    MEDICAL("medical", "Medical SOS"),
    HAZARD("hazard", "Road Hazard"),
    BREAKDOWN("breakdown", "Stranded / Breakdown");

    companion object {
        fun fromWire(value: String): AlertCategory =
            entries.firstOrNull { it.wireValue == value } ?: ACCIDENT
    }
}

enum class AlertSeverity(val wireValue: String, val label: String) {
    CRITICAL("critical", "Critical"),
    HIGH("high", "High"),
    MODERATE("moderate", "Moderate");

    companion object {
        fun fromWire(value: String): AlertSeverity =
            entries.firstOrNull { it.wireValue == value } ?: HIGH
    }
}

@IgnoreExtraProperties
data class SafetyAlert(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val category: String = AlertCategory.ACCIDENT.wireValue,
    val severity: String = AlertSeverity.CRITICAL.wireValue,
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationLabel: String = "",
    val status: String = "active",
    val responderCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AlertResponse(
    @DocumentId val id: String = "",
    val responderId: String = "",
    val responderName: String = "",
    val alertId: String = "",
    val alertReporterId: String = "",
    val status: String = "responding",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val bloodGroup: String? = null,
    val vehicleInfo: String? = null,
    val medicalNotes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class HospitalSuggestion(
    val name: String,
    val estimatedDistance: String,
    val note: String,
)

data class EmergencyGuidance(
    val regionDetected: String,
    val ambulanceNumber: String,
    val policeNumber: String,
    val roadHelplineNumber: String,
    val summary: String,
    val nearestHospitals: List<HospitalSuggestion>,
    val immediateActions: List<String>,
    val doNotDoWarnings: List<String>,
)

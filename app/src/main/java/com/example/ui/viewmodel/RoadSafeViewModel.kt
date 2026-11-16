package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AlertCategory
import com.example.data.model.AlertSeverity
import com.example.data.model.SafetyAlert
import com.example.data.model.UserProfile
import com.example.data.repository.AlertRepository
import com.example.util.CapturedLocation
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class RoadSafeViewModel(
    private val repository: AlertRepository,
    val currentUserId: String,
    val initialUserName: String,
) : ViewModel() {

    private val seenAlertIds = mutableSetOf<String>()
    private var initialSnapshotProcessed = false

    private val _incomingSosEvent = MutableSharedFlow<SafetyAlert>(extraBufferCapacity = 4)
    val incomingSosEvent: SharedFlow<SafetyAlert> = _incomingSosEvent.asSharedFlow()

    private val _latestBannerAlert = MutableStateFlow<SafetyAlert?>(null)
    val latestBannerAlert: StateFlow<SafetyAlert?> = _latestBannerAlert.asStateFlow()

    private val _userLocation = MutableStateFlow<CapturedLocation?>(null)
    val userLocation: StateFlow<CapturedLocation?> = _userLocation.asStateFlow()

    private val _isSubmittingSos = MutableStateFlow(false)
    val isSubmittingSos: StateFlow<Boolean> = _isSubmittingSos.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val activeAlerts: StateFlow<UiState<List<SafetyAlert>>> = repository.observeActiveAlerts()
        .onEach { alerts ->
            if (!initialSnapshotProcessed) {
                alerts.forEach { seenAlertIds.add(it.id) }
                initialSnapshotProcessed = true
            } else {
                val brandNewOtherAlerts = alerts.filter {
                    it.id.isNotBlank() &&
                        it.id !in seenAlertIds &&
                        it.reporterId != currentUserId
                }
                alerts.forEach { if (it.id.isNotBlank()) seenAlertIds.add(it.id) }
                brandNewOtherAlerts.firstOrNull()?.let { newest ->
                    _latestBannerAlert.value = newest
                    _incomingSosEvent.tryEmit(newest)
                }
            }
        }
        .map<List<SafetyAlert>, UiState<List<SafetyAlert>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing active alerts", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load active alerts"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val myAlerts: StateFlow<UiState<List<SafetyAlert>>> = repository.observeMyAlerts()
        .map<List<SafetyAlert>, UiState<List<SafetyAlert>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user alerts", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load your alert history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    fun updateUserLocation(location: CapturedLocation) {
        _userLocation.value = location
    }

    fun dismissBannerAlert() {
        _latestBannerAlert.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun raiseSosAlert(
        category: AlertCategory,
        severity: AlertSeverity,
        description: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        onCreated: (String) -> Unit,
    ) {
        if (_isSubmittingSos.value) return
        viewModelScope.launch {
            _isSubmittingSos.value = true
            val displayName = userProfile.value?.displayName?.takeIf { it.isNotBlank() }
                ?: initialUserName.ifBlank { "RoadSafe Driver" }

            val result = repository.raiseAlert(
                reporterName = displayName,
                category = category,
                severity = severity,
                description = description,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
            )
            _isSubmittingSos.value = false
            result.fold(
                onSuccess = { newAlertId ->
                    seenAlertIds.add(newAlertId)
                    onCreated(newAlertId)
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Failed to broadcast SOS alert."
                }
            )
        }
    }

    fun saveSafetyProfile(
        displayName: String,
        bloodGroup: String,
        vehicleInfo: String,
        medicalNotes: String,
    ) {
        viewModelScope.launch {
            val result = repository.saveUserProfile(
                displayName = displayName,
                bloodGroup = bloodGroup,
                vehicleInfo = vehicleInfo,
                medicalNotes = medicalNotes,
            )
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Safety profile saved."
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not save safety profile."
                }
            )
        }
    }

    private companion object {
        const val TAG = "RoadSafeVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}

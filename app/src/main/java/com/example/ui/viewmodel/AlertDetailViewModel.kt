package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AlertResponse
import com.example.data.model.EmergencyGuidance
import com.example.data.model.SafetyAlert
import com.example.data.repository.AlertRepository
import com.example.data.repository.EmergencyAiRepository
import com.example.util.CapturedLocation
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiGuidanceState {
    data object Idle : AiGuidanceState
    data object Loading : AiGuidanceState
    data class Success(val guidance: EmergencyGuidance) : AiGuidanceState
    data class Error(val message: String) : AiGuidanceState
}

class AlertDetailViewModel(
    private val alertRepository: AlertRepository,
    private val aiRepository: EmergencyAiRepository,
    private val alertId: String,
    val currentUserId: String,
    val responderDisplayName: String,
) : ViewModel() {

    val alertState: StateFlow<UiState<SafetyAlert?>> = alertRepository.observeAlert(alertId)
        .map<SafetyAlert?, UiState<SafetyAlert?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing alert $alertId", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load alert details"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val myResponse: StateFlow<AlertResponse?> = alertRepository.observeMyResponse(alertId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing my response for $alertId", error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null,
        )

    val respondersForMyAlert: StateFlow<List<AlertResponse>> =
        alertRepository.observeRespondersForMyAlert(alertId)
            .catch { error ->
                if (error is CancellationException) throw error
                // Non-reporters are expected to not list other responders.
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList(),
            )

    private val _aiState = MutableStateFlow<AiGuidanceState>(AiGuidanceState.Idle)
    val aiState: StateFlow<AiGuidanceState> = _aiState.asStateFlow()

    private val _isUpdatingAction = MutableStateFlow(false)
    val isUpdatingAction: StateFlow<Boolean> = _isUpdatingAction.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var hasAutoFetchedAi = false

    fun clearActionError() {
        _actionError.value = null
    }

    fun respondAndFetchAiGuidance(
        alert: SafetyAlert,
        responderLocation: CapturedLocation?,
    ) {
        viewModelScope.launch {
            _isUpdatingAction.value = true
            _actionError.value = null
            val result = alertRepository.respondToAlert(alert, responderDisplayName)
            _isUpdatingAction.value = false
            result.fold(
                onSuccess = {
                    fetchAiGuidance(alert, responderLocation, force = true)
                },
                onFailure = { error ->
                    _actionError.value = error.localizedMessage ?: "Failed to register help response."
                }
            )
        }
    }

    fun ensureAiGuidanceIfAuthorized(
        alert: SafetyAlert,
        isResponderOrOwner: Boolean,
        responderLocation: CapturedLocation?,
    ) {
        if (isResponderOrOwner && !hasAutoFetchedAi && _aiState.value is AiGuidanceState.Idle) {
            fetchAiGuidance(alert, responderLocation, force = false)
        }
    }

    fun fetchAiGuidance(
        alert: SafetyAlert,
        responderLocation: CapturedLocation?,
        force: Boolean = true,
    ) {
        if (!force && _aiState.value is AiGuidanceState.Loading) return
        hasAutoFetchedAi = true
        viewModelScope.launch {
            _aiState.value = AiGuidanceState.Loading
            val result = aiRepository.fetchEmergencyGuidance(
                alert = alert,
                responderLat = responderLocation?.latitude,
                responderLng = responderLocation?.longitude,
            )
            result.fold(
                onSuccess = { guidance ->
                    _aiState.value = AiGuidanceState.Success(guidance)
                },
                onFailure = { error ->
                    _aiState.value = AiGuidanceState.Error(
                        error.localizedMessage ?: "Could not load Gemini emergency guidance."
                    )
                }
            )
        }
    }

    fun markArrived() {
        viewModelScope.launch {
            _isUpdatingAction.value = true
            val result = alertRepository.markArrivedOnScene(alertId)
            _isUpdatingAction.value = false
            result.onFailure { error ->
                _actionError.value = error.localizedMessage ?: "Failed to update arrival status."
            }
        }
    }

    fun resolveAlert() {
        viewModelScope.launch {
            _isUpdatingAction.value = true
            val result = alertRepository.resolveAlert(alertId)
            _isUpdatingAction.value = false
            result.onFailure { error ->
                _actionError.value = error.localizedMessage ?: "Failed to resolve alert."
            }
        }
    }

    private companion object {
        const val TAG = "AlertDetailVM"
    }
}

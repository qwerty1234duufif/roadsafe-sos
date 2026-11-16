package com.example.data.repository

import android.util.Log
import com.example.data.model.AlertCategory
import com.example.data.model.AlertSeverity
import com.example.data.model.EmergencyGuidance
import com.example.data.model.HospitalSuggestion
import com.example.data.model.SafetyAlert
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "EmergencyAiRepo"
private const val AI_TIMEOUT_MS = 60_000L

class EmergencyAiRepository {

    private val guidanceSchema = Schema.obj(
        properties = mapOf(
            "regionDetected" to Schema.string(
                description = "Detected city, state, and country from the coordinates or address"
            ),
            "ambulanceNumber" to Schema.string(
                description = "Primary local ambulance/EMS emergency phone number for that country/region (e.g., 108, 112, 911, 999)"
            ),
            "policeNumber" to Schema.string(
                description = "Local police emergency phone number for that region"
            ),
            "roadHelplineNumber" to Schema.string(
                description = "Highway/road accident helpline number for that region if applicable, or general emergency number"
            ),
            "summary" to Schema.string(
                description = "Short, calm, 1-2 sentence situational briefing for the helper"
            ),
            "nearestHospitals" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Name of a known nearby hospital or trauma center"),
                        "estimatedDistance" to Schema.string(description = "Approximate area or distance hint from the coordinates"),
                        "note" to Schema.string(description = "Trauma/emergency care note or landmark")
                    )
                ),
                description = "2 to 3 likely nearest hospitals or emergency trauma centers near the incident location"
            ),
            "immediateActions" to Schema.array(
                items = Schema.string(),
                description = "4 to 6 clear, numbered-ready first-aid and scene-safety steps to do until ambulance/help arrives"
            ),
            "doNotDoWarnings" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 critical things NOT to do (e.g. do not move injured person if neck/back pain suspected)"
            ),
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = guidanceSchema
            }
        )
    }

    suspend fun fetchEmergencyGuidance(
        alert: SafetyAlert,
        responderLat: Double?,
        responderLng: Double?,
    ): Result<EmergencyGuidance> {
        val categoryLabel = AlertCategory.fromWire(alert.category).label
        val severityLabel = AlertSeverity.fromWire(alert.severity).label
        val responderContext = if (responderLat != null && responderLng != null) {
            "Responder current GPS: $responderLat, $responderLng."
        } else {
            "Responder GPS unavailable."
        }

        val prompt = """
            You are an emergency road-safety dispatch assistant. A community responder is helping at an active road incident.
            Provide accurate, region-specific emergency numbers, likely nearest major hospitals/trauma centers near the coordinates,
            and clear step-by-step guidance on what to do until professional help arrives.

            Incident Details:
            - Category: $categoryLabel
            - Severity: $severityLabel
            - Incident GPS Coordinates: ${alert.latitude}, ${alert.longitude}
            - Location Label: ${alert.locationLabel}
            - Description: ${alert.description.ifBlank { "No extra description provided." }}
            - $responderContext

            Requirements:
            1. Detect the country/region accurately from the coordinates (${alert.latitude}, ${alert.longitude}) and location label.
            2. Provide the exact local ambulance, police, and road/highway emergency phone numbers for that country/region.
            3. Suggest 2 to 3 major hospitals or emergency medical centers closest to (${alert.latitude}, ${alert.longitude}). If exact distance cannot be verified offline, state approximate locality/direction honestly.
            4. Give practical, medically sound first-aid and traffic-safety steps for a bystander responder until ambulance arrives.
            5. Include critical 'do not do' warnings (e.g., spinal precautions, fire/fuel hazards).
        """.trimIndent()

        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val json = JSONObject(jsonText)
            val hospitalsJson = json.optJSONArray("nearestHospitals")
            val hospitals = buildList {
                if (hospitalsJson != null) {
                    for (i in 0 until hospitalsJson.length()) {
                        val item = hospitalsJson.getJSONObject(i)
                        add(
                            HospitalSuggestion(
                                name = item.optString("name", "Nearest Emergency Hospital"),
                                estimatedDistance = item.optString("estimatedDistance", "Nearby"),
                                note = item.optString("note", "Emergency & trauma care"),
                            )
                        )
                    }
                }
            }

            val actionsJson = json.optJSONArray("immediateActions")
            val actions = buildList {
                if (actionsJson != null) {
                    for (i in 0 until actionsJson.length()) {
                        add(actionsJson.getString(i))
                    }
                }
            }

            val warningsJson = json.optJSONArray("doNotDoWarnings")
            val warnings = buildList {
                if (warningsJson != null) {
                    for (i in 0 until warningsJson.length()) {
                        add(warningsJson.getString(i))
                    }
                }
            }

            Result.success(
                EmergencyGuidance(
                    regionDetected = json.optString("regionDetected", alert.locationLabel),
                    ambulanceNumber = json.optString("ambulanceNumber", "112"),
                    policeNumber = json.optString("policeNumber", "112"),
                    roadHelplineNumber = json.optString("roadHelplineNumber", "112"),
                    summary = json.optString("summary", "Stay calm, secure the scene, and keep the victim safe until EMS arrives."),
                    nearestHospitals = hospitals,
                    immediateActions = actions,
                    doNotDoWarnings = warnings,
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini emergency guidance timed out", e)
            Result.failure(IllegalStateException("AI emergency lookup timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini emergency guidance failed", e)
            Result.failure(e)
        }
    }
}

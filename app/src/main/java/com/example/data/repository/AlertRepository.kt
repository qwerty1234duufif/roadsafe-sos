package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AlertCategory
import com.example.data.model.AlertResponse
import com.example.data.model.AlertSeverity
import com.example.data.model.SafetyAlert
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AlertRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(db.app),
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeActiveAlerts(): Flow<List<SafetyAlert>> = flow {
        val uid = requireUserId()
        val path = ALERTS_COLLECTION
        emitAll(
            db.collection(ALERTS_COLLECTION)
                .whereEqualTo("status", "active")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SafetyAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeMyAlerts(): Flow<List<SafetyAlert>> = flow {
        val uid = requireUserId()
        val path = ALERTS_COLLECTION
        emitAll(
            db.collection(ALERTS_COLLECTION)
                .whereEqualTo("reporterId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SafetyAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeAlert(alertId: String): Flow<SafetyAlert?> = flow {
        val uid = requireUserId()
        val docRef = db.collection(ALERTS_COLLECTION).document(alertId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SafetyAlert::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeMyResponse(alertId: String): Flow<AlertResponse?> = flow {
        val uid = requireUserId()
        val docRef = db.collection(ALERTS_COLLECTION)
            .document(alertId)
            .collection(RESPONSES_SUBCOLLECTION)
            .document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AlertResponse::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeRespondersForMyAlert(alertId: String): Flow<List<AlertResponse>> = flow {
        val uid = requireUserId()
        val path = "$ALERTS_COLLECTION/$alertId/$RESPONSES_SUBCOLLECTION"
        emitAll(
            db.collection(ALERTS_COLLECTION)
                .document(alertId)
                .collection(RESPONSES_SUBCOLLECTION)
                .whereEqualTo("alertReporterId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AlertResponse::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun raiseAlert(
        reporterName: String,
        category: AlertCategory,
        severity: AlertSeverity,
        description: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection(ALERTS_COLLECTION).document()
        val safeName = reporterName.trim().ifBlank { "RoadSafe User" }.take(60)
        val safeLabel = locationLabel.trim().ifBlank {
            "${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}"
        }.take(200)
        val safeDescription = description.trim().take(300)

        val payload = mapOf(
            "reporterId" to uid,
            "reporterName" to safeName,
            "category" to category.wireValue,
            "severity" to severity.wireValue,
            "description" to safeDescription,
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "locationLabel" to safeLabel,
            "status" to "active",
            "responderCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun respondToAlert(alert: SafetyAlert, responderName: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val alertRef = db.collection(ALERTS_COLLECTION).document(alert.id)
        val responseRef = alertRef.collection(RESPONSES_SUBCOLLECTION).document(uid)

        return try {
            val existing = responseRef.get().await()
            if (existing.exists()) {
                return Result.success(Unit)
            }
            val safeName = responderName.trim().ifBlank { "Community Helper" }.take(60)
            val batch = db.batch()
            batch.set(
                responseRef,
                mapOf(
                    "responderId" to uid,
                    "responderName" to safeName,
                    "alertId" to alert.id,
                    "alertReporterId" to alert.reporterId,
                    "status" to "responding",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                alertRef,
                mapOf(
                    "responderCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, responseRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun markArrivedOnScene(alertId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val responseRef = db.collection(ALERTS_COLLECTION)
            .document(alertId)
            .collection(RESPONSES_SUBCOLLECTION)
            .document(uid)
        return try {
            responseRef.update(
                mapOf(
                    "status" to "arrived",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, responseRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun resolveAlert(alertId: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val alertRef = db.collection(ALERTS_COLLECTION).document(alertId)
        return try {
            alertRef.update(
                mapOf(
                    "status" to "resolved",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, alertRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun getAlertById(alertId: String): Result<SafetyAlert?> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val alertRef = db.collection(ALERTS_COLLECTION).document(alertId)
        return try {
            val snap = alertRef.get().await()
            Result.success(
                if (snap.exists()) {
                    snap.toObject(
                        SafetyAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, alertRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection(USERS_COLLECTION).document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserProfile(
        displayName: String,
        bloodGroup: String?,
        vehicleInfo: String?,
        medicalNotes: String?,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection(USERS_COLLECTION).document(uid)
        return try {
            val existing = docRef.get().await()
            val cleanName = displayName.trim().ifBlank { "RoadSafe User" }.take(60)
            val cleanBlood = bloodGroup?.trim()?.takeIf { it.isNotEmpty() }?.take(12)
            val cleanVehicle = vehicleInfo?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
            val cleanNotes = medicalNotes?.trim()?.takeIf { it.isNotEmpty() }?.take(200)

            if (existing.exists()) {
                val updates = mutableMapOf<String, Any?>(
                    "displayName" to cleanName,
                    "bloodGroup" to cleanBlood,
                    "vehicleInfo" to cleanVehicle,
                    "medicalNotes" to cleanNotes,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (cleanBlood != null) payload["bloodGroup"] = cleanBlood
                if (cleanVehicle != null) payload["vehicleInfo"] = cleanVehicle
                if (cleanNotes != null) payload["medicalNotes"] = cleanNotes
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    companion object {
        const val ALERTS_COLLECTION = "alerts"
        const val RESPONSES_SUBCOLLECTION = "responses"
        const val USERS_COLLECTION = "users"
    }
}

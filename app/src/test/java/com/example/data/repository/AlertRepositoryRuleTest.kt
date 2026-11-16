package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AlertCategory
import com.example.data.model.AlertSeverity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun raiseAlert_validPayload_createsAndEmitsRealtime() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = AlertRepository(firestore, auth)

        val alertId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.raiseAlert(
                reporterName = "Alice",
                category = AlertCategory.ACCIDENT,
                severity = AlertSeverity.CRITICAL,
                description = "Collision near highway ramp",
                latitude = 37.422,
                longitude = -122.084,
                locationLabel = "Highway 101 North"
            ).getOrThrow()
        }
        assertTrue(alertId.isNotEmpty())

        val activeAlerts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeActiveAlerts().first { list -> list.any { it.id == alertId } }
        }
        val created = activeAlerts.first { it.id == alertId }
        assertEquals("active", created.status)
        assertEquals(0L, created.responderCount)
    }

    @Test
    fun respondToAlert_otherUser_atomicallyCreatesResponseAndIncrementsCount() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = AlertRepository(firestore, auth)
        val alertId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.raiseAlert(
                reporterName = "Alice",
                category = AlertCategory.MEDICAL,
                severity = AlertSeverity.HIGH,
                description = "Driver needs medical help",
                latitude = 37.422,
                longitude = -122.084,
                locationLabel = "Main Street"
            ).getOrThrow()
        }

        val createdAlert = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getAlertById(alertId).getOrThrow()
        }
        assertNotNull(createdAlert)

        signInTestUser(BOB_EMAIL)
        val bobRepo = AlertRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.respondToAlert(createdAlert!!, "Bob").getOrThrow()
        }

        val updatedAlert = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getAlertById(alertId).getOrThrow()
        }
        assertEquals(1L, updatedAlert?.responderCount)

        val myResponse = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeMyResponse(alertId).first { it != null }
        }
        assertEquals("responding", myResponse?.status)
    }

    @Test
    fun resolveAlert_nonOwner_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = AlertRepository(firestore, auth)
        val alertId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.raiseAlert(
                reporterName = "Alice",
                category = AlertCategory.HAZARD,
                severity = AlertSeverity.MODERATE,
                description = "Debris blocking right lane",
                latitude = 37.422,
                longitude = -122.084,
                locationLabel = "Oak Ave"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = AlertRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.resolveAlert(alertId)
        }
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull(exception)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)
    }

    @Test
    fun userProfile_saveAndRead_isolatesToOwner() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AlertRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveUserProfile(
                displayName = "Alice Driver",
                bloodGroup = "O+",
                vehicleInfo = "Blue Sedan",
                medicalNotes = "No allergies"
            ).getOrThrow()
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeUserProfile().first { it != null }
        }
        assertEquals("O+", profile?.bloodGroup)

        // Bob cannot read Alice's profile directly
        signInTestUser(BOB_EMAIL)
        val bobReadException = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection(AlertRepository.USERS_COLLECTION).document(aliceUid).get().let { task ->
                    com.google.android.gms.tasks.Tasks.await(task)
                }
            }
        }.exceptionOrNull()
        assertNotNull(bobReadException)
    }

    @Test
    fun observeActiveAlerts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = AlertRepository(firestore, auth)

        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { repository.observeActiveAlerts().first() }
        }.exceptionOrNull()
        assertNotNull(exception)
        val firestoreException = exception?.unwrapFirestoreException()
        // Repository fail-fast requireUserId() throws IllegalStateException before hitting network
        // OR if Firestore listener runs unauthenticated it throws PERMISSION_DENIED.
        assertTrue(
            exception is IllegalStateException ||
                firestoreException?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        assertNull(auth.currentUser)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_roadsafe@test.com"
        const val BOB_EMAIL = "bob_roadsafe@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

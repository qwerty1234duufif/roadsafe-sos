package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Result<CapturedLocation> {
        if (!hasLocationPermission(context)) {
            return Result.failure(SecurityException("Location permission not granted."))
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val tokenSource = CancellationTokenSource()
            val freshLocation = withTimeoutOrNull(8_000L) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
            }
            val location = freshLocation ?: withTimeoutOrNull(3_000L) {
                client.lastLocation.await()
            }
            if (location == null) {
                Result.failure(IllegalStateException("Unable to acquire GPS fix. Ensure device location is enabled."))
            } else {
                val label = reverseGeocodeLabel(context, location.latitude, location.longitude)
                Result.success(CapturedLocation(location.latitude, location.longitude, label))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reverseGeocodeLabel(context: Context, lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            val fallback = "${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lng)}"
            if (!Geocoder.isPresent()) return@withContext fallback
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    withTimeoutOrNull(3_000L) {
                        suspendCancellableCoroutine<Address?> { cont ->
                            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
                if (address == null) return@withContext fallback
                val parts = listOfNotNull(
                    address.thoroughfare ?: address.featureName,
                    address.subLocality ?: address.locality,
                    address.adminArea,
                    address.countryName,
                ).distinct().filter { it.isNotBlank() }
                if (parts.isEmpty()) fallback else parts.joinToString(", ").take(190)
            } catch (_: Exception) {
                fallback
            }
        }

    fun formatDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): String {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        val meters = results[0]
        return if (meters < 1000f) {
            "${meters.roundToInt()} m away"
        } else {
            val km = meters / 1000f
            "${"%.1f".format(Locale.US, km)} km away"
        }
    }

    fun openMapsDirections(context: Context, lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label)
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun openNearbyHospitalsInMaps(context: Context, lat: Double, lng: Double) {
        val uri = Uri.parse("geo:$lat,$lng?q=${Uri.encode("hospital emergency")}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/search/hospital+near+$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun dialPhoneNumber(context: Context, phoneNumber: String) {
        val clean = phoneNumber.filter { it.isDigit() || it == '+' }.ifBlank { "112" }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

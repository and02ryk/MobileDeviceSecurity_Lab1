package org.example.project

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.example.project.model.GeoPhoto
import org.example.project.model.PermissionStatus
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var cameraStatus by remember { mutableStateOf(PermissionStatus.NotRequested) }
            var locationStatus by remember { mutableStateOf(PermissionStatus.NotRequested) }

            val photos = remember { mutableStateListOf<GeoPhoto>() }
            var lastKnownLocation by remember { mutableStateOf<Location?>(null) }

            var tempImageFile by remember { mutableStateOf<File?>(null) }

            LaunchedEffect(Unit) {
                cameraStatus = resolveCurrentStatus(Manifest.permission.CAMERA)
                locationStatus = resolveCurrentStatus(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        cameraStatus = resolveCurrentStatus(Manifest.permission.CAMERA)
                        locationStatus = resolveCurrentStatus(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                cameraStatus = if (granted) PermissionStatus.Granted else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                        PermissionStatus.Denied else PermissionStatus.PermanentlyDenied
                }
            }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                locationStatus = if (granted) PermissionStatus.Granted else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))
                        PermissionStatus.Denied else PermissionStatus.PermanentlyDenied
                }
            }

            val fusedLocationClient by lazy {
                LocationServices.getFusedLocationProviderClient(this)
            }

            val cameraLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                if (!success) return@rememberLauncherForActivityResult

                val file = tempImageFile ?: return@rememberLauncherForActivityResult

                val location = lastKnownLocation

                if (location != null) {
                    writeExifGps(file, location)
                }

                val contentUri = saveToMediaStore(file)
                file.delete()
                tempImageFile = null
                lastKnownLocation = null

                if (contentUri != null) {
                    photos.add(
                        GeoPhoto(
                            id = UUID.randomUUID().toString(),
                            imagePath = contentUri.toString(),
                            lat = location?.latitude ?: 0.0,
                            lon = location?.longitude ?: 0.0
                        )
                    )
                }
            }

            App(
                cameraStatus = cameraStatus,
                locationStatus = locationStatus,
                photos = photos,
                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onRequestLocation = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onOpenSettings = { openAppSettings() },
                onTakePhoto = {
                    val dir = File(cacheDir, "camera").also { it.mkdirs() }
                    val file = File(dir, "geosnap_${System.currentTimeMillis()}.jpg")
                    tempImageFile = file

                    val uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )

                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        val cts = CancellationTokenSource()
                        fusedLocationClient
                            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    lastKnownLocation = location
                                }
                                cameraLauncher.launch(uri)
                            }
                            .addOnFailureListener {
                                cameraLauncher.launch(uri)
                            }
                    } else {
                        cameraLauncher.launch(uri)
                    }
                }
            )
        }
    }

    private fun resolveCurrentStatus(permission: String): PermissionStatus =
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
            PermissionStatus.Granted
        else
            PermissionStatus.NotRequested

    private fun writeExifGps(file: File, location: Location) {
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDms(location.latitude))
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDms(location.longitude))
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")
            setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${location.altitude.toLong()}/1")
            setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
            saveAttributes()
        }
    }

    private fun decimalToDms(decimal: Double): String {
        val abs = if (decimal < 0) -decimal else decimal
        val degrees = abs.toInt()
        val minFull = (abs - degrees) * 60
        val minutes = minFull.toInt()
        val seconds = ((minFull - minutes) * 60 * 1000).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }

    private fun saveToMediaStore(file: File): Uri? {
        val name = "GeoSnap_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/GeoSnap"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }

        return uri
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }
}

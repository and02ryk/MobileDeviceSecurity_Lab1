package org.example.project

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.example.project.model.GeoPhoto
import org.example.project.model.PermissionStatus
import platform.AVFoundation.*
import platform.CoreLocation.*
import platform.Foundation.*
import platform.Photos.*
import platform.UIKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
fun MainViewController() = ComposeUIViewController {

    var cameraStatus by remember { mutableStateOf(PermissionStatus.NotRequested) }
    var locationStatus by remember { mutableStateOf(PermissionStatus.NotRequested) }

    val photos = remember { mutableStateListOf<GeoPhoto>() }

    val locationDelegate = remember {
        LocationDelegate { newStatus -> locationStatus = newStatus }
    }
    val locationManager = remember {
        CLLocationManager().also { it.delegate = locationDelegate }
    }

    val pickerDelegate = remember {
        ImagePickerDelegate(
            locationManager = locationManager,
            onPhotoCaptured = { geoPhoto -> photos.add(geoPhoto) }
        )
    }

    LaunchedEffect(Unit) {
        cameraStatus = currentCameraStatus()
        locationStatus = currentLocationStatus(locationManager)
        if (locationStatus == PermissionStatus.Granted) {
            locationManager.startUpdatingLocation()
        }
    }

    App(
        cameraStatus = cameraStatus,
        locationStatus = locationStatus,
        photos = photos,
        onRequestCamera = {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            if (status == AVAuthorizationStatusNotDetermined) {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    cameraStatus = if (granted) PermissionStatus.Granted
                    else PermissionStatus.PermanentlyDenied
                }
            } else {
                cameraStatus = mapAVStatus(status)
            }
        },

        onRequestLocation = {
            val status = locationManager.authorizationStatus
            if (status == kCLAuthorizationStatusNotDetermined) {
                locationManager.requestWhenInUseAuthorization()
            } else {
                locationStatus = mapCLStatus(status)
                if (locationStatus == PermissionStatus.Granted) {
                    locationManager.startUpdatingLocation()
                }
            }
        },

        onOpenSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(
                    url = url,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = null
                )
            }
        },

        onTakePhoto = {
            if (!UIImagePickerController.isSourceTypeAvailable(
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                )
            ) return@App

            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                delegate = pickerDelegate
                allowsEditing = false
            }

            UIApplication.sharedApplication.keyWindow
                ?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(
    private val onStatusChange: (PermissionStatus) -> Unit
) : NSObject(), CLLocationManagerDelegateProtocol {

    var lastLocation: CLLocation? = null

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = mapCLStatus(manager.authorizationStatus)
        onStatusChange(status)
        if (status == PermissionStatus.Granted) {
            manager.startUpdatingLocation()
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        lastLocation = didUpdateLocations.lastOrNull() as? CLLocation
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ImagePickerDelegate(
    private val locationManager: CLLocationManager,
    private val onPhotoCaptured: (GeoPhoto) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return

        val delegate = locationManager.delegate as? LocationDelegate
        val location = delegate?.lastLocation

        saveToPhotoLibrary(image, location)

        val filePath = saveToDocuments(image)

        if (filePath != null) {
            onPhotoCaptured(
                GeoPhoto(
                    id = NSUUID().UUIDString,
                    // Coil3 на iOS загружает локальные файлы по схеме file://
                    imagePath = "file://$filePath",
                    lat = location?.coordinate?.useContents { latitude } ?: 0.0,
                    lon = location?.coordinate?.useContents { longitude } ?: 0.0
                )
            )
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }

    private fun saveToPhotoLibrary(image: UIImage, location: CLLocation?) {
        PHPhotoLibrary.requestAuthorizationForAccessLevel(
            accessLevel = PHAccessLevelAddOnly,
            handler = { status ->
                if (status != PHAuthorizationStatusAuthorized &&
                    status != PHAuthorizationStatusLimited
                ) return@requestAuthorizationForAccessLevel

                PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                    val request = PHAssetCreationRequest.creationRequestForAsset()
                    request.addResourceWithType(
                        type = PHAssetResourceTypePhoto,
                        data = UIImageJPEGRepresentation(image, 0.9) ?: return@performChanges,
                        options = null
                    )
                    if (location != null) {
                        request.location = location
                    }
                }, completionHandler = { _, _ -> })
            }
        )
    }

    private fun saveToDocuments(image: UIImage): String? {
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return null

        val fileName = "geosnap_${NSDate().timeIntervalSince1970.toLong()}.jpg"
        val filePath = "$docs/$fileName"

        val data = UIImageJPEGRepresentation(image, 0.9) ?: return null
        return if (data.writeToFile(filePath, atomically = true)) filePath else null
    }
}

private fun currentCameraStatus(): PermissionStatus =
    mapAVStatus(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo))

private fun currentLocationStatus(manager: CLLocationManager): PermissionStatus =
    mapCLStatus(manager.authorizationStatus)

private fun mapAVStatus(status: AVAuthorizationStatus): PermissionStatus = when (status) {
    AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
    AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied
    else -> PermissionStatus.NotRequested
}

private fun mapCLStatus(status: CLAuthorizationStatus): PermissionStatus = when (status) {
    kCLAuthorizationStatusAuthorizedWhenInUse,
    kCLAuthorizationStatusAuthorizedAlways -> PermissionStatus.Granted

    kCLAuthorizationStatusDenied,
    kCLAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied

    else -> PermissionStatus.NotRequested
}
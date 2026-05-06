package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.model.GeoPhoto
import org.example.project.model.PermissionStatus
import org.example.project.ui.GallerySection
import org.example.project.ui.MainFeatureSection
import org.example.project.ui.PermissionSection

@Composable
fun App(
    cameraStatus: PermissionStatus,
    locationStatus: PermissionStatus,
    photos: List<GeoPhoto>,
    onRequestCamera: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onTakePhoto: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Фотографии с геолокацией",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider()

                PermissionSection(
                    title = "Камера",
                    icon = "📷",
                    rationale = "Нужна для создания фотографий",
                    status = cameraStatus,
                    onRequest = onRequestCamera,
                    onOpenSettings = onOpenSettings
                )

                HorizontalDivider()

                PermissionSection(
                    title = "Геолокация",
                    icon = "📍",
                    rationale = "Нужна для прикрепления координат к фото",
                    status = locationStatus,
                    onRequest = onRequestLocation,
                    onOpenSettings = onOpenSettings
                )

                HorizontalDivider()

                MainFeatureSection(
                    cameraGranted = cameraStatus == PermissionStatus.Granted,
                    locationGranted = locationStatus == PermissionStatus.Granted,
                    onTakePhoto = onTakePhoto
                )

                if (photos.isNotEmpty()) {
                    HorizontalDivider()
                    GallerySection(photos)
                }
            }
        }
    }
}

package org.example.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.model.GeoPhoto
import org.example.project.model.PermissionStatus

@Composable
fun MainFeatureSection(
    cameraGranted: Boolean,
    locationGranted: Boolean,
    onTakePhoto: () -> Unit
) {
    val allGranted = cameraGranted && locationGranted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (allGranted) {
                Text(
                    "Всё готово!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Button(onClick = onTakePhoto) {
                    Text("Сделать фото")
                }
            } else {
                Text(
                    "Предоставьте разрешения выше, чтобы использовать приложение",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!cameraGranted) Text(
                    "Нужен доступ к камере",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!locationGranted) Text(
                    "Нужен доступ к геолокации",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionSection(
    title: String,
    icon: String,
    rationale: String,
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            StatusBadge(status)
        }

        Text(
            rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (status) {
            PermissionStatus.NotRequested, PermissionStatus.Denied -> {
                Button(onClick = onRequest) {
                    Text("Разрешить")
                }
            }

            PermissionStatus.PermanentlyDenied -> {
                // Подсказка с кнопкой открытия настроек
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️")
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Доступ запрещён",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Разрешите «$title» вручную в настройках приложения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onOpenSettings) {
                            Text(
                                "Настройки",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            PermissionStatus.Granted -> {  }
        }
    }
}

@Composable
fun GallerySection(photos: List<GeoPhoto>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Снимки (${photos.size})", style = MaterialTheme.typography.titleMedium)

        photos.reversed().forEach { photo ->
            GeoPhotoCard(photo)
        }
    }
}

@Composable
private fun StatusBadge(status: PermissionStatus) {
    val (text, color) = when (status) {
        PermissionStatus.Granted          -> "✓ Разрешено" to MaterialTheme.colorScheme.primary
        PermissionStatus.Denied           -> "Отклонено"   to MaterialTheme.colorScheme.error
        PermissionStatus.PermanentlyDenied-> "Запрещено"   to MaterialTheme.colorScheme.error
        PermissionStatus.NotRequested     -> "Не запрошено" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

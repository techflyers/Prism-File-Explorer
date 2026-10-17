package com.techflyers.compose.file.explorer.screen.main.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.INTERNAL_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.REMOTE_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.ROOT

@Composable
fun StorageDeviceView(
    storageDevice: StorageDevice,
    onClick: () -> Unit = {}
) {
    val progress = if (storageDevice.totalSize > 0) {
        (storageDevice.usedSize.toFloat() / storageDevice.totalSize).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "storageProgress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.padding(8.dp),
            imageVector = when (storageDevice.type) {
                INTERNAL_STORAGE -> Icons.Rounded.FolderOpen
                ROOT -> Icons.Rounded.Numbers
                REMOTE_STORAGE -> Icons.Rounded.Cloud
                else -> Icons.Rounded.SdStorage
            },
            contentDescription = null
        )
        Space(size = 8.dp)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = storageDevice.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (storageDevice.totalSize > 0) {
                    val free = (storageDevice.totalSize - storageDevice.usedSize).coerceAtLeast(0L)
                    Space(size = 8.dp)
                    Text(
                        text = stringResource(R.string.free_storage, free.toFormattedSize()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (progress > 0.85f) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            if (storageDevice.totalSize > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    progress = { animatedProgress },
                    strokeCap = StrokeCap.Round,
                    color = if (progress > 0.85f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${storageDevice.usedSize.toFormattedSize()} used",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Text(
                        text = "${storageDevice.totalSize.toFormattedSize()} total",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Unavailable",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
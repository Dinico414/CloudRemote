package com.xenonware.cloudremote.ui.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenon.mylibrary.ActivityScreen
import com.xenon.mylibrary.values.MediumCornerRadius
import com.xenon.mylibrary.values.MediumPadding
import com.xenonware.cloudremote.R
import com.xenonware.cloudremote.viewmodel.LayoutType
import com.xenonware.cloudremote.viewmodel.MainViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CompactPingDevice(
    viewModel: MainViewModel,
    layoutType: LayoutType,
    onBack: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val cloudDevices = devices.filter { it.id != viewModel.localDeviceId }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(30000.milliseconds)
        }
    }

    ActivityScreen(
        titleText = stringResource(R.string.ping_device),
        expandable = layoutType != LayoutType.COVER && layoutType != LayoutType.SMALL,
        onNavigationIconClick = onBack,
        navigationIcon = {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigate_back_description))
        },
        content = { scaffoldPadding ->
            if (cloudDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_other_devices_found))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = scaffoldPadding.calculateBottomPadding() + MediumPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cloudDevices) { device ->
                        val isOnline = (currentTime - device.lastUpdated) < 900_000

                        Card(
                            shape = RoundedCornerShape(MediumCornerRadius),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(alpha = if (isOnline) 1f else 0.5f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceBright
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (device.isPinged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (device.isPinged) stringResource(R.string.pinging) else if (isOnline) stringResource(R.string.ready_to_ping) else stringResource(R.string.offline),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            viewModel.updateDeviceFields(
                                                device.id,
                                                mapOf("isPinged" to !device.isPinged)
                                            )
                                        },
                                        enabled = isOnline || device.isPinged
                                    ) {
                                        Text(if (device.isPinged) stringResource(R.string.stop) else stringResource(R.string.ping))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

package com.secureguard.enterprise.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import com.secureguard.enterprise.R
import com.secureguard.enterprise.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_profile),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::setUserName,
                            label = { Text(stringResource(R.string.label_user)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = state.organization,
                            onValueChange = viewModel::setOrganization,
                            label = { Text(stringResource(R.string.label_organization)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_notifications),
                            style = MaterialTheme.typography.titleMedium)
                        SwitchRow(stringResource(R.string.switch_push_notifications), state.notificationsEnabled,
                            viewModel::setNotifications)
                        HorizontalDivider()
                        SwitchRow(stringResource(R.string.switch_offline_only), state.offlineOnly,
                            viewModel::setOfflineOnly)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_connections), style = MaterialTheme.typography.titleMedium)
                        SwitchRow(stringResource(R.string.switch_external_crowd),
                            state.externalCrowdAllowed, viewModel::setExternalCrowd)
                        HorizontalDivider()
                        SwitchRow(stringResource(R.string.switch_learning_agent), state.learningMode,
                            viewModel::setLearning)
                        HorizontalDivider()
                        SwitchRow(stringResource(R.string.switch_dark_mode), state.darkMode, viewModel::setDarkMode)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_privacy),
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.consentGiven,
                                onCheckedChange = viewModel::setConsent
                            )
                            Text(stringResource(R.string.gdpr_consent))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.gdpr_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_tools),
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.tool_node_status),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.NODE_STATUS) }
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.tool_temp_mail),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.TEMP_MAIL) }
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.tool_security_center),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.SECURITY) }
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.tool_esp32_config),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.ESP32_CONFIG) }
                        )
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.section_foreground_service), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.startForegroundService() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_start)) }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.stopForegroundService() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_stop)) }
                        }
                        Text(
                            stringResource(R.string.foreground_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_data_export),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.createBackup() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_backup)) }
                            androidx.compose.material3.Button(
                                onClick = { viewModel.exportCsv() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_csv)) }
                            androidx.compose.material3.Button(
                                onClick = { viewModel.exportPdf() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_pdf)) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportDetectionsCsv() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_detections_csv)) }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportEncryptedCsv() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_csv_encrypted)) }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.restoreBackup() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_restore)) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.backups_available, viewModel.listBackups()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.offline_maps_url, viewModel.getOfflineMapUrl()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Status message
            state.statusMessage?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.startsWith("✅")) Color(0x1A4CAF50) else Color(0x1AF44336)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.TextButton(onClick = { viewModel.clearStatus() }) {
                                Text("✕")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.footer_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

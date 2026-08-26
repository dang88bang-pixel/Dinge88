package com.secureguard.enterprise.presentation.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.R
import com.secureguard.enterprise.presentation.theme.TerminalBg
import com.secureguard.enterprise.presentation.theme.TerminalCyan
import com.secureguard.enterprise.presentation.theme.TerminalGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    navController: NavController,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val logEntries by viewModel.logEntries.collectAsState()
    val agentStatus by viewModel.agentStatus.collectAsState()
    val listState = rememberLazyListState()
    var command by remember { mutableStateOf("") }

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⟩_", fontFamily = FontFamily.Monospace, color = TerminalGreen, fontSize = 18.sp)
                        Text(" DinGelinG Console", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalBg,
                    titleContentColor = TerminalGreen
                )
            )
        },
        containerColor = TerminalBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBg)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Agent: ${if (agentStatus.running) "AKTIV" else "INAKTIV"} | Cycle: ${agentStatus.cycle}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (agentStatus.running) TerminalGreen else TerminalCyan
                )
                Text(
                    "${agentStatus.detectionsThisCycle} Detections",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalCyan
                )
            }

            // Terminal output
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logEntries) { entry ->
                    Text(
                        text = entry.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = entry.color,
                        lineHeight = 14.sp
                    )
                }
            }

            // Command input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "root@dingeling:~#",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalGreen
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TerminalGreen,
                        unfocusedTextColor = TerminalGreen,
                        cursorColor = TerminalGreen,
                        focusedBorderColor = TerminalGreen,
                        unfocusedBorderColor = TerminalCyan.copy(alpha = 0.3f)
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                IconButton(
                    onClick = {
                        if (command.isNotBlank()) {
                            viewModel.executeCommand(command)
                            command = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = stringResource(R.string.cd_send), tint = TerminalGreen)
                }
            }
        }
    }
}

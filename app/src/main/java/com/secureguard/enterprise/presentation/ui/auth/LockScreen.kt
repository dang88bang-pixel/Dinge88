package com.secureguard.enterprise.presentation.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.R

/**
 * Sperrbildschirm (PIN-Eingabe). Wird von der MainActivity angezeigt,
 * solange [com.secureguard.enterprise.services.AuthManager.state] gelockt ist.
 */
@Composable
fun LockScreen(
    attemptsRemaining: Int,
    onUnlock: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lock_app_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.lock_app_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text(stringResource(R.string.label_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (onUnlock(pin)) {
                    pin = ""
                    error = null
                } else {
                    pin = ""
                    error = if (attemptsRemaining > 1) {
                        stringResource(R.string.wrong_pin_attempts, attemptsRemaining)
                    } else {
                        stringResource(R.string.wrong_pin_locked)
                    }
                }
            }
        ) {
            Text(stringResource(R.string.btn_unlock))
        }
    }
}

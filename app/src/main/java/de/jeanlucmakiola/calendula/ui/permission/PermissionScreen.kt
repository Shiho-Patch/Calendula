package de.jeanlucmakiola.calendula.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R

@Composable
fun PermissionScreen(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.onGranted() else viewModel.onDenied()
    }

    LaunchedEffect(state) {
        if (state == PermissionUiState.Granted) onGranted()
    }

    when (state) {
        is PermissionUiState.Rationale -> RationaleContent(
            onRequest = { launcher.launch(Manifest.permission.READ_CALENDAR) },
            modifier = modifier,
        )
        is PermissionUiState.Denied -> DeniedContent(
            onRetry = {
                viewModel.onRetry()
                launcher.launch(Manifest.permission.READ_CALENDAR)
            },
            modifier = modifier,
        )
        is PermissionUiState.Granted -> {
            // Transient — LaunchedEffect above fires and parent replaces us.
        }
    }
}

@Composable
private fun RationaleContent(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_rationale_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_rationale_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_request_button))
        }
    }
}

@Composable
private fun DeniedContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_denied_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_denied_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.permission_retry_button))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        ) {
            Text(stringResource(R.string.permission_open_settings_button))
        }
    }
}

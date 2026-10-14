package io.github.vahiru.tailscaled.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.data.TsPrefs
import io.github.vahiru.tailscaled.ui.LoginState
import io.github.vahiru.tailscaled.ui.copyToClipboard

private const val DEFAULT_CONTROL = "https://controlplane.tailscale.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    login: LoginState,
    prefs: TsPrefs?,
    snackbar: SnackbarHostState,
    onStart: (controlUrl: String, hostname: String, authKey: String?) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var useKey by rememberSaveable { mutableStateOf(false) }
    var controlUrl by rememberSaveable {
        mutableStateOf(prefs?.controlUrl?.takeIf { it.isNotBlank() && it != DEFAULT_CONTROL }.orEmpty())
    }
    var hostname by rememberSaveable { mutableStateOf(prefs?.hostname.orEmpty()) }
    var authKey by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var openedUrl by rememberSaveable { mutableStateOf("") }

    fun openUrl(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            ctx.copyToClipboard("URL", url)
        }
    }

    // Open the browser automatically the first time a login URL appears.
    LaunchedEffect(login.authUrl) {
        if (login.authUrl.isNotBlank() && login.authUrl != openedUrl) {
            openedUrl = login.authUrl
            openUrl(login.authUrl)
        }
    }
    LaunchedEffect(login.success) { if (login.success) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.login_intro), style = MaterialTheme.typography.bodyMedium)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !useKey,
                    onClick = { useKey = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    enabled = !login.running,
                    icon = { SegmentedButtonDefaults.Icon(!useKey) { Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp)) } },
                ) { Text(stringResource(R.string.login_browser)) }
                SegmentedButton(
                    selected = useKey,
                    onClick = { useKey = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    enabled = !login.running,
                    icon = { SegmentedButtonDefaults.Icon(useKey) { Icon(Icons.Outlined.Key, null, Modifier.size(18.dp)) } },
                ) { Text(stringResource(R.string.login_auth_key)) }
            }

            if (useKey) {
                OutlinedTextField(
                    value = authKey,
                    onValueChange = { authKey = it.trim() },
                    label = { Text(stringResource(R.string.auth_key)) },
                    placeholder = { Text("tskey-auth-…") },
                    singleLine = true,
                    enabled = !login.running,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    },
                    supportingText = { Text(stringResource(R.string.auth_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = controlUrl,
                onValueChange = { controlUrl = it.trim() },
                label = { Text(stringResource(R.string.control_server)) },
                placeholder = { Text(DEFAULT_CONTROL) },
                singleLine = true,
                enabled = !login.running,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = { Text(stringResource(R.string.control_server_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it.filter { c -> c.isLetterOrDigit() || c == '-' } },
                label = { Text(stringResource(R.string.device_name)) },
                singleLine = true,
                enabled = !login.running,
                supportingText = { Text(stringResource(R.string.device_name_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (login.running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.size(16.dp))
                    Text(
                        stringResource(if (login.authUrl.isBlank()) R.string.login_waiting_url else R.string.login_waiting_auth),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            } else {
                Button(
                    onClick = { onStart(controlUrl, hostname, if (useKey) authKey else null) },
                    enabled = !useKey || authKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (useKey) R.string.login else R.string.login_get_link))
                }
            }

            if (login.authUrl.isNotBlank() && login.running) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.login_open_url), style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(login.authUrl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { openUrl(login.authUrl) }) {
                                Icon(Icons.Outlined.OpenInBrowser, null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.open_browser))
                            }
                            OutlinedButton(onClick = { ctx.copyToClipboard("URL", login.authUrl) }) {
                                Icon(Icons.Outlined.ContentCopy, null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.copy))
                            }
                        }
                    }
                }
            }

            if (login.error.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(login.error, modifier = Modifier.padding(16.dp))
                }
            }

            if (login.output.isNotEmpty()) {
                Text(
                    login.output.takeLast(12).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

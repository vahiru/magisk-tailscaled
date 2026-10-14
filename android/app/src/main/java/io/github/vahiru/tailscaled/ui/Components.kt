package io.github.vahiru.tailscaled.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vahiru.tailscaled.R
import java.time.Instant

fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(R.string.copied, text), Toast.LENGTH_SHORT).show()
    }
}

fun UiMessage.resolve(ctx: Context): String = when (this) {
    is UiMessage.Res -> if (arg != null) ctx.getString(id, arg) else ctx.getString(id)
    is UiMessage.Text -> text
}

fun relativeTime(instant: Instant?): String? =
    instant?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString() }

fun humanBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%.2f GB".format(bytes / 1073741824.0)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    val color = if (online) Color(0xFF2E9E4F) else MaterialTheme.colorScheme.outline
    Box(modifier.size(10.dp).background(color, CircleShape))
}

/** A label / value row with an optional copy button. */
@Composable
fun InfoRow(label: String, value: String, copyable: Boolean = false, icon: ImageVector? = null) {
    val ctx = LocalContext.current
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = {
            Text(value.ifBlank { "—" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = if (copyable && value.isNotBlank()) {
            {
                IconButton(onClick = { ctx.copyToClipboard(label, value) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
                }
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

/** Monospace output of a command (ping, logs, module installation). */
@Composable
fun TaskDialog(task: TaskLog, onDismiss: () -> Unit, extraAction: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = { if (!task.running) onDismiss() },
        title = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (task.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 420.dp),
                ) {
                    val vScroll = rememberScrollState(Int.MAX_VALUE)
                    SelectionContainer {
                        Text(
                            task.lines.joinToString("\n").ifBlank { "…" },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .verticalScroll(vScroll)
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
                task.exitCode?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (it == 0) stringResource(R.string.task_done) else stringResource(R.string.task_failed, it),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (it == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!task.running) extraAction?.invoke()
                TextButton(onClick = onDismiss, enabled = !task.running) { Text(stringResource(R.string.close)) }
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun RowSpacer() = Spacer(Modifier.width(8.dp))

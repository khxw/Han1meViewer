package com.yenaly.han1meviewer.ui.screen.settings.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.HA1_GITHUB_FORUM_URL
import com.yenaly.han1meviewer.HA1_GITHUB_ISSUE_URL
import com.yenaly.han1meviewer.HA1_GITHUB_URL
import com.yenaly.han1meviewer.R
import com.yenaly.yenaly_libs.utils.browse

private const val MORSE_CODE = ".---- ....- ....- ..... ....- ..... ....-"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showMorseCodeDialog by remember { mutableStateOf(false) }

    if (showMorseCodeDialog) {
        MorseCodeDialog(
            morseCode = MORSE_CODE,
            onDismiss = { showMorseCodeDialog = false },
        )
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .heightIn(max = 640.dp),
        properties = DialogProperties(),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AboutHeader(
                        onVersionClick = {
                            versionTapCount++
                            if (versionTapCount >= 7) {
                                versionTapCount = 0
                                showMorseCodeDialog = true
                            }
                        },
                    )

                    AboutSection(title = stringResource(R.string.about)) {
                        Text(
                            text = stringResource(R.string.about_remastered_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinkRow(stringResource(R.string.about_repo)) {
                            context.browse(HA1_GITHUB_URL)
                        }
                        LinkRow(stringResource(R.string.submit_bug)) {
                            context.browse(HA1_GITHUB_ISSUE_URL)
                        }
                        LinkRow(stringResource(R.string.forum)) {
                            context.browse(HA1_GITHUB_FORUM_URL)
                        }
                    }

                    AboutSection(title = stringResource(R.string.credits)) {
                        Text(
                            text = stringResource(R.string.credits_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinkRow(stringResource(R.string.credits_upstream_repo)) {
                            context.browse("https://github.com/misaka10032w/Han1meViewer")
                        }
                        LinkRow(stringResource(R.string.credits_original_repo)) {
                            context.browse("https://github.com/YenalyLiew/Han1meViewer")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.close),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AboutHeader(
    onVersionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.launcher_preview_new),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(65.dp),
            )
Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.hanime_app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onVersionClick)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun LinkRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MorseCodeDialog(
    morseCode: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("...") },
        text = {
            SelectionContainer {
                Text(
                    text = morseCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText("Morse code", morseCode)
                    )
                    Toast.makeText(
                        context,
                        R.string.copy_to_clipboard,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            ) {
                Text(stringResource(R.string.morse_copy))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
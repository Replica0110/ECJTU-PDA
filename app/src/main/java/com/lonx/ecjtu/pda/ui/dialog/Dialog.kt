package com.lonx.ecjtu.pda.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A reusable container for building various types of AlertDialogs.
 * It provides the basic structure (Dialog, Card, optional Title, Button Row)
 * and allows injecting custom content.
 *
 * @param showDialog Controls whether the dialog is shown.
 * @param onDismissRequest Called when the user tries to dismiss the dialog (clicking outside, back press).
 * @param modifier Modifier applied to the Card container within the dialog.
 * @param properties Properties for the underlying Dialog window (e.g., dismissOnClickOutside).
 * @param title Optional String title for the dialog.
 * @param titleAlign Text alignment for the title.
 * @param confirmButtonText Text for the positive action button (e.g., "OK", "Save", "Confirm").
 * @param onConfirm Callback invoked when the positive action button is clicked. If null, the button is not shown.
 * @param dismissButtonText Text for the negative action button (e.g., "Cancel", "Dismiss").
 * @param onDismissAction Callback invoked when the negative action button is clicked. If null, the button is not shown. Usually, this should also trigger `onDismissRequest`.
 * @param isLoading If true, shows a loading indicator instead of buttons and disables buttons.
 * @param content The main content of the dialog, placed within a ColumnScope.
 */
@Composable
fun AlertDialogContainer(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    title: String? = null,
    titleAlign: TextAlign = TextAlign.Center,
    confirmButtonText: String = "确认",
    onConfirm: (() -> Unit)? = null,
    maxWidth: Dp = 400.dp,
    minWidth: Dp = 220.dp,
    dismissButtonText: String = "取消",
    onDismissAction: (() -> Unit)? = null,
    isLoading: Boolean = false,
    showButtons: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (showDialog) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            Card(
                modifier = modifier
                    .padding(16.dp)
                    .widthIn(min = minWidth, max = maxWidth)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MiuixTheme.textStyles.title3,
                            textAlign = titleAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
                    }

                    this.content()

                    if (showButtons) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            } else {
                                if (onDismissAction != null) {
                                    TextButton(
                                        text = dismissButtonText,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onDismissAction()
                                        },
                                        enabled = !isLoading
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (onDismissAction != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (onConfirm != null) {
                                    TextButton(
                                        text = confirmButtonText,
                                        onClick = { onConfirm() },
                                        colors = ButtonDefaults.textButtonColorsPrimary(),
                                        modifier = Modifier.weight(1f),
                                        enabled = !isLoading
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun InfoAlertDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: AnnotatedString,
    confirmButtonText: String = "确认",
    messageStyle: TextStyle = MiuixTheme.textStyles.main,
    showButton: Boolean = true
) {
    AlertDialogContainer(
        modifier = modifier,
        showDialog = showDialog,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmButtonText = confirmButtonText,
        onConfirm = if (showButton) { { onDismissRequest() } } else null,
        onDismissAction = null,
        showButtons = showButton,
        content = {
            Text(
                text = message,
                style = messageStyle,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    )
}

@Composable
fun ConfirmAlertDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消"
) {
    AlertDialogContainer(
        modifier = modifier,
        showDialog = showDialog,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmButtonText = confirmButtonText,
        onConfirm = onConfirm,
        dismissButtonText = dismissButtonText,
        onDismissAction = { onDismissRequest() }
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/**
 * A dialog with a title, a single input field, Confirm and Cancel buttons.
 */
@Composable
fun InputAlertDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    label: String = "输入",
    initialValue: String = "",
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var inputText by remember(showDialog) { mutableStateOf(initialValue) }

    AlertDialogContainer(
        modifier = modifier,
        showDialog = showDialog,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmButtonText = confirmButtonText,
        onConfirm = { onConfirm(inputText) },
        dismissButtonText = dismissButtonText,
        onDismissAction = { onDismissRequest() }
    ) {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = label,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}
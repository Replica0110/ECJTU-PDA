package com.lonx.ecjtu.pda.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme // Assuming MiuixTheme for styling

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
    dismissButtonText: String = "取消",
    onDismissAction: (() -> Unit)? = null,
    isLoading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (showDialog) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            Card(
                modifier = modifier.padding(16.dp),
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
                                        onDismissRequest() },
                                    enabled = !isLoading
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (onDismissAction != null){
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (onConfirm != null) {
                                TextButton(
                                    text = confirmButtonText,
                                    onClick = { onConfirm()
                                        onDismissRequest() },
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
@Composable
fun InfoAlertDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String,
    confirmButtonText: String = "确认"
) {
    AlertDialogContainer(
        modifier = modifier,
        showDialog = showDialog,
        onDismissRequest = onDismissRequest,
        title = title,
        confirmButtonText = confirmButtonText,
        onConfirm = { onDismissRequest() },
        onDismissAction = null
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
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
    label: String = "输入",
    initialValue: String = "",
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var inputText by remember(showDialog) { mutableStateOf(initialValue) }

    AlertDialogContainer(
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
package com.makay.cleaner.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

@Composable
fun AppUpdateHost(
    openFromNotification: Boolean = false,
    autoDownload: Boolean = false,
    promptToken: Long = 0L
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val fragmentActivity = context.findActivity() as? FragmentActivity
    val viewModel: AppUpdateViewModel = if (fragmentActivity != null) {
        viewModel(viewModelStoreOwner = fragmentActivity, factory = AppUpdateViewModel.Factory(app))
    } else {
        viewModel(factory = AppUpdateViewModel.Factory(app))
    }
    val state by viewModel.state.collectAsState()
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.checkOnLaunch()
    }
    LaunchedEffect(promptToken, openFromNotification, autoDownload) {
        if (promptToken == 0L && !openFromNotification && !autoDownload) return@LaunchedEffect
        viewModel.handleNotificationOpen(autoDownload = autoDownload, activity = activity)
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                state.needsInstallPermission &&
                activity != null
            ) {
                viewModel.retryInstallAfterPermission(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.dialogVisible && state.available != null) {
        val update = state.available!!
        AlertDialog(
            onDismissRequest = {
                if (!state.downloading) viewModel.dismissDialog(postpone = true)
            },
            title = { Text("Yeni sürüm: ${update.versionName}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Uygulamayı güncellemek ister misiniz?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Güncelleme yalnızca resmi GitHub Releases’ten indirilir. " +
                            "Kurulumdan önce paket adı, imza ve (varsa) SHA-256 doğrulanır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (update.releaseNotes.isNotBlank()) {
                        Text(
                            update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.downloading) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (state.progress >= 100) "Bütünlük kontrol ediliyor…"
                            else "%${state.progress} indiriliyor…"
                        )
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activity != null) viewModel.startDownloadAndInstall(activity)
                        else viewModel.reportNoActivity()
                    },
                    enabled = !state.downloading
                ) {
                    Text(if (state.downloading) "İndiriliyor…" else "Güncelle")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDialog(postpone = true) },
                    enabled = !state.downloading
                ) { Text("Ertele") }
            }
        )
    }
}

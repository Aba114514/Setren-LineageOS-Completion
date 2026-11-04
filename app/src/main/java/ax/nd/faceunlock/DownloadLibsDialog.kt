package ax.nd.faceunlock

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

class DownloadLibsDialog(private val activity: MainActivity, private val viewModel: ChooseLibsViewModel) {
    fun open() {
        var dialog: AlertDialog? = null

        activity.lifecycleScope.launch {
            viewModel.downloadStatus
                .combine(LibManager.librariesData) { status, libraries -> status to libraries }
                .transformWhile { pair ->
                    emit(pair)
                    pair.second.any { !it.valid }
                }
                .flowWithLifecycle(activity.lifecycle)
                .collect { (status, libraries) ->
                    dialog?.dismiss()
                    dialog = when {
                        libraries.all { it.valid } -> {
                            activity.checkAndAskForPermissions()
                            null
                        }
                        status == null -> showImportPrompt()
                        status is DownloadStatus.Importing -> showImportProgress()
                        status is DownloadStatus.ImportError -> showImportError(status)
                        else -> null
                    }
                }
        }
    }

    private fun showImportPrompt(): AlertDialog =
        MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
            .setTitle(R.string.libs_required_title)
            .setMessage(R.string.libs_required_message)
            .setPositiveButton(R.string.libs_choose_apk, null)
            .setNegativeButton(R.string.libs_open_apkpure, null)
            .setCancelable(false)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        activity.browseForFiles()
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        activity.openLibraryDownloadPage()
                    }
                }
                dialog.show()
            }

    private fun showImportProgress(): AlertDialog =
        MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
            .setTitle(R.string.libs_required_title)
            .setView(R.layout.dialog_import_progress)
            .setCancelable(false)
            .create()
            .also { it.show() }

    private fun showImportError(status: DownloadStatus.ImportError): AlertDialog =
        MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
            .setTitle(R.string.libs_import_error_title)
            .setMessage(
                activity.getString(
                    R.string.libs_import_error_message,
                    status.error.localizedMessage ?: status.error.javaClass.simpleName
                )
            )
            .setPositiveButton(R.string.libs_try_again, null)
            .setNegativeButton(R.string.libs_open_apkpure, null)
            .setCancelable(false)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        viewModel.clearDownloadResult()
                        activity.browseForFiles()
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        activity.openLibraryDownloadPage()
                    }
                }
                dialog.show()
            }
}

package ax.nd.faceunlock

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.codec.binary.Hex
import java.io.*
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipInputStream
import kotlin.Exception

sealed interface CheckResult {
    val library: RequiredLib

    data class BadHash(override val library: RequiredLib, val tmpFile: File) : CheckResult
    data class FileError(override val library: RequiredLib, val error: Exception) : CheckResult
}

sealed interface DownloadStatus {
    object Importing : DownloadStatus
    data class ImportError(val error: Exception) : DownloadStatus
}

class ChooseLibsViewModel : ViewModel() {
    val checkingStatus = MutableStateFlow(false)
    val checkResult = MutableStateFlow<CheckResult?>(null)
    val downloadStatus = MutableStateFlow<DownloadStatus?>(null)
    fun importLibs(context: Context, uri: Uri) {
        if(downloadStatus.value !is DownloadStatus.Importing) {
            downloadStatus.value = DownloadStatus.Importing
            viewModelScope.launch {
                try {
                    importLibsInternal(context, uri)
                } catch (e: Exception) {
                    downloadStatus.value = DownloadStatus.ImportError(e)
                }
            }
        }
    }

    private fun openImportUri(context: Context, uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri) ?: run {
            throw NullPointerException("ContentProvider crashed!")
        }
    }

    private suspend fun importLibsInternal(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val inputStream = openImportUri(context, uri)
            inputStream.buffered().use { resp ->
                val zin = ZipInputStream(resp)
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val name = entry.name
                    if (!name.startsWith("lib/arm64-v8a/")) {
                        continue
                    }
                    val fname = name.substringAfterLast('/')
                    val lib = LibManager.requiredLibraries.find { it.name == fname } ?: continue

                    val digest = try {
                        MessageDigest.getInstance(LibManager.HASH_TYPE)
                    } catch (e: NoSuchAlgorithmException) {
                        throw IOException("Missing hash type: ${LibManager.HASH_TYPE}", e)
                    }

                    val outFile = LibManager.getLibFile(context, lib, temp = true)
                    outFile.parentFile?.mkdirs()
                    DigestOutputStream(outFile.outputStream().buffered(), digest).use { ostream ->
                        zin.copyTo(ostream)
                    }

                    val hash = digest.digest()
                    val hex = Hex.encodeHexString(hash, true)

                    val targetHash = lib.hashForCurrentAbi() ?: run {
                        throw UnsupportedOperationException("This app cannot run on your device: unsupported ABI!")
                    }

                    if(hex == targetHash) {
                        val realFile = LibManager.getLibFile(context, lib)
                        realFile.parentFile?.mkdirs()
                        if(!outFile.renameTo(realFile)) {
                            throw IOException("Failed to rename temp file!")
                        }
                        LibManager.updateLibraryData(context)
                        if(LibManager.libsLoaded.get()) {
                            break
                        }
                    } else {
                        throw IOException("Hash mismatch, maybe the download got corrupted?")
                        }
                    }
                }
                LibManager.updateLibraryData(context)
                val missingLibraries = LibManager.librariesData.value
                    .filterNot { it.valid }
                    .joinToString { it.library.name }
                if (missingLibraries.isNotEmpty()) {
                    throw IOException(
                        "Required ARM64 libraries were not found or did not match: $missingLibraries"
                    )
                }
            }
        }

    fun addLib(context: Context, library: RequiredLib, uri: Uri) {
        if (!checkingStatus.value && checkResult.value == null) {
            checkingStatus.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    addLibInternal(context, library, uri)
                } finally {
                    checkingStatus.value = false
                }
            }
        }
    }

    fun saveBadHashLib(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = checkResult.value
            if (!checkingStatus.value && result is CheckResult.BadHash) {
                if(!result.tmpFile.renameTo(LibManager.getLibFile(context, result.library))) {
                    val exception = IOException("Failed to save library file!")
                    checkResult.value = CheckResult.FileError(result.library, exception)
                    Log.e(TAG, "Failed to save library file!", exception)
                    return@launch
                }
                // Valid, update library data
                LibManager.updateLibraryData(context)
            }
            clearCheckResult()
        }
    }

    fun clearDownloadResult() {
        if(downloadStatus.value !is DownloadStatus.Importing) {
            downloadStatus.value = null
        }
    }

    fun clearCheckResult() {
        checkResult.value = null
    }

    private fun addLibInternal(context: Context, library: RequiredLib, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri) ?: run {
                val exception = NullPointerException("ContentProvider crashed!")
                checkResult.value = CheckResult.FileError(library, exception)
                Log.e(TAG, "Failed to open input stream!", exception)
                return
            }
        } catch (e: FileNotFoundException) {
            checkResult.value = CheckResult.FileError(library, e)
            Log.e(TAG, "Content provider reported file not found!", e)
            return
        }.use { inputStream ->
            val targetFile = LibManager.getLibFile(context, library, temp = true)
            targetFile.parentFile?.mkdirs()

            val digest = try {
                MessageDigest.getInstance(LibManager.HASH_TYPE)
            } catch (e: NoSuchAlgorithmException) {
                checkResult.value = CheckResult.FileError(library, e)
                Log.e(TAG, "Missing hash type: ${LibManager.HASH_TYPE}", e)
                return
            }
            val wrappedInputStream = DigestInputStream(inputStream, digest)
            try {
                targetFile.outputStream().use { outputStream ->
                    wrappedInputStream.copyTo(outputStream)
                }
            } catch(e: IOException) {
                checkResult.value = CheckResult.FileError(library, e)
                Log.e(TAG, "Failed to write library to: $targetFile!", e)
                return
            }

            val hash = digest.digest()
            val hex = Hex.encodeHexString(hash, true)

            val targetHash = library.hashForCurrentAbi() ?: run {
                val exception = UnsupportedOperationException("This app cannot run on your device: unsupported ABI!")
                checkResult.value = CheckResult.FileError(library, exception)
                Log.e(TAG, "App cannot run on device, ABI not supported!", exception)
                return
            }
            if(hex == targetHash) {
                if(!targetFile.renameTo(LibManager.getLibFile(context, library))) {
                    val exception = IOException("Failed to save library file!")
                    checkResult.value = CheckResult.FileError(library, exception)
                    Log.e(TAG, "Failed to save library file!", exception)
                    return
                }
                // Valid, update library data
            } else {
                checkResult.value = CheckResult.BadHash(library, tmpFile = targetFile)
            }
        }
    }

    companion object {
        private val TAG = ChooseLibsViewModel::class.simpleName

    }
}

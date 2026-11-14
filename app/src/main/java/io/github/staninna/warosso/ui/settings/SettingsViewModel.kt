package io.github.staninna.warosso.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.staninna.warosso.data.ComplexRepository
import io.github.staninna.warosso.data.Documents
import io.github.staninna.warosso.data.ExportCodec
import io.github.staninna.warosso.data.ImportException
import io.github.staninna.warosso.data.Settings
import io.github.staninna.warosso.data.SettingsRepository
import io.github.staninna.warosso.domain.Setup
import io.github.staninna.warosso.sensors.PositionSimulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.OffsetDateTime

/** What an export or import ended in, for the screen to tell the user once. */
sealed interface TransferResult {
    data object Exported : TransferResult
    data object Imported : TransferResult
    data object FileError : TransferResult
    data class Refused(val reason: ImportException) : TransferResult
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val complex: ComplexRepository,
    private val documents: Documents,
    /** The debug menu's simulator, in debug builds. */
    val simulator: PositionSimulator?,
) : ViewModel() {
    val settings: StateFlow<Settings?> = settingsRepository.settings

    /** A file that was read and understood, waiting for the user to agree to replace their setup. */
    val pendingImport = MutableStateFlow<Setup?>(null)
    val result = MutableStateFlow<TransferResult?>(null)

    fun update(change: (Settings) -> Settings) = settingsRepository.update(change)

    fun export(uri: Uri) {
        val setup = complex.data.value?.setup ?: return
        viewModelScope.launch {
            result.value = try {
                documents.write(uri, ExportCodec.encode(setup, OffsetDateTime.now()))
                TransferResult.Exported
            } catch (_: IOException) {
                TransferResult.FileError
            }
        }
    }

    fun openImport(uri: Uri) {
        viewModelScope.launch {
            try {
                pendingImport.value = ExportCodec.decode(documents.read(uri))
            } catch (e: ImportException) {
                result.value = TransferResult.Refused(e)
            } catch (_: IOException) {
                result.value = TransferResult.FileError
            }
        }
    }

    fun confirmImport() {
        val setup = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            complex.replaceSetup(setup)
            result.value = TransferResult.Imported
        }
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    fun clearResult() {
        result.value = null
    }
}

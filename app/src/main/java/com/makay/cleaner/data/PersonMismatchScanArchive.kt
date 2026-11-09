package com.makay.cleaner.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.makay.cleaner.scanner.PersonMismatchScanner
import com.makay.cleaner.util.SoftErrorLogger
import java.io.File

/**
 * Son «Klasörde farklı kişi» taramasını diskte tutar.
 * Geri navigasyon / süreç ölümü sonrası sonuçlar kaybolmaz.
 */
class PersonMismatchScanArchive(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    data class Snapshot(
        val savedAt: Long = 0L,
        val bucketName: String = "",
        val threshold: Float = PersonMismatchScanner.DEFAULT_MATCH_THRESHOLD,
        val referenceUris: List<String> = emptyList(),
        val candidates: List<CandidateDto> = emptyList(),
        val completed: Boolean = true,
        val scannedCount: Int = 0,
        val totalCount: Int = 0
    )

    data class CandidateDto(
        val path: String = "",
        val uri: String = "",
        val displayName: String = "",
        val sizeBytes: Long = 0L,
        val bestSimilarity: Float = 0f,
        val faceCount: Int = 0,
        val reason: String = ""
    )

    fun load(): Snapshot? {
        return try {
            val f = file
            if (!f.isFile || f.length() == 0L) return null
            val raw = f.readText()
            gson.fromJson(raw, Snapshot::class.java)?.takeIf { it.bucketName.isNotBlank() }
        } catch (e: Exception) {
            SoftErrorLogger.warn(TAG, "Arşiv okunamadı", e)
            null
        }
    }

    fun save(snapshot: Snapshot) {
        try {
            file.writeText(gson.toJson(snapshot))
        } catch (e: Exception) {
            SoftErrorLogger.warn(TAG, "Arşiv yazılamadı", e)
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun toCandidates(snapshot: Snapshot): List<PersonMismatchScanner.MismatchCandidate> {
        return snapshot.candidates.mapNotNull { dto ->
            val uri = runCatching { Uri.parse(dto.uri) }.getOrNull() ?: return@mapNotNull null
            if (dto.path.isBlank() && dto.uri.isBlank()) return@mapNotNull null
            PersonMismatchScanner.MismatchCandidate(
                path = dto.path.ifBlank { dto.uri },
                uri = uri,
                displayName = dto.displayName.ifBlank { "foto" },
                sizeBytes = dto.sizeBytes,
                bestSimilarity = dto.bestSimilarity,
                faceCount = dto.faceCount,
                reason = dto.reason
            )
        }
    }

    fun fromCandidates(
        bucketName: String,
        threshold: Float,
        referenceUris: List<Uri>,
        candidates: List<PersonMismatchScanner.MismatchCandidate>,
        completed: Boolean,
        scannedCount: Int = candidates.size,
        totalCount: Int = candidates.size
    ): Snapshot {
        return Snapshot(
            savedAt = System.currentTimeMillis(),
            bucketName = bucketName,
            threshold = threshold,
            referenceUris = referenceUris.map { it.toString() },
            candidates = candidates.map {
                CandidateDto(
                    path = it.path,
                    uri = it.uri.toString(),
                    displayName = it.displayName,
                    sizeBytes = it.sizeBytes,
                    bestSimilarity = it.bestSimilarity,
                    faceCount = it.faceCount,
                    reason = it.reason
                )
            },
            completed = completed,
            scannedCount = scannedCount,
            totalCount = totalCount
        )
    }

    companion object {
        private const val TAG = "PersonMismatchArchive"
        private const val FILE_NAME = "person_mismatch_last_scan.json"
    }
}

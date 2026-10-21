package com.makay.cleaner.scanner

/**
 * Duplicate Photos Fixer Pro tarzı Selection Assistant:
 * kuralların önceliğine göre grupta hangisinin tutulacağını seçer.
 */
object SelectionAssistant {

    enum class Rule(val id: String, val label: String, val detail: String) {
        RESOLUTION(
            "resolution",
            "Çözünürlük",
            "Daha düşük çözünürlüklü kopyalar işaretlenir"
        ),
        FILE_SIZE(
            "size",
            "Dosya boyutu",
            "Daha küçük dosyalar işaretlenir"
        ),
        CAPTURE_DATE(
            "date",
            "Çekim tarihi",
            "Daha eski kareler işaretlenir"
        ),
        METADATA(
            "metadata",
            "Metadata",
            "EXIF’siz kopyalar işaretlenir"
        ),
        FOLDER(
            "folder",
            "Klasör önceliği",
            "Düşük öncelikli klasördeki kopyalar işaretlenir"
        )
    }

    val defaultOrder: List<Rule> = listOf(
        Rule.RESOLUTION,
        Rule.FILE_SIZE,
        Rule.CAPTURE_DATE,
        Rule.METADATA,
        Rule.FOLDER
    )

    /**
     * Grupta tutulacak tek yolu ve silinecek yolları hesaplar.
     * Kilitli ve korumalı klasördeki fotoğraflar işaretlenmez.
     */
    fun markForDeletion(
        photos: List<DuplicatePhotoScanner.PhotoItem>,
        rules: List<Rule> = defaultOrder,
        protectedBuckets: Set<String> = emptySet(),
        lockedPaths: Set<String> = emptySet(),
        folderPriority: List<String> = emptyList()
    ): MarkResult {
        if (photos.size <= 1) {
            return MarkResult(keepPath = photos.firstOrNull()?.path, deletePaths = emptySet())
        }
        val eligible = photos.filter {
            it.path !in lockedPaths &&
                it.bucketName !in protectedBuckets
        }
        if (eligible.isEmpty()) {
            return MarkResult(keepPath = photos.first().path, deletePaths = emptySet())
        }
        val orderedRules = rules.ifEmpty { defaultOrder }
        val keep = eligible.maxWithOrNull(comparator(orderedRules, folderPriority)) ?: eligible.first()
        val delete = photos
            .asSequence()
            .filter { it.path != keep.path }
            .filter { it.path !in lockedPaths }
            .filter { it.bucketName !in protectedBuckets }
            .map { it.path }
            .toSet()
        return MarkResult(keepPath = keep.path, deletePaths = delete)
    }

    fun autoMarkAll(
        groups: List<DuplicatePhotoScanner.PhotoGroup>,
        rules: List<Rule>,
        protectedBuckets: Set<String>,
        lockedPaths: Set<String>,
        folderPriority: List<String>
    ): Set<String> {
        val out = mutableSetOf<String>()
        groups.forEach { g ->
            out += markForDeletion(
                g.photos, rules, protectedBuckets, lockedPaths, folderPriority
            ).deletePaths
        }
        return out
    }

    fun parseRules(csv: String?): List<Rule> {
        if (csv.isNullOrBlank()) return defaultOrder
        val byId = Rule.entries.associateBy { it.id }
        val parsed = csv.split(',').mapNotNull { byId[it.trim()] }
        val missing = defaultOrder.filter { it !in parsed }
        return (parsed + missing).distinct()
    }

    fun serializeRules(rules: List<Rule>): String =
        rules.joinToString(",") { it.id }

    private fun comparator(
        rules: List<Rule>,
        folderPriority: List<String>
    ): Comparator<DuplicatePhotoScanner.PhotoItem> {
        return Comparator { a, b ->
            for (rule in rules) {
                val cmp = when (rule) {
                    Rule.RESOLUTION -> a.pixelCount.compareTo(b.pixelCount)
                    Rule.FILE_SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                    Rule.CAPTURE_DATE -> {
                        val ta = a.dateTaken.takeIf { it > 0 } ?: a.dateAdded
                        val tb = b.dateTaken.takeIf { it > 0 } ?: b.dateAdded
                        ta.compareTo(tb)
                    }
                    Rule.METADATA -> a.hasMetadata.compareTo(b.hasMetadata)
                    Rule.FOLDER -> {
                        val ia = folderRank(a.bucketName, folderPriority)
                        val ib = folderRank(b.bucketName, folderPriority)
                        // Düşük indeks = yüksek öncelik (tutulur)
                        ib.compareTo(ia)
                    }
                }
                if (cmp != 0) return@Comparator cmp
            }
            a.dateAdded.compareTo(b.dateAdded)
        }
    }

    private fun folderRank(bucket: String, priority: List<String>): Int {
        if (priority.isEmpty()) return 0
        val idx = priority.indexOfFirst { it.equals(bucket, ignoreCase = true) }
        return if (idx >= 0) idx else priority.size
    }

    data class MarkResult(
        val keepPath: String?,
        val deletePaths: Set<String>
    )
}

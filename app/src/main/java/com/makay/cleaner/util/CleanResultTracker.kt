package com.makay.cleaner.util

/**
 * Son temizlik özeti — UI'da CleanSummaryDialog için.
 */
object CleanResultTracker {
    data class Summary(
        val category: String,
        val filesDeleted: Int,
        val spaceSaved: Long,
        val protectedSkipped: Int,
        val movedToTrash: Boolean
    )

    @Volatile
    var last: Summary? = null
        private set

    fun record(
        category: String,
        filesDeleted: Int,
        spaceSaved: Long,
        protectedSkipped: Int = SystemFileGuard.consumeSkippedCount(),
        movedToTrash: Boolean = true
    ) {
        last = Summary(category, filesDeleted, spaceSaved, protectedSkipped, movedToTrash)
    }

    fun clear() {
        last = null
    }
}

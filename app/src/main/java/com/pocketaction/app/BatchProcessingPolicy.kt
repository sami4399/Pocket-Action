package com.pocketaction.app

/**
 * Central policy for the maximum number of images accepted by a batch run.
 * Keeping the cap here makes picker results and shared-input paths consistent.
 */
object BatchProcessingPolicy {
    const val MAX_IMAGES = 10

    fun <T> limit(items: List<T>): List<T> = items.take(MAX_IMAGES)
}

package com.pocketaction.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchProcessingPolicyTest {
    @Test
    fun acceptsUpToTenItems() {
        val items = (1..10).toList()
        assertEquals(items, BatchProcessingPolicy.limit(items))
    }

    @Test
    fun truncatesItemsAboveTen() {
        val items = (1..15).toList()
        assertEquals((1..10).toList(), BatchProcessingPolicy.limit(items))
    }

    @Test
    fun acceptsEmptyInput() {
        assertTrue(BatchProcessingPolicy.limit(emptyList<Int>()).isEmpty())
    }

    @Test
    fun doesNotMutateCallerList() {
        val items = (1..12).toMutableList()
        val original = items.toList()
        BatchProcessingPolicy.limit(items)
        assertEquals(original, items)
    }
}

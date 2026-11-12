package tech.vauldex.dekita.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tech.vauldex.dekita.data.mock.SeedStoreIds
import tech.vauldex.dekita.data.mock.seedStores
import tech.vauldex.dekita.domain.BusinessType
import tech.vauldex.dekita.domain.StoreBadge

/** The text both apps show, so Android and iOS stay in step. */
class LabelsTest {

    private fun store(id: String) = seedStores.first { it.id == id }

    @Test
    fun queueLabelCoversEachState() {
        assertEquals("3 ahead · ~8 min", store(SeedStoreIds.RAMEN_BAR).queueLabel())
        assertEquals("No wait", store(SeedStoreIds.MART).queueLabel())
        assertEquals("Not accepting orders", store(SeedStoreIds.SOBA_HOUSE).queueLabel())
        assertEquals("18 ahead · not taking new orders", store(SeedStoreIds.BENTO_KITCHEN).queueLabel())
    }

    @Test
    fun queueDetailLabelSpellsOutTheQueue() {
        assertEquals("3 orders ahead · ~8 min wait", store(SeedStoreIds.RAMEN_BAR).queueDetailLabel())
        assertEquals("No wait", store(SeedStoreIds.MART).queueDetailLabel())
        assertEquals("Not accepting orders", store(SeedStoreIds.SOBA_HOUSE).queueDetailLabel())
        assertEquals("18 orders ahead · not taking new orders", store(SeedStoreIds.BENTO_KITCHEN).queueDetailLabel())
        assertEquals("", store(SeedStoreIds.RAMEN_BAR).copy(queue = null).queueDetailLabel())
    }

    @Test
    fun shareTextNamesTheStoreOnly() {
        assertEquals("Test Ramen Bar · Ramen · 4 min walk", store(SeedStoreIds.RAMEN_BAR).shareText())
    }

    @Test
    fun badgeLabelsMatchTheTicket() {
        assertEquals("Open for Lunch", StoreBadge.OPEN_FOR_LUNCH.label)
        assertEquals("Pickup Only", StoreBadge.PICKUP_ONLY.label)
        assertEquals("In-store Only", StoreBadge.IN_STORE_ONLY.label)
        assertEquals("Paused", StoreBadge.PAUSED.label)
    }

    @Test
    fun onlyStoresWithBothModesSayWhichModes() {
        assertEquals("Eat-In & Takeout", BusinessType.BOTH.modesLabel)
        assertNull(BusinessType.PICKUP_ONLY.modesLabel)
        assertNull(BusinessType.IN_STORE_ONLY.modesLabel)
    }
}

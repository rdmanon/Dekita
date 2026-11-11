package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueStatusTest {

    @Test
    fun isHighFromTheThresholdUp() {
        assertFalse(QueueStatus(ordersAhead = 0, estimatedWaitMinutes = 0).isHigh)
        assertFalse(QueueStatus(ordersAhead = HIGH_QUEUE_THRESHOLD - 1, estimatedWaitMinutes = 20).isHigh)
        assertTrue(QueueStatus(ordersAhead = HIGH_QUEUE_THRESHOLD, estimatedWaitMinutes = 25).isHigh)
        assertTrue(QueueStatus(ordersAhead = 999, estimatedWaitMinutes = 999).isHigh)
    }
}

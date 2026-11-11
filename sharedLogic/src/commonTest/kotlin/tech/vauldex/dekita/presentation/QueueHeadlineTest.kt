package tech.vauldex.dekita.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import tech.vauldex.dekita.domain.QueueStatus

class QueueHeadlineTest {

    @Test
    fun headlineCoversEmptySingularAndLargeQueues() {
        assertEquals("No wait", QueueStatus(0, 0).headline())
        assertEquals("1 order ahead", QueueStatus(1, 25).headline())
        assertEquals("3 orders ahead", QueueStatus(3, 8).headline())
        assertEquals("999 orders ahead", QueueStatus(999, 999).headline())
    }
}

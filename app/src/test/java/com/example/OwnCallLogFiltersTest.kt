package com.example

import com.example.data.repository.CrmRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnCallLogFiltersTest {
    @Test fun buildsCallerFilterWithDateFloor() {
        assertEquals(
            """[["caller","=","rep@onebook.app"],["creation",">=","2026-10-18"]]""",
            CrmRepository.ownCallLogFilters("caller", "rep@onebook.app", "2026-10-18")
        )
    }

    @Test fun receiverSideUsesReceiverField() {
        assertEquals(
            """[["receiver","=","rep@onebook.app"],["creation",">=","2026-10-18"]]""",
            CrmRepository.ownCallLogFilters("receiver", "rep@onebook.app", "2026-10-18")
        )
    }

    @Test fun escapesQuotesAndBackslashesSoTheJsonStaysValid() {
        assertEquals(
            """[["caller","=","a\"b\\c"],["creation",">=","2026-10-18"]]""",
            CrmRepository.ownCallLogFilters("caller", "a\"b\\c", "2026-10-18")
        )
    }
}

package com.gurbakir.storefront

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HomeContentContractIdTest {
    @Test
    fun `only the two approved Home tuples resolve`() {
        assertEquals(
            HomeContentContractId.GATE7_V1,
            HomeContentContractId.fromTuple("mobile_home", 1, "gate7-v1")
        )
        assertEquals(
            HomeContentContractId.PILOT_MEDIA_V2,
            HomeContentContractId.fromTuple("mobile_home_v2", 2, "pilot-media-v2")
        )
    }

    @Test
    fun `mixed unknown and case changed tuples fail closed`() {
        listOf(
            Triple("mobile_home", 2, "gate7-v1"),
            Triple("mobile_home_v2", 1, "pilot-media-v2"),
            Triple("mobile_home", 1, "pilot-media-v2"),
            Triple("mobile_home_v2", 2, "PILOT-MEDIA-V2"),
            Triple("foreign_home", 9, "foreign")
        ).forEach { (rootType, version, contract) ->
            val failure =
                assertThrows<IllegalArgumentException> {
                    HomeContentContractId.fromTuple(rootType, version, contract)
                }
            assertEquals("HOME_CONTRACT_MISMATCH", failure.message)
        }
    }
}

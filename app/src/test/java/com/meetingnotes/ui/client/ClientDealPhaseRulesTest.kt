package com.meetingnotes.ui.client

import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientDealPhaseRulesTest {

    private fun p(id: Long, phase: DealPhase?, clientId: Long = 1) = ClientProjectEntity(
        id = id, clientId = clientId, name = "P$id", createdAt = 0L, phase = phase?.wireValue
    )

    @Test fun `no projects returns null`() {
        assertNull(ClientDealPhaseRules.of(emptyList()))
    }

    @Test fun `single active project returns its phase`() {
        assertEquals(DealPhase.PROPOSAL, ClientDealPhaseRules.of(listOf(p(1, DealPhase.PROPOSAL))))
    }

    @Test fun `multiple active projects returns the most advanced`() {
        val phase = ClientDealPhaseRules.of(
            listOf(p(1, DealPhase.HEARING), p(2, DealPhase.QUOTED), p(3, DealPhase.PROPOSAL))
        )
        assertEquals(DealPhase.QUOTED, phase)
    }

    @Test fun `on_hold ranks below the pipeline stages`() {
        val phase = ClientDealPhaseRules.of(listOf(p(1, DealPhase.ON_HOLD), p(2, DealPhase.HEARING)))
        assertEquals(DealPhase.HEARING, phase)
    }

    @Test fun `won when no active and a won project exists`() {
        val phase = ClientDealPhaseRules.of(listOf(p(1, DealPhase.WON), p(2, DealPhase.LOST)))
        assertEquals(DealPhase.WON, phase)
    }

    @Test fun `lost when only lost projects`() {
        assertEquals(DealPhase.LOST, ClientDealPhaseRules.of(listOf(p(1, DealPhase.LOST))))
    }

    @Test fun `projects without a phase are ignored`() {
        assertNull(ClientDealPhaseRules.of(listOf(p(1, null), p(2, null))))
    }

    @Test fun `byClient groups per client`() {
        val map = ClientDealPhaseRules.byClient(
            listOf(
                p(1, DealPhase.PROPOSAL, clientId = 10),
                p(2, DealPhase.QUOTED, clientId = 10),
                p(3, DealPhase.WON, clientId = 20),
                p(4, null, clientId = 30)
            )
        )
        assertEquals(DealPhase.QUOTED, map[10])
        assertEquals(DealPhase.WON, map[20])
        assertNull(map[30])
    }
}

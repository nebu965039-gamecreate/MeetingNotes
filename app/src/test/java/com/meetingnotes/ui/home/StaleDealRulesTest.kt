package com.meetingnotes.ui.home

import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleDealRulesTest {

    private val now = 1_757_000_000_000L
    private val day = 86_400_000L

    private fun client(id: Long) = ClientEntity(id = id, name = "C$id", createdAt = 0L)

    private fun project(
        id: Long,
        clientId: Long = 1,
        phase: DealPhase?,
        phaseChangedDaysAgo: Long
    ) = ClientProjectEntity(
        id = id,
        clientId = clientId,
        name = "P$id",
        createdAt = now - phaseChangedDaysAgo * day,
        phase = phase?.wireValue,
        phaseChangedAt = now - phaseChangedDaysAgo * day
    )

    @Test
    fun `active project past threshold is stale`() {
        val out = StaleDealRules.compute(
            listOf(project(1, phase = DealPhase.PROPOSAL, phaseChangedDaysAgo = 30)),
            listOf(client(1)),
            nowMillis = now
        )
        assertEquals(listOf(1L), out.map { it.projectId })
        assertEquals(30, out[0].daysSincePhaseChange)
        assertEquals("C1", out[0].clientName)
    }

    @Test
    fun `recently advanced project is not stale`() {
        val out = StaleDealRules.compute(
            listOf(project(1, phase = DealPhase.PROPOSAL, phaseChangedDaysAgo = 5)),
            listOf(client(1)),
            nowMillis = now
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `won lost and phaseless projects are excluded`() {
        val out = StaleDealRules.compute(
            listOf(
                project(1, phase = DealPhase.WON, phaseChangedDaysAgo = 90),
                project(2, phase = DealPhase.LOST, phaseChangedDaysAgo = 90),
                project(3, phase = null, phaseChangedDaysAgo = 90)
            ),
            listOf(client(1)),
            nowMillis = now
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `sorted by days since phase change descending`() {
        val out = StaleDealRules.compute(
            listOf(
                project(1, phase = DealPhase.HEARING, phaseChangedDaysAgo = 25),
                project(2, phase = DealPhase.QUOTED, phaseChangedDaysAgo = 60),
                project(3, phase = DealPhase.PROPOSAL, phaseChangedDaysAgo = 40)
            ),
            listOf(client(1)),
            nowMillis = now
        )
        assertEquals(listOf(2L, 3L, 1L), out.map { it.projectId })
    }

    @Test
    fun `falls back to createdAt when phaseChangedAt is null`() {
        val p = ClientProjectEntity(
            id = 1, clientId = 1, name = "P1",
            createdAt = now - 40 * day, phase = DealPhase.PROPOSAL.wireValue, phaseChangedAt = null
        )
        val out = StaleDealRules.compute(listOf(p), listOf(client(1)), nowMillis = now)
        assertEquals(40, out.single().daysSincePhaseChange)
    }
}

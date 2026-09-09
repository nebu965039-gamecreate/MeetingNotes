package com.meetingnotes.ui.sales

import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/** `SalesRules.report` は案件(`client_projects`)を通貨ごとの売上集計にする純粋関数。 */
class SalesRulesTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = YearMonth.of(2026, 9)

    private fun millisOf(year: Int, month: Int, day: Int = 15): Long =
        java.time.LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun project(
        id: Long,
        phase: DealPhase?,
        currency: String = "JPY",
        estimated: Long? = null,
        won: Long? = null,
        wonAt: Long? = null,
        lostReason: String? = null
    ) = ClientProjectEntity(
        id = id,
        clientId = 1,
        name = "P$id",
        createdAt = 0L,
        phase = phase?.wireValue,
        currency = currency,
        estimatedAmount = estimated,
        wonAmount = won,
        wonAt = wonAt,
        lostReason = lostReason
    )

    @Test
    fun `empty projects yields empty report`() {
        assertTrue(SalesRules.report(emptyList(), SalesPeriod.ALL, now, zone).isEmpty())
    }

    @Test
    fun `currency without won or lost is excluded`() {
        val reports = SalesRules.report(
            listOf(project(1, DealPhase.PROPOSAL, currency = "USD", estimated = 100)),
            SalesPeriod.ALL, now, zone
        )
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `this-month period only counts wins with wonAt in current month`() {
        val projects = listOf(
            project(1, DealPhase.WON, won = 300, wonAt = millisOf(2026, 9)),
            project(2, DealPhase.WON, won = 500, wonAt = millisOf(2026, 8))
        )
        val report = SalesRules.report(projects, SalesPeriod.THIS_MONTH, now, zone).single()
        assertEquals(300L, report.wonAmount)
        assertEquals(1, report.wonCount)
        // 全期間の分子は期間フィルタに関係なく両方
        assertEquals(800L, report.wonAmountAllTime)
        assertEquals(2, report.wonCountAllTime)
    }

    @Test
    fun `this-year period counts all wins in the year`() {
        val projects = listOf(
            project(1, DealPhase.WON, won = 300, wonAt = millisOf(2026, 9)),
            project(2, DealPhase.WON, won = 500, wonAt = millisOf(2026, 2)),
            project(3, DealPhase.WON, won = 999, wonAt = millisOf(2025, 12))
        )
        val report = SalesRules.report(projects, SalesPeriod.THIS_YEAR, now, zone).single()
        assertEquals(800L, report.wonAmount)
        assertEquals(2, report.wonCount)
    }

    @Test
    fun `monthly series has 12 buckets ending at now and is period-independent`() {
        val report = SalesRules.report(
            listOf(project(1, DealPhase.WON, won = 400, wonAt = millisOf(2026, 7))),
            SalesPeriod.THIS_MONTH, now, zone
        ).single()
        assertEquals(12, report.monthlyWon.size)
        assertEquals(now, report.monthlyWon.last().month)
        assertEquals(YearMonth.of(2025, 10), report.monthlyWon.first().month)
        assertEquals(400L, report.monthlyWon.first { it.month == YearMonth.of(2026, 7) }.amount)
    }

    @Test
    fun `win rates use amount and count over all time`() {
        val projects = listOf(
            project(1, DealPhase.WON, won = 700, wonAt = millisOf(2026, 9)),
            project(2, DealPhase.WON, won = 300, wonAt = millisOf(2026, 9)),
            project(3, DealPhase.LOST, estimated = 1000)
        )
        val report = SalesRules.report(projects, SalesPeriod.ALL, now, zone).single()
        // 金額: 1000 / (1000 + 1000) = 50%
        assertEquals(50, report.winRateByAmount)
        // 件数: 2 / 3 = 66%
        assertEquals(66, report.winRateByCount)
    }

    @Test
    fun `win rate is null when there is no denominator`() {
        val report = SalesRules.report(
            listOf(project(1, DealPhase.LOST)),
            SalesPeriod.ALL, now, zone
        ).single()
        assertNull(report.winRateByAmount)
        assertEquals(0, report.winRateByCount)
    }

    @Test
    fun `pipeline groups active projects by phase descending`() {
        val projects = listOf(
            project(1, DealPhase.WON, won = 1, wonAt = millisOf(2026, 9)),
            project(2, DealPhase.PROPOSAL, estimated = 200),
            project(3, DealPhase.PROPOSAL, estimated = 300),
            project(4, DealPhase.HEARING, estimated = 100),
            project(5, DealPhase.LOST, estimated = 999)
        )
        val report = SalesRules.report(projects, SalesPeriod.ALL, now, zone).single()
        assertEquals(listOf(DealPhase.PROPOSAL, DealPhase.HEARING), report.pipelineByPhase.map { it.phase })
        assertEquals(500L, report.pipelineByPhase.first().amount)
        assertEquals(600L, report.pipelineTotal)
    }

    @Test
    fun `separate reports per currency sorted by code`() {
        val projects = listOf(
            project(1, DealPhase.WON, currency = "USD", won = 10, wonAt = millisOf(2026, 9)),
            project(2, DealPhase.WON, currency = "JPY", won = 20, wonAt = millisOf(2026, 9))
        )
        val reports = SalesRules.report(projects, SalesPeriod.ALL, now, zone)
        assertEquals(listOf("JPY", "USD"), reports.map { it.currencyCode })
    }

    @Test
    fun `lost reason breakdown counts and sorts by frequency`() {
        val projects = listOf(
            project(1, DealPhase.LOST, lostReason = "価格"),
            project(2, DealPhase.LOST, lostReason = "価格"),
            project(3, DealPhase.LOST, lostReason = "タイミング"),
            project(4, DealPhase.LOST, lostReason = "  "),
            project(5, DealPhase.LOST, lostReason = null)
        )
        val report = SalesRules.report(projects, SalesPeriod.ALL, now, zone).single()
        assertEquals(
            listOf("価格" to 2, "タイミング" to 1),
            report.lostReasonBreakdown.map { it.reason to it.count }
        )
    }

    @Test
    fun `average deal size ignores wins without an amount`() {
        val projects = listOf(
            project(1, DealPhase.WON, won = 400, wonAt = millisOf(2026, 9)),
            project(2, DealPhase.WON, won = 200, wonAt = millisOf(2026, 9)),
            project(3, DealPhase.WON, won = null, wonAt = millisOf(2026, 9))
        )
        val report = SalesRules.report(projects, SalesPeriod.ALL, now, zone).single()
        assertEquals(300L, report.avgDealSize)
        assertEquals(3, report.wonCount)
        assertEquals(600L, report.wonAmount)
    }
}

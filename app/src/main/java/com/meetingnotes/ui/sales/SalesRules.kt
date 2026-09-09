package com.meetingnotes.ui.sales

import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 売上ビューの期間フィルタ。成約額合計・平均単価・月次グラフのハイライトに効く。 */
enum class SalesPeriod(val label: String) {
    THIS_MONTH("今月"),
    THIS_YEAR("今年"),
    ALL("全期間")
}

data class MonthlyAmount(val month: YearMonth, val amount: Long)
data class PhaseAmount(val phase: DealPhase, val amount: Long)
data class ReasonCount(val reason: String, val count: Int)
data class PhaseDays(val phase: DealPhase, val days: Int)

/**
 * 1通貨ぶんの売上集計。
 *
 * 期間フィルタ([SalesPeriod])が効くのは [wonAmount] / [wonCount] / [avgDealSize]。
 * [monthlyWon] は常に直近12ヶ月、成約率とパイプラインは常に全期間・現在状態で見る
 * (1人利用では月次の成約率はぶれが大きいため)。
 */
data class SalesCurrencyReport(
    val currencyCode: String,
    /** 直近12ヶ月の月次成約額(古い順・12要素固定。該当なしは 0)。期間フィルタ非依存。 */
    val monthlyWon: List<MonthlyAmount>,
    /** 期間内の成約額合計(金額未入力の案件は 0 扱い)。 */
    val wonAmount: Long,
    /** 期間内の成約件数(金額未入力も含む)。 */
    val wonCount: Int,
    /** 全期間の成約額合計(成約率の分子)。 */
    val wonAmountAllTime: Long,
    /** 全期間の成約件数(成約率の分子)。 */
    val wonCountAllTime: Int,
    /** 全期間の失注件数。 */
    val lostCount: Int,
    /** 全期間の失注案件の見積額合計(金額ベース成約率の分母)。 */
    val lostAmount: Long,
    /** 進行中案件のフェーズ別 見積額合計(見積額のあるもの・降順)。 */
    val pipelineByPhase: List<PhaseAmount>,
    /** 売上予測 = Σ(見積額 × 受注確度)。確度は案件の probability、未入力ならフェーズ既定値。 */
    val weightedPipeline: Long,
    /** 平均成約単価(金額のある成約案件のみ)。0件なら null。 */
    val avgDealSize: Long?,
    /** 失注理由の内訳(理由が入力された失注案件のみ・件数降順)。 */
    val lostReasonBreakdown: List<ReasonCount> = emptyList(),
    /** 平均セールスサイクル日数(案件作成 → 成約 の日数、成約案件のみ)。0件なら null。 */
    val avgCycleDays: Int? = null,
    /** 進行中案件のフェーズ別 平均滞留日数(現在 − フェーズ最終変更日)・降順。 */
    val avgDaysInPhase: List<PhaseDays> = emptyList()
) {
    /** 金額ベース成約率(%): 成約額 / (成約額 + 失注見積額)。母数0なら null。 */
    val winRateByAmount: Int?
        get() = (wonAmountAllTime + lostAmount).takeIf { it > 0 }
            ?.let { ((wonAmountAllTime * 100) / it).toInt() }

    /** 件数ベース成約率(%): 成約数 / (成約数 + 失注数)。母数0なら null。 */
    val winRateByCount: Int?
        get() = (wonCountAllTime + lostCount).takeIf { it > 0 }
            ?.let { (wonCountAllTime * 100) / it }

    val pipelineTotal: Long get() = pipelineByPhase.sumOf { it.amount }
}

object SalesRules {

    private const val DAY_MS = 86_400_000L

    private fun monthOf(millis: Long, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    /**
     * 案件一覧を通貨ごとの [SalesCurrencyReport] に集計する。通貨コード昇順。
     * 成約・失注がどちらも無い通貨は結果に含めない。
     */
    fun report(
        projects: List<ClientProjectEntity>,
        period: SalesPeriod,
        now: YearMonth = YearMonth.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<SalesCurrencyReport> {
        if (projects.isEmpty()) return emptyList()

        return projects.map { it.currency }.distinct().sorted().mapNotNull { code ->
            val forCurrency = projects.filter { it.currency == code }
            val won = forCurrency.filter { DealPhase.fromWire(it.phase) == DealPhase.WON }
            val lost = forCurrency.filter { DealPhase.fromWire(it.phase) == DealPhase.LOST }
            if (won.isEmpty() && lost.isEmpty()) return@mapNotNull null

            fun inPeriod(wonAt: Long?): Boolean = when (period) {
                SalesPeriod.ALL -> true
                else -> {
                    val m = wonAt?.let { monthOf(it, zone) } ?: return@inPeriod false
                    if (period == SalesPeriod.THIS_MONTH) m == now else m.year == now.year
                }
            }

            val wonInPeriod = won.filter { inPeriod(it.wonAt) }
            val wonAmountsInPeriod = wonInPeriod.mapNotNull { it.wonAmount }

            val monthly = (0 until 12).map { idx ->
                val m = now.minusMonths((11 - idx).toLong())
                val sum = won
                    .filter { it.wonAt != null && monthOf(it.wonAt, zone) == m }
                    .sumOf { it.wonAmount ?: 0L }
                MonthlyAmount(m, sum)
            }

            val active = forCurrency
                .filter { DealPhase.fromWire(it.phase)?.isActive == true && it.estimatedAmount != null }
            val pipeline = active
                .groupBy { DealPhase.fromWire(it.phase)!! }
                .map { (phase, list) -> PhaseAmount(phase, list.sumOf { it.estimatedAmount ?: 0L }) }
                .sortedByDescending { it.amount }
            val weighted = active.sumOf { p ->
                val prob = p.probability ?: DealPhase.fromWire(p.phase)?.defaultProbability ?: 0
                (p.estimatedAmount ?: 0L) * prob / 100
            }

            val cycleDays = won
                .filter { it.wonAt != null }
                .map { ((it.wonAt!! - it.createdAt) / DAY_MS).toInt().coerceAtLeast(0) }
            val phaseAges = forCurrency
                .filter { DealPhase.fromWire(it.phase)?.isActive == true }
                .groupBy { DealPhase.fromWire(it.phase)!! }
                .map { (phase, list) ->
                    val ages = list.map {
                        ((nowMillis - (it.phaseChangedAt ?: it.createdAt)) / DAY_MS).toInt().coerceAtLeast(0)
                    }
                    PhaseDays(phase, ages.sum() / ages.size)
                }
                .sortedByDescending { it.days }

            SalesCurrencyReport(
                currencyCode = code,
                monthlyWon = monthly,
                wonAmount = wonAmountsInPeriod.sum(),
                wonCount = wonInPeriod.size,
                wonAmountAllTime = won.sumOf { it.wonAmount ?: 0L },
                wonCountAllTime = won.size,
                lostCount = lost.size,
                lostAmount = lost.sumOf { it.estimatedAmount ?: 0L },
                pipelineByPhase = pipeline,
                weightedPipeline = weighted,
                avgDealSize = wonAmountsInPeriod.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
                lostReasonBreakdown = lost
                    .mapNotNull { it.lostReason?.trim()?.takeIf(String::isNotEmpty) }
                    .groupingBy { it }
                    .eachCount()
                    .map { (reason, count) -> ReasonCount(reason, count) }
                    .sortedByDescending { it.count },
                avgCycleDays = cycleDays.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
                avgDaysInPhase = phaseAges
            )
        }
    }
}

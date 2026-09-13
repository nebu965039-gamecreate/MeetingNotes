package com.meetingnotes.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val WheelItemHeight = 44.dp
private const val WHEEL_VISIBLE_COUNT = 5 // 奇数。中央の1行が選択値。

/**
 * ドラムロール(スピナー)式の時刻選択。時・分をそれぞれ縦スクロールのホイールで選ぶ。
 * 「全ての時刻入力をドラムロール式にしてほしい」というフィードバックを受け、アプリ内の時刻入力
 * (次回打ち合わせ・ToDoの期限日時/通知日時・予定・案件の成約日など、すべて共通の
 * `NextMeetingDateTimeDialog` 経由)をこれに統一した(2026-09-20。旧: M3標準のダイヤル式/
 * キーボード入力をアイコンで切り替える方式)。
 */
@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(value = hour, range = 0..23, onValueChange = onHourChange)
        Text(
            ":",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        WheelColumn(value = minute, range = 0..59, onValueChange = onMinuteChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    val edgePadding = WheelItemHeight * (WHEEL_VISIBLE_COUNT / 2)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = value - range.first)
    val flingBehavior = rememberSnapFlingBehavior(listState)

    // スクロール(フリング含む)が止まったら、中央に来ている行を選択値として通知する。
    // contentPadding で上下を(表示行数/2)ぶん空けているため、firstVisibleItemIndex が
    // そのまま「中央に表示されているアイテムのindex」になる(スナップにより offset は 0 に揃う)。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val centerIndex = listState.firstVisibleItemIndex.coerceIn(0, range.last - range.first)
                onValueChange(range.first + centerIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(72.dp)
            .height(WheelItemHeight * WHEEL_VISIBLE_COUNT),
        contentAlignment = Alignment.Center
    ) {
        // 中央の選択行を示すハイライト帯。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = edgePadding),
            modifier = Modifier.fillMaxSize()
        ) {
            items(range.count()) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "%02d".format(range.first + index),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

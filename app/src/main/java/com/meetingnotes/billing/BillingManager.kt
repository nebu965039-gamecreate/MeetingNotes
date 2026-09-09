package com.meetingnotes.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play Billing(サブスクリプション「Pro」)のクライアント。
 *
 * - 商品は1サブスク `meetingnotes_pro`(現状は月額ベースプランのみ)。
 * - 検証はクライアント側のみ(`queryPurchasesAsync` + acknowledge)。サーバー検証は将来タスク。
 * - 購入状態は [isPro] で公開し、`MeetingNotesApp` が `ProAccess` に流し込む。
 * - Play 未対応・未接続・商品未登録でも落ちない(その場合 [isPro] は false のまま)。
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _monthlyProduct = MutableStateFlow<ProductDetails?>(null)
    /** 月額プランの `ProductDetails`。ペイウォールの価格表示・購入フローに使う。 */
    val monthlyProduct: StateFlow<ProductDetails?> = _monthlyProduct.asStateFlow()

    private var reconnectAttempts = 0

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** アプリ起動時に1回呼ぶ。 */
    fun start() {
        if (client.connectionState == BillingClient.ConnectionState.CONNECTED ||
            client.connectionState == BillingClient.ConnectionState.CONNECTING
        ) return
        connect()
    }

    private fun connect() {
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        reconnectAttempts = 0
                        queryProduct()
                        refreshPurchases()
                    } else {
                        Log.w(TAG, "billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (reconnectAttempts < 3) {
                        reconnectAttempts++
                        connect()
                    }
                }
            })
        }.onFailure { Log.w(TAG, "billing connect error", it) }
    }

    /** 起動時・画面復帰時に呼び、Play 側の購入状態と同期する。 */
    fun refreshPurchases() {
        if (client.connectionState != BillingClient.ConnectionState.CONNECTED) {
            start()
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            var active = false
            for (p in purchases) {
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.contains(PRODUCT_ID)) {
                    active = true
                    acknowledgeIfNeeded(p)
                }
            }
            _isPro.value = active
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _monthlyProduct.value = productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            } else {
                Log.w(TAG, "queryProductDetails failed: ${result.responseCode}")
            }
        }
    }

    /** ペイウォールの「登録する」から呼ぶ。商品未取得なら何もしない。 */
    fun launchPurchase(activity: Activity) {
        val product = _monthlyProduct.value ?: return
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) {
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.contains(PRODUCT_ID)) {
                    acknowledgeIfNeeded(p)
                    _isPro.value = true
                }
            }
        }
        // ユーザーキャンセル・保留などは既存状態のまま(refreshPurchases が別途整合させる)。
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.responseCode}")
            }
        }
    }

    /** 月額プランの表示価格(例: "¥980")。未取得なら null。 */
    fun monthlyPriceLabel(): String? =
        _monthlyProduct.value?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice

    companion object {
        private const val TAG = "BillingManager"

        /** Play Console のサブスクリプション商品ID。 */
        const val PRODUCT_ID = "meetingnotes_pro"
    }
}

package com.meetingnotes.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.meetingnotes.BuildConfig

/**
 * Firebase Analytics / Crashlytics の薄いラッパー(2026-09-23新設)。
 * `google-services.json` が無いビルド(`BuildConfig.FIREBASE_ENABLED = false`)では
 * 何もしない no-op として動作する(ローカル開発・json未取得の環境でクラッシュしない)。
 *
 * 商談内容・クライアント名・文字起こし等のユーザーデータは一切送らない。送るのは
 * イベント名と数値/種別程度のパラメータのみ([logEvent] の呼び出し側で担保する)。
 */
object AppAnalytics {

    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        analytics = FirebaseAnalytics.getInstance(context)
    }

    /**
     * 利用状況データ送信(Analytics/Crashlyticsとも)のON/OFF。設定画面のトグルから呼ぶ。
     * 既定はON(未設定時)。オフにすると新規のイベント送信・クラッシュレポートを止める
     * (Firebase SDK側の挙動。過去に送信済みのデータは削除されない)。
     */
    fun setCollectionEnabled(enabled: Boolean) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        analytics?.setAnalyticsCollectionEnabled(enabled)
        runCatching { FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled) }
    }

    /** 文字列/数値パラメータのみを受け付ける(個人情報・商談内容を誤って送らないための制約)。 */
    fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        val a = analytics ?: return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
            }
        }
        a.logEvent(name, bundle)
    }
}

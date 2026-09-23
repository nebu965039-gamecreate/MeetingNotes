import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase Crashlytics / Analytics(2026-09-23〜)。google-services.json は Firebase Console から
// プロジェクトごとに個別ダウンロードするローカル専用ファイル(.gitignore対象、keystore.properties と同様の
// 扱い)。この2プラグインは google-services.json の中身(アプリID等)をビルド時に読み込むため、
// ファイルが無い環境(json未取得の開発者・CI)でビルドが壊れないよう、存在するときだけ適用する。
// firebase-crashlytics/firebase-analytics の依存自体は(AppAnalytics.kt がコンパイル時に参照できるよう)
// json の有無に関わらず常に含める — Firebase SDK は未設定(google-services.xml が無い)ときは
// FirebaseApp の自動初期化を静かに諦める設計のため、json 未配置でもクラッシュはしない
// (BuildConfig.FIREBASE_ENABLED で自前コードからの呼び出しはさらに確実にガードする、下記参照)。
val hasGoogleServicesJson = file("google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// リリース署名用。keystore.properties はgitignore対象(keystore.properties.example を参照)。
// 未設定の場合、release ビルドは未署名になる(Play App Signing のアップロード鍵をここに設定する)。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")?.let {
    rootProject.file(it).exists()
} ?: false

// Room スキーマ履歴を app/schemas/ に書き出す。スキーマ変更時はこのJSONを
// コミットし、対応する Migration を data/local/Migrations.kt に追加する。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.meetingnotes"
    compileSdk = 37

    defaultConfig {
        // ストア上のアプリ識別子(公開後は変更不可)。コード上のパッケージ名(namespace)は
        // com.meetingnotes のまま。両者は異なっていてよい。
        applicationId = "com.manaapps.meetingnotes"
        minSdk = 33
        targetSdk = 37
        versionCode = 12
        versionName = "0.2.0"

        // 要約は自前の中継Worker(server/)経由で呼ぶ。アプリにAPIキーは持たない。
        // URL・トークンは秘匿情報ではないが、環境ごとに変わるので local.properties から読む。
        val summaryProxyUrl = localProperties.getProperty("SUMMARY_PROXY_URL") ?: ""
        buildConfigField("String", "SUMMARY_PROXY_URL", "\"$summaryProxyUrl\"")
        val summaryProxyAppToken = localProperties.getProperty("SUMMARY_PROXY_APP_TOKEN") ?: ""
        buildConfigField("String", "SUMMARY_PROXY_APP_TOKEN", "\"$summaryProxyAppToken\"")

        // Play Integrity(フェーズ2)。GCPプロジェクト番号を設定するとプロキシ呼び出しに
        // Integrity トークンを付ける。未設定なら何もしない(Worker側も PLAY_INTEGRITY_ENABLED=off なら不要)。
        val playIntegrityProjectNumber = localProperties.getProperty("PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER") ?: ""
        buildConfigField("String", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "\"$playIntegrityProjectNumber\"")

        // --- AdMob ---
        // 既定は Google 公式テスト広告(クローズドテスト・ローカル開発はすべてこれ)。
        // 本番/オープンテストのAABをビルドするときだけ local.properties に
        //   ADMOB_USE_PRODUCTION_ADS=true
        // を設定して本番の広告ユニットに切り替える。
        // 広告ユニットIDは秘匿情報ではない(公開APKに必ず含まれる)。
        val useProductionAds =
            (localProperties.getProperty("ADMOB_USE_PRODUCTION_ADS") ?: "false").toBoolean()
        logger.lifecycle("AdMob: ${if (useProductionAds) "本番広告ユニット" else "テスト広告ユニット"}")

        val admobAppId: String
        val admobBanner: String
        val admobInterstitial: String
        val admobRewarded: String
        val admobAppOpen: String
        if (useProductionAds) {
            admobAppId = "ca-app-pub-7474417689976149~4169817438"
            admobBanner = "ca-app-pub-7474417689976149/9542502966"
            admobInterstitial = "ca-app-pub-7474417689976149/9522321669"
            admobRewarded = "ca-app-pub-7474417689976149/1355574535"
            // App Openアド(2026-09-21追加)。AdMobコンソールでApp Open広告ユニットを作成し、
            // local.properties に ADMOB_APP_OPEN_UNIT_ID=ca-app-pub-... を設定してから本番配信すること。
            // 未設定のままだと本番ビルドでも読み込みに失敗するだけ(クラッシュはしない)。
            admobAppOpen = localProperties.getProperty("ADMOB_APP_OPEN_UNIT_ID") ?: ""
        } else {
            admobAppId = "ca-app-pub-3940256099942544~3347511713"
            admobBanner = "ca-app-pub-3940256099942544/9214589741"
            admobInterstitial = "ca-app-pub-3940256099942544/1033173712"
            admobRewarded = "ca-app-pub-3940256099942544/5224354917"
            admobAppOpen = "ca-app-pub-3940256099942544/9257395921"
        }
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBanner\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitial\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobRewarded\"")
        buildConfigField("String", "ADMOB_APP_OPEN_UNIT_ID", "\"$admobAppOpen\"")

        // 本番広告に切り替えたあとも実機テスターにテスト広告を出すための端末ID
        // (カンマ区切り、local.properties)。テスト広告のうちは不要。
        // 端末IDはアプリ起動時の logcat の
        //   "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList(\"XXXX\"))"
        // から取得する。エミュレータは登録不要。
        val admobTestDeviceIds = localProperties.getProperty("ADMOB_TEST_DEVICE_IDS") ?: ""
        buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"$admobTestDeviceIds\"")

        // --- Pro (Google Play Billing) ---
        // Pro 機能のロック表示(gating)。既定 OFF。Play Console でサブスク商品 meetingnotes_pro を
        // 登録し、ライセンステストが通ることを確認してから local.properties に
        //   PRO_GATING_ENABLED=true
        // を設定する。OFF の間は BillingManager は動くが shouldLock は常に false。
        val proGatingEnabled =
            (localProperties.getProperty("PRO_GATING_ENABLED") ?: "false").toBoolean()
        logger.lifecycle("Pro gating: ${if (proGatingEnabled) "ON" else "OFF"}")
        buildConfigField("Boolean", "PRO_GATING_ENABLED", "$proGatingEnabled")

        // クローズドテスト等で「Pro購入は一切できないが、サンプルデータ投入はテスターにも
        // 使わせたい」場合に true にする(local.properties)。true の間:
        //  - BillingManager.launchPurchase が何もしない(購入フローを起動しない、単一の関所)
        //  - ProPaywallDialog の「登録する」ボタンが「閉じる」に差し替わる
        //  - リリースビルドでも設定画面に「サンプルデータを投入」が表示される(通常は BuildConfig.DEBUG 限定)
        val betaFreeOnly =
            (localProperties.getProperty("BETA_FREE_ONLY_MODE") ?: "false").toBoolean()
        logger.lifecycle("Beta free-only mode: ${if (betaFreeOnly) "ON(購入不可・サンプルデータ投入可)" else "OFF"}")
        buildConfigField("Boolean", "BETA_FREE_ONLY", "$betaFreeOnly")

        // --- Firebase (Analytics / Crashlytics) ---
        // google-services.json 未配置のビルドでは AppAnalytics/MeetingNotesApp が Firebase の
        // API を一切呼ばないようにするフラグ。
        logger.lifecycle("Firebase: ${if (hasGoogleServicesJson) "有効" else "google-services.json 未配置のため無効"}")
        buildConfigField("Boolean", "FIREBASE_ENABLED", "$hasGoogleServicesJson")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("keystore.properties が未設定のため release ビルドは未署名になります。")
                null
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room のマイグレーションテスト(MigrationTestHelper)がスキーマJSONを読めるようにする。
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.ads)
    implementation(libs.play.integrity)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.pdfbox.android)
    implementation(libs.billing)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}

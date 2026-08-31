import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
        versionCode = 5
        versionName = "0.1.4"

        val anthropicApiKey = localProperties.getProperty("ANTHROPIC_API_KEY") ?: ""
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicApiKey\"")

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
        if (useProductionAds) {
            admobAppId = "ca-app-pub-7474417689976149~4169817438"
            admobBanner = "ca-app-pub-7474417689976149/9542502966"
            admobInterstitial = "ca-app-pub-7474417689976149/9522321669"
            admobRewarded = "ca-app-pub-7474417689976149/1355574535"
        } else {
            admobAppId = "ca-app-pub-3940256099942544~3347511713"
            admobBanner = "ca-app-pub-3940256099942544/9214589741"
            admobInterstitial = "ca-app-pub-3940256099942544/1033173712"
            admobRewarded = "ca-app-pub-3940256099942544/5224354917"
        }
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBanner\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitial\"")
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobRewarded\"")

        // 本番広告に切り替えたあとも実機テスターにテスト広告を出すための端末ID
        // (カンマ区切り、local.properties)。テスト広告のうちは不要。
        // 端末IDはアプリ起動時の logcat の
        //   "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList(\"XXXX\"))"
        // から取得する。エミュレータは登録不要。
        val admobTestDeviceIds = localProperties.getProperty("ADMOB_TEST_DEVICE_IDS") ?: ""
        buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"$admobTestDeviceIds\"")
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

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}

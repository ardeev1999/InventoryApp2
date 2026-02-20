plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.yourname.inventoryapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourname.inventoryapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        // Для совместимости с Kapt
        languageVersion = "1.9"
    }

    packaging {
        resources {
            // ★ УПРОЩАЕМ для POI ★
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/*.kotlin_module")
            
            // Только эти нужны
            merges.add("META-INF/LICENSE.md")
            merges.add("META-INF/NOTICE.md")
            merges.add("META-INF/INDEX.LIST")
            merges.add("META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {
    // Android базовые
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")
    
    // ViewModel и LiveData - ВСЕГО ОДНА ВЕРСИЯ 2.7.0
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Coroutines - ВСЕГО ОДНА ВЕРСИЯ 1.8.0
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // ★★ УПРОЩАЕМ POI - ТОЛЬКО 3 ЗАВИСИМОСТИ ★★
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
    
    // Библиотека для сканирования QR
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Тесты
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Упрощаем resolutionStrategy - убираем force если не нужно
configurations.all {
    resolutionStrategy {
        // Оставляем только если есть проблемы
        // force("org.apache.xmlbeans:xmlbeans:5.1.1")
    }
}
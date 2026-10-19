plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("org.jetbrains.kotlin.kapt")
}

android {

    namespace = "com.example.coletornotificacao"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.coletornotificacao"
        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // Exporta o esquema do Room para permitir testes de migração.
        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    buildTypes {

        release {

            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.17.0")

    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.recyclerview:recyclerview:1.4.0")

    implementation("androidx.viewpager2:viewpager2:1.1.0")

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.9.2"
    )

    implementation(
        "androidx.lifecycle:lifecycle-livedata-ktx:2.9.2"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
    )

    implementation(
        "androidx.room:room-runtime:2.7.2"
    )

    implementation(
        "androidx.room:room-ktx:2.7.2"
    )
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // WorkManager: sincronização confiável em background, exigida pelo escopo
    // (reenvio de pendentes com restrição de rede, retomada após reinício).
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    kapt(
        "androidx.room:room-compiler:2.7.2"
    )

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.room:room-testing:2.7.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")

    androidTestImplementation(
        "androidx.test.ext:junit:1.3.0"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.7.0"
    )
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.test:core:1.6.1")
}

// Testes unitários (host) que usam Robolectric/Room precisam dos recursos Android.
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Permite que chamadas a android.util.Log retornem valores padrão em
        // testes de unidade (host) em vez de lançar "not mocked".
        unitTests.isReturnDefaultValues = true
    }
    // Disponibiliza os esquemas Room exportados para o MigrationTestHelper.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}
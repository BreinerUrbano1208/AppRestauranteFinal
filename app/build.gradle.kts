plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.bremnercamilourbano.apprestaurante"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.bremnercamilourbano.apprestaurante"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


        // Dirección del servidor (la lee ProductsApi). Cámbiala solo aquí:
        //   Render (producción): https://server-app-restaurant.onrender.com/
        //   Emulador + servidor en tu PC: http://10.0.2.2:3000/
        //   Celular + servidor en tu PC: http://IP_DE_TU_PC:3000/
        buildConfigField("String", "API_BASE_URL", "\"https://server-app-restaurant.onrender.com/\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.glide)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.samanramezani.dina"
 compileSdk = 36
 defaultConfig {
  applicationId = "com.samanramezani.dina"
  minSdk = 29
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0"
  ndk { abiFilters += "arm64-v8a" }
 }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(project(":runtime-android"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui:1.7.8")
 implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
 implementation("androidx.compose.foundation:foundation:1.7.8")
 implementation("androidx.compose.material:material:1.7.8")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
 debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}

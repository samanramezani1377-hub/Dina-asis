plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.woogit.aicore.runtime.android"
 compileSdk = 36
 ndkVersion = "27.2.12479018"
 defaultConfig {
  minSdk = 29
  ndk { abiFilters += "arm64-v8a"; debugSymbolLevel = "none" }
  externalNativeBuild { cmake {
   abiFilters += "arm64-v8a"
   arguments += listOf("-DLLAMA_BUILD_COMMON=ON","-DLLAMA_BUILD_TESTS=OFF","-DLLAMA_BUILD_EXAMPLES=OFF","-DLLAMA_BUILD_TOOLS=OFF","-DLLAMA_BUILD_SERVER=OFF","-DLLAMA_BUILD_APP=OFF","-DLLAMA_BUILD_UI=OFF","-DLLAMA_OPENSSL=OFF","-DGGML_NATIVE=OFF","-DGGML_CPU_ARM_ARCH=armv8-a","-DGGML_CPU_KLEIDIAI=ON","-DGGML_OPENMP=ON","-DOpenMP_C_FLAGS=-fopenmp -static-openmp","-DOpenMP_CXX_FLAGS=-fopenmp -static-openmp","-DGGML_LLAMAFILE=OFF","-DGGML_OPENCL=ON","-DGGML_VULKAN=OFF","-DCMAKE_BUILD_TYPE=Release")
  } }
 }
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") }

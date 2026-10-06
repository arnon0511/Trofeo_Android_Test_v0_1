plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="com.tsk.carmedia"; compileSdk=35
 defaultConfig { applicationId="com.tsk.carmedia"; minSdk=30; targetSdk=35; versionCode=19; versionName="1.8.1" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }; kotlinOptions { jvmTarget="17" } }
kotlin { jvmToolchain(17) }
dependencies { implementation("androidx.core:core-ktx:1.15.0"); implementation("androidx.appcompat:appcompat:1.7.0") }
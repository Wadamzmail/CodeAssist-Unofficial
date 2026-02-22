
plugins {
    id("com.android.library")
    id("kotlin-android")
}



android {
    namespace = "wrapper.wrapper1"
    compileSdk = 36
	defaultConfig {
	 minSdk = 26
	 targetSdk = 36
	}
	
	compileOptions{
       sourceCompatibility = JavaVersion.VERSION_17
       targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
	compilerOptions {
	    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}

dependencies {
    api(project(":completion-api"))
}

plugins {
	id("com.android.library")
	id("kotlin-android")
}

android {
	namespace = "com.itsaky.androidide.lsp.kotlin"
    compileSdk = 36 
    defaultConfig {
     minSdk = 26 
     targetSdk = 36 
     
    } 
	sourceSets {
		named("main") {
			resources.srcDir(
				project(":lsp:kotlin-stdlib-generator")
					.layout.buildDirectory.dir("generated-resources/stdlib")
			)
		}
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

afterEvaluate {
	tasks.matching { it.name.startsWith("process") && it.name.endsWith("JavaRes") }.configureEach {
		dependsOn(":lsp:kotlin-stdlib-generator:generateStdlibIndex")
	}
}

dependencies {
	implementation(libs.common.lsp4j)
	implementation(libs.common.jsonrpc)
	implementation(libs.common.kotlin)
	implementation(libs.common.kotlin.coroutines.core)
	implementation(libs.common.kotlin.coroutines.android)
	implementation(project(":completion-api"))
	implementation(project(":event:eventbus-events")) 
	implementation(project(":lsp:kotlin-core")) 
	implementation(project(":lsp:api"))
	implementation(project(":project")) 
	implementation(project(":common")) 
	implementation(project(":build-logic")) 
	implementation(libs.androidx.core)
	
}

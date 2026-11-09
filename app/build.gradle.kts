plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Package the canonical M0 files, without copies drifting from the source Profile.
val profileAssets = tasks.register<Sync>("syncProfileAssets") {
    from(rootProject.file("profiles/default")) {
        into("default")
        include("*.json", "prompt.md")
    }
    from(rootProject.file("docs/m0/examples")) {
        into("examples")
        include("*.json")
    }
    into(layout.buildDirectory.dir("generated/profileAssets"))
}

android {
    namespace = "dev.dicta.prototype"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.dicta.prototype"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-m1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main").assets.srcDir(profileAssets.map { it.destinationDir })
        getByName("test").resources.srcDirs("../profiles/default", "../docs/m0/examples")
    }
    testOptions {
        unitTests.all { it.systemProperty("dicta.root", rootProject.projectDir.absolutePath) }
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}
kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
tasks.named("preBuild").configure { dependsOn(profileAssets) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.activity.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
}

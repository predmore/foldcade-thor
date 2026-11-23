plugins { id("com.android.application") }

val code = providers.gradleProperty("thorVersionCode").orElse(providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull()?.coerceAtLeast(1)?.toString() ?: "1" }).get().toInt()
check(code in 1..2_100_000_000) { "thorVersionCode must be a positive Android versionCode" }
val signingFile = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
val signingPassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
val signingAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val canSign = listOf(signingFile, signingPassword, signingAlias, signingKeyPassword).all { !it.isNullOrBlank() }
val allowUnsigned = providers.gradleProperty("thorAllowUnsigned").orNull == "true"

android {
    namespace = "app.foldcade.thor"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.foldcade.thor"
        minSdk = 33
        targetSdk = 33
        versionCode = code
        versionName = "0.1.0"
    }
    buildFeatures { buildConfig = true }
    if (canSign) signingConfigs.create("release") {
        storeFile = file(signingFile!!)
        storePassword = signingPassword
        keyAlias = signingAlias
        keyPassword = signingKeyPassword
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}

// Guard the actual release build, while allowing debug/lint configuration without secrets.
tasks.configureEach {
    if (name == "preReleaseBuild") doFirst {
        check(canSign || allowUnsigned) {
            "Release signing requires RELEASE_KEYSTORE_FILE, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD. Use -PthorAllowUnsigned=true only for a deliberate unsigned build."
        }
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }

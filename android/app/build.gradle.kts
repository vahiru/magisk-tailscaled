import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Magisk module lives in the repository root, one level above this Gradle project.
val moduleRoot: File = rootDir.parentFile
val moduleProp = Properties().apply { moduleRoot.resolve("module.prop").inputStream().use { load(it) } }
val moduleVersion: String = moduleProp.getProperty("version")
val moduleVersionCode: Int = moduleProp.getProperty("versionCode").toInt()

/**
 * Packs the Magisk module from the repository root into assets/module.zip so the app can
 * install or update it through the root manager. Binaries in tailscale/bin/ are included
 * when present (full package); otherwise the installer downloads them (lite package).
 */
abstract class BundleModuleTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleFiles: ConfigurableFileCollection

    @get:Internal
    abstract val moduleDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val root = moduleDir.get().asFile
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        ZipOutputStream(out.resolve("module.zip").outputStream().buffered()).use { zip ->
            moduleFiles.files.filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

val bundleModule = tasks.register<BundleModuleTask>("bundleModule") {
    moduleDir.set(moduleRoot)
    moduleFiles.from(
        project.fileTree(moduleRoot) {
            include(
                "module.prop", "customize.sh", "service.sh", "action.sh", "uninstall.sh", "LICENSE",
                "META-INF/**", "tailscale/**",
            )
        },
    )
}

// Robolectric's Android runtime, resolved by Gradle so tests run offline.
val robolectricRuntime: Configuration by configurations.creating
val robolectricDepsDir = layout.buildDirectory.dir("robolectric-deps")
val prepareRobolectricRuntime = tasks.register<Copy>("prepareRobolectricRuntime") {
    from(robolectricRuntime)
    into(robolectricDepsDir)
}

android {
    namespace = "io.github.vahiru.tailscaled"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.vahiru.tailscaled"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField("String", "MODULE_VERSION", "\"$moduleVersion\"")
        buildConfigField("int", "MODULE_VERSION_CODE", moduleVersionCode.toString())
    }

    signingConfigs {
        create("release") {
            // CI / local release signing via environment, falls back to the debug key.
            System.getenv("SIGNING_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("SIGNING_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "zip"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.dependsOn(prepareRobolectricRuntime)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricDepsDir.get().asFile.path)
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("roborazzi.record.filePathStrategy", "relativePathFromRoborazziContextOutputDirectory")
            it.systemProperty("roborazzi.outputDir", layout.buildDirectory.dir("outputs/roborazzi").get().asFile.path)
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleModule, BundleModuleTask::outputDir)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    // Screenshot tests: render screens on the JVM with fake state.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    robolectricRuntime("org.robolectric:android-all-instrumented:15-robolectric-13954326-i7")
}

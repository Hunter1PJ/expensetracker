import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.suguru.expensetracker"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.suguru.expensetracker"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "1.5.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("androidTest").assets.directories.add("$projectDir/schemas")
  }

  signingConfigs {
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
      FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
    }

    val storeFilePath = keystoreProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
    val storePass = keystoreProps.getProperty("storePassword") ?: System.getenv("STORE_PASSWORD")
    val alias = keystoreProps.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS") ?: "expensetracker-upload"
    val keyPass = keystoreProps.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")

    if (!storeFilePath.isNullOrBlank() && file(storeFilePath).exists() && !storePass.isNullOrBlank() && !keyPass.isNullOrBlank()) {
      create("release") {
        storeFile = file(storeFilePath)
        storePassword = storePass
        keyAlias = alias
        keyPassword = keyPass
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isDebuggable = false
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { releaseConfig ->
        signingConfig = releaseConfig
      }
    }
    debug {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)

  // Firebase Auth and Google Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.androidx.glance.appwidget)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("verifyLocalizationResources") {
  group = "verification"
  description = "Checks application string, array, plural, and format-argument parity for en, uz, and ru."
  notCompatibleWithConfigurationCache("This task parses source XML files at execution time.")

  doLast {
    val resourceRoot = file("src/main/res")
    val localeDirectories = mapOf(
      "en" to resourceRoot.resolve("values"),
      "uz" to resourceRoot.resolve("values-uz"),
      "ru" to resourceRoot.resolve("values-ru"),
    )

    fun formatArguments(value: String): List<String> =
      Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z]")
        .findAll(value.replace("%%", ""))
        .map { it.value }
        .sorted()
        .toList()

    fun resourceSignatures(directory: java.io.File): Map<String, String> {
      val xmlFiles = directory.listFiles { file -> file.isFile && file.extension == "xml" }.orEmpty()
      return buildMap {
        xmlFiles.forEach { xmlFile ->
          val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setExpandEntityReferences(false)
          }
          val document = try {
            factory.newDocumentBuilder().parse(xmlFile)
          } catch (error: Exception) {
            throw GradleException("Invalid localization XML at ${xmlFile.relativeTo(projectDir)}: ${error.message}", error)
          }
          val children = document.documentElement.childNodes
          for (index in 0 until children.length) {
            val element = children.item(index) as? org.w3c.dom.Element ?: continue
            val type = element.tagName
            if (type !in setOf("string", "string-array", "plurals")) continue
            if (element.getAttribute("translatable") == "false") continue
            val name = element.getAttribute("name")
            if (name.isBlank()) throw GradleException("Resource without a name in ${xmlFile.relativeTo(projectDir)}")
            val key = "$type/$name"
            val signature = when (type) {
              "string" -> "args=${formatArguments(element.textContent)}"
              "string-array" -> {
                val items = (0 until element.childNodes.length)
                  .mapNotNull { element.childNodes.item(it) as? org.w3c.dom.Element }
                  .filter { it.tagName == "item" }
                  .map { formatArguments(it.textContent) }
                "items=${items.size};args=$items"
              }
              else -> {
                val items = (0 until element.childNodes.length)
                  .mapNotNull { element.childNodes.item(it) as? org.w3c.dom.Element }
                  .filter { it.tagName == "item" }
                  .associate { it.getAttribute("quantity") to formatArguments(it.textContent) }
                // Locale plural rules may define additional quantities (for example Russian few/many).
                // Compare every quantity required by English while permitting valid locale-specific forms.
                val requiredItems = items.filterKeys { it in setOf("one", "other") }
                "quantities=${requiredItems.keys.sorted()};args=$requiredItems"
              }
            }
            if (put(key, signature) != null) {
              throw GradleException("Duplicate localization resource $key in ${xmlFile.relativeTo(projectDir)}")
            }
          }
        }
      }
    }

    val english = resourceSignatures(localeDirectories.getValue("en"))
    logger.lifecycle("Found ${english.size} English app string/array/plural keys.")
    val failures = mutableListOf<String>()
    for (locale in listOf("uz", "ru")) {
      val translated = resourceSignatures(localeDirectories.getValue(locale))
      (english.keys - translated.keys).sorted().forEach { key ->
        failures += "Missing $locale resource key $key"
      }
      (translated.keys - english.keys).sorted().forEach { key ->
        failures += "Unexpected $locale resource key $key"
      }
      (english.keys intersect translated.keys).sorted().forEach { key ->
        if (english.getValue(key) != translated.getValue(key)) {
          failures += "Resource structure or format arguments differ for $locale key $key: " +
            "English=${english.getValue(key)}, $locale=${translated.getValue(key)}"
        }
      }
    }
    if (failures.isNotEmpty()) {
      throw GradleException(
        "Localization resource validation failed with ${failures.size} issue(s):\n" +
          failures.joinToString("\n") { " - $it" }
      )
    }
    logger.lifecycle(
      "Localization resources are consistent: ${english.size} keys in English, Uzbek, and Russian."
    )
  }
}

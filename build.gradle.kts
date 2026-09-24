import java.util.Properties

plugins { id("com.android.application") }

val supabaseProperties = Properties()
val supabaseFile = rootProject.file("supabase.properties")
if (supabaseFile.exists()) supabaseFile.inputStream().use { supabaseProperties.load(it) }
fun supabaseProperty(name: String): String = supabaseProperties.getProperty(name, "")
fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    namespace = "com.socialnetwork.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.socialnetwork.app"
        minSdk = 21
        targetSdk = 36
        versionCode = 44
        versionName = "2.18.0"
        buildConfigField("String", "SUPABASE_URL", buildConfigString(supabaseProperty("SUPABASE_URL")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString(supabaseProperty("SUPABASE_PUBLISHABLE_KEY")))
        buildConfigField("String", "SUPABASE_DATA_READ_URL", buildConfigString(supabaseProperty("SUPABASE_DATA_READ_URL").ifBlank { supabaseProperty("SUPABASE_URL") }))
        buildConfigField("String", "SUPABASE_DATA_WRITE_URL", buildConfigString(supabaseProperty("SUPABASE_DATA_WRITE_URL").ifBlank { supabaseProperty("SUPABASE_URL") }))
        buildConfigField("String", "SUPABASE_AUTH_URL", buildConfigString(supabaseProperty("SUPABASE_AUTH_URL").ifBlank { supabaseProperty("SUPABASE_URL") }))
        buildConfigField("String", "SUPABASE_STORAGE_URL", buildConfigString(supabaseProperty("SUPABASE_STORAGE_URL").ifBlank { supabaseProperty("SUPABASE_URL") }))
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
}

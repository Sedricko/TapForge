pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        // Terceiros (OpenCV, ML Kit e o que eles puxam) vêm deste repositório, que está no projeto. Para estes grupos o Gradle
        // NÃO consulta a internet: se faltar qualquer peça em maven-repo, o build falha. Regenerar: tools/resolve-deps.py.
        exclusiveContent {
            forRepository { maven { url = uri("maven-repo") } }
            filter {
                listOf(
            "androidx.activity",
            "androidx.annotation",
            "androidx.appcompat",
            "androidx.arch.core",
            "androidx.asynclayoutinflater",
            "androidx.collection",
            "androidx.concurrent",
            "androidx.coordinatorlayout",
            "androidx.core",
            "androidx.cursoradapter",
            "androidx.customview",
            "androidx.documentfile",
            "androidx.drawerlayout",
            "androidx.emoji2",
            "androidx.exifinterface",
            "androidx.fragment",
            "androidx.interpolator",
            "androidx.legacy",
            "androidx.lifecycle",
            "androidx.loader",
            "androidx.localbroadcastmanager",
            "androidx.print",
            "androidx.resourceinspection",
            "androidx.savedstate",
            "androidx.slidingpanelayout",
            "androidx.startup",
            "androidx.swiperefreshlayout",
            "androidx.tracing",
            "androidx.vectordrawable",
            "androidx.versionedparcelable",
            "androidx.viewpager",
            "com.google.android.datatransport",
            "com.google.android.gms",
            "com.google.android.odml",
            "com.google.firebase",
            "com.google.guava",
            "com.google.mlkit",
            "javax.inject",
            "org.opencv",
                "org.sonatype.oss",
                ).forEach { includeGroup(it) }
            }
        }
        google(); mavenCentral()   // só a cadeia de build (plugin Android, aapt2 etc.)
    }
}
rootProject.name = "Macro17"
include(":app")

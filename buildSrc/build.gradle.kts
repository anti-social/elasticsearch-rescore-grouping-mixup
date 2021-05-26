import java.nio.file.Paths
import java.util.Properties

plugins {
    `kotlin-dsl`
    idea
    id("org.ajoberstar.grgit") version "4.1.0"
}

val gitDescribe = grgit.describe(mapOf("match" to listOf("v*-es*"), "tags" to true))
    ?: throw IllegalStateException("Could not find any version tag")

class GitDescribe(val describe: String) {
    private val VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+(\\-(alpha|beta|rc)\\-[0-9]+)?"

    private val matchedGroups = "v(?<plugin>${VERSION_REGEX})-es(?<es>${VERSION_REGEX})(-(?<abbrev>.*))?".toRegex()
        .matchEntire(describe)!!
        .groups

    val plugin = matchedGroups["plugin"]!!.value
    val es = matchedGroups["es"]!!.value
    val abbrev = matchedGroups["abbrev"]?.value

    fun esVersion() = if (hasProperty("esVersion")) {
        property("esVersion")
    } else {
        es
    }

    fun pluginVersion() = buildString {
        append(plugin)
        if (abbrev != null) {
            append("-$abbrev")
        }
    }

    fun projectVersion() = buildString {
        append("$plugin-es${esVersion()}")
        if (abbrev != null) {
            append("-$abbrev")
        }
    }
}
val describe = GitDescribe(gitDescribe)

val generatedResourcesDir = Paths.get(buildDir.path, "generated-resources", "main")

sourceSets {
    main {
        output.dir(mapOf("builtBy" to "generateVersionProperties"), generatedResourcesDir)
    }
}

tasks.create("generateVersionProperties") {
    outputs.dir(generatedResourcesDir)
    doLast {
        val versionProps = Properties().apply {
            put("tag", describe.describe)
            put("projectVersion", describe.projectVersion())
            put("pluginVersion", describe.pluginVersion())
            put("esVersion", describe.esVersion())
        }
        generatedResourcesDir.resolve("es-plugin-versions.properties").toFile().writer().use {
            versionProps.store(it, null)
        }
    }
}

repositories {
    mavenLocal()
    gradlePluginPortal()
}

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")
    implementation("org.elasticsearch.gradle:build-tools:${describe.esVersion()}")
}

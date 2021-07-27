import java.nio.file.Paths
import java.util.Properties

plugins {
    `kotlin-dsl`
    idea
    id("org.ajoberstar.grgit") version "4.1.0"
}

val minEsVersion = readVersion("es-minimum.version")

val gitDescribe = grgit.describe(mapOf("match" to listOf("v*-es*"), "tags" to true))
    ?: "v0.0.0-es$minEsVersion"

class GitDescribe(val describe: String) {
    private val VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+(\\-(alpha|beta|rc)\\-[0-9]+)?"

    private val matchedGroups =
        "v(?<plugin>${VERSION_REGEX})-es(?<es>${VERSION_REGEX})(-(?<abbrev>.*))?".toRegex()
            .matchEntire(describe)!!
            .groups

    val plugin = matchedGroups["plugin"]!!.value
    val es = matchedGroups["es"]!!.value
    val abbrev = matchedGroups["abbrev"]?.value

    fun esVersion() = if (hasProperty("esVersion")) {
        property("esVersion")
    } else {
        // When adopting to new Elasticsearch version
        // create `buildSrc/es.version` file so IDE can fetch correct version of Elasticsearch
        readVersion("es.version") ?: es
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
    val versionsFilePath = generatedResourcesDir.resolve("es-plugin-versions.properties")

    outputs.file(versionsFilePath)

    doLast {
        val versionProps = Properties().apply {
            put("projectVersion", describe.projectVersion())
            put("pluginVersion", describe.pluginVersion())
            put("esVersion", describe.esVersion())
        }
        versionsFilePath.toFile().writer().use {
            versionProps.store(it, null)
        }
    }
}

repositories {
    mavenCentral()
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

// Utils

fun readVersion(fileName: String): String? {
    project.projectDir.toPath().resolve(fileName).toFile().let {
        if (it.exists()) {
            val esVersion = it.readText().trim()
            if (!esVersion.startsWith('#')) {
                return esVersion
            }
            return null
        }
        return null
    }
}

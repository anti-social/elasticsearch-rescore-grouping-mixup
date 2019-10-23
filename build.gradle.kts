import java.util.Date
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.RecordingCopyTask
import org.elasticsearch.gradle.VersionProperties

buildscript {
    val esVersion = project.properties["esVersion"]
            ?: project.file("es.version")
                    .readLines()
                    .first()

    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath("org.elasticsearch.gradle:build-tools:$esVersion")
    }
}

plugins {
    java
    idea
    id("org.ajoberstar.grgit") version "4.0.0-rc.1"
    id("com.jfrog.bintray") version "1.8.4"
}

apply {
    plugin("org.ajoberstar.grgit")
    plugin("elasticsearch.esplugin")
}

val lastTag = grgit.describe(mapOf("match" to listOf("v*"), "tags" to true)) ?: "v0.0.0"

val versionParts = lastTag.split('-', limit=3)
val pluginVersion = versionParts[0].trimStart('v')

configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
    name = "rescore-grouping-mixup"
    description = "Adds rescorer for mixing up search hits inside their groups."
    classname = "company.evo.elasticsearch.plugin.GroupingMixupPlugin"
    version = pluginVersion
}

project.setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
project.setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

val versions = VersionProperties.getVersions() as Map<String, String>
project.version = "$pluginVersion-es${versions["elasticsearch"]}"

bintray {
    user = if (hasProperty("bintrayUser")) {
        property("bintrayUser").toString()
    } else {
        System.getenv("BINTRAY_USER")
    }
    key = if (hasProperty("bintrayApiKey")) {
        property("bintrayApiKey").toString()
    } else {
        System.getenv("BINTRAY_API_KEY")
    }
    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "elasticsearch"
        name = project.name
        userOrg = "evo"
        setLicenses("Apache-2.0")
        setLabels("elasticsearch-plugin", "rescore-grouping-mixup")
        vcsUrl = "https://github.com/anti-social/elasticsearch-rescore-grouping-mixup.git"
        version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = pluginVersion
            released = Date().toString()
            vcsTag = "v$pluginVersion"
        })
    })
    filesSpec(delegateClosureOf<RecordingCopyTask> {
        val distributionsDir = buildDir.resolve("distributions")
        from(distributionsDir)
        include("*-$pluginVersion-*.zip")
        into(".")
    })
    publish = true
    dryRun = hasProperty("bintrayDryRun")
}

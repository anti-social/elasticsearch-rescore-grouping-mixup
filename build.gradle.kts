import java.util.Date
import com.jfrog.bintray.gradle.BintrayExtension
import org.elasticsearch.gradle.VersionProperties
import com.jfrog.bintray.gradle.tasks.RecordingCopyTask

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
    id("com.jfrog.bintray") version "1.8.4"
}

apply {
    plugin("elasticsearch.esplugin")
}

val pluginVersion = project.file("project.version")
        .readLines()
        .first()
        .toUpperCase()
        .let { ver ->
            if (hasProperty("release")) {
                ver.removeSuffix("-SNAPSHOT")
            } else {
                ver
            }
        }

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

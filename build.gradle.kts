import java.nio.file.Paths

plugins {
    java
    idea
    id("elasticsearch.esplugin")
    id("elasticsearch.internal-cluster-test")
    id("nebula.ospackage") version Versions.nebula
}

group = "dev.evo.elasticsearch"
version = Versions.project

configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
    name = "rescore-grouping-mixup"
    description = "Adds rescorer for mixing up search hits inside their groups."
    classname = "company.evo.elasticsearch.plugin.GroupingMixupPlugin"
    version = Versions.plugin
}

setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

val distDir = Paths.get(buildDir.path, "distributions")

tasks.register("assembledInfo") {
    val assembledFilenamePath = distDir.resolve("assembled-plugin.filename")

    outputs.file(assembledFilenamePath)

    doLast {
        if (properties.containsKey("assembledInfo")) {
            assembledFilenamePath.toFile()
                .writeText(tasks["bundlePlugin"].outputs.files.singleFile.name)
        }
    }
}
tasks.named("assemble") {
    dependsOn("assembledInfo")
    dependsOn("deb")
}

tasks.named("validateElasticPom") {
    enabled = false
}

tasks.register("deb", com.netflix.gradle.plugins.deb.Deb::class) {
    dependsOn("bundlePlugin")

    packageName = "elasticsearch-${project.name}-plugin"
    requires("elasticsearch", Versions.elasticsearch)
        .or("elasticsearch-oss", Versions.elasticsearch)

    from(zipTree(tasks["bundlePlugin"].outputs.files.singleFile))

    val esHome = project.properties["esHome"] ?: "/usr/share/elasticsearch"
    into("$esHome/plugins/${project.name}")

    doLast {
        if (properties.containsKey("assembledInfo")) {
            distDir.resolve("assembled-deb.filename").toFile()
                .writeText(assembleArchiveName())
        }
    }
}
import java.nio.file.Paths

plugins {
    java
    idea
    id("elasticsearch.esplugin")
    id("elasticsearch.internal-cluster-test")
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

tasks.register("assembledVersion") {
    val assembledVersionPath = Paths.get(buildDir.path, "distributions", "assembled.version")
    outputs.file(assembledVersionPath)
    doLast {
        if (properties.containsKey("assembledVersion")) {
            file(assembledVersionPath).writeText(Versions.project)
        }
    }
}
tasks.named("assemble") {
    dependsOn("assembledVersion")
}

tasks.named("validateElasticPom") {
    enabled = false
}

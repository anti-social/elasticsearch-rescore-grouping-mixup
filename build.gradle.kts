plugins {
    java
    idea
    id("elasticsearch.esplugin")
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

// Cannot download plugin from bintray
tasks.named("loggerUsageCheck") {
    enabled = false
}

tasks.named("validateNebulaPom") {
    enabled = false
}

tasks.named("testingConventions") {
    enabled = false
}

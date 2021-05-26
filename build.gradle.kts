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

tasks.named("validateNebulaPom") {
    enabled = false
}

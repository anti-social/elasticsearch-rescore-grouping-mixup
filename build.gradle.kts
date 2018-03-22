buildscript {
    repositories {
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath("org.elasticsearch.gradle:build-tools:6.2.3")
    }
}

repositories {
    mavenCentral()
    jcenter()
}

plugins {
    java
    idea
}

apply {
    plugin("elasticsearch.esplugin")
}

configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
    name = "rescore-grouping-mixup"
    description = "Adds rescorer for mixing up search hits inside their groups."
    version = "0.1.0"
    classname = "company.evo.elasticsearch.plugin.GroupingMixupPlugin"
}

project.setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
project.setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

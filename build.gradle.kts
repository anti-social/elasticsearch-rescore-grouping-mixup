import java.nio.file.Paths

plugins {
    java
    idea
    id("elasticsearch.esplugin")
    id("nebula.ospackage") version Versions.nebula
}

configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
    name = "rescore-grouping-mixup"
    description = "Adds rescorer for mixing up search hits inside their groups."
    classname = "company.evo.elasticsearch.plugin.GroupingMixupPlugin"
    version = Versions.plugin
}

setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

version = Versions.project

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configure<NamedDomainObjectContainer<org.elasticsearch.gradle.testclusters.ElasticsearchCluster>> {
    val integTestCluster by named("integTest") {
        setTestDistribution(org.elasticsearch.gradle.testclusters.TestDistribution.OSS)
    }

    val integTestTask = tasks.getByName<org.elasticsearch.gradle.test.RestIntegTestTask>("integTest") {
        dependsOn("bundlePlugin")
    }

    tasks.named("check") {
        dependsOn(integTestTask)
    }
}

val distDir = Paths.get(buildDir.path, "distributions")

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

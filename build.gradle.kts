plugins {
    java
    idea
    id("elasticsearch.esplugin")
    id("nebula.ospackage")
}

configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
    name = "rescore-grouping-mixup"
    description = "Adds rescorer for mixing up search hits inside their groups."
    classname = "company.evo.elasticsearch.plugin.GroupingMixupPlugin"
    extendedPlugins = listOf("lang-painless")
}

extraConfiguration()

version = Versions.project

dependencies {
    compileOnly("org.elasticsearch.plugin:elasticsearch-scripting-painless-spi:${Versions.elasticsearch}")
}

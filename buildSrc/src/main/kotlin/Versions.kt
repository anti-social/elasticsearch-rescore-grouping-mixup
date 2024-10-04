import java.util.Properties

object Versions {
    private val versionProps = this::class.java
        .getResourceAsStream("/es-plugin-versions.properties")
        .use {
            Properties().apply {
                load(it)
            }
        }

    val project = versionProps["projectVersion"]!!.toString()
    val elasticsearch = versionProps["esVersion"]!!.toString()
    val plugin = versionProps["pluginVersion"]!!.toString()

    val nebula = "9.1.1"
}

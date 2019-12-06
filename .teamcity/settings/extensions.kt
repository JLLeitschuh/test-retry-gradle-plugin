import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2018_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2018_2.ParametrizedWithType
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2018_2.toId

fun BuildType.agentRequirement(os: Os) {
    requirements {
        contains("teamcity.agent.jvm.os.name", os.requirementName)
    }
}

fun ParametrizedWithType.java8Home(os: Os) {
    param("env.JAVA_HOME", "%${os.name}.java8.oracle.64bit%")
}

const val useGradleInternalScansServer = "-I gradle/init-scripts/build-scan.init.gradle.kts"

fun Project.buildType(buildTypeName: String, init: BuildType.() -> Unit): BuildType {
    val buildType = buildType {
        name = buildTypeName
        id = RelativeId(name.toId(stripRootProject(this@buildType.id.toString())))

        artifactRules = "build/reports/** => reports"
        agentRequirement(Os.linux) // default

        params {
            java8Home(Os.linux)
        }

        vcs {
            root(DslContext.settingsRoot)
            checkoutMode = CheckoutMode.ON_AGENT
        }
    }.apply(init)

    this.buildTypesOrderIds += buildType.id!!
    return buildType
}

fun stripRootProject(id: String): String {
    return id.replace("${DslContext.projectId.value}_", "")
}

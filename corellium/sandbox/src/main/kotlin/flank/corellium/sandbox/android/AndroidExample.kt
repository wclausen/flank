@file:JvmName("AndroidExample")
package flank.corellium.sandbox.android

import flank.corellium.client.Corellium
import flank.corellium.client.data.BootOptions
import flank.corellium.client.data.Instance
import flank.corellium.sandbox.config.Config
import kotlinx.coroutines.runBlocking

private const val instanceName = "corellium-android"
private const val flavor = "ranchu"
private const val os = "11.0.0"
private const val screen = "720x1280:280"
private const val projectName = "Default Project"
private const val apkPath = "./corellium/corellium-sandbox/src/main/resources/android/app-debug.apk"
private const val testApkPath =
    "./corellium/corellium-sandbox/src/main/resources/android/app-multiple-success-debug-androidTest.apk"

fun main(): Unit = runBlocking {
    val client = Corellium(
        api = Config.api,
        username = Config.username,
        password = Config.password
    )

    client.logIn()

    val projectId = client.getAllProjects().first { it.name == projectName }.id

    println("Looking for $instanceName instance")
    val instanceId = client.getProjectInstancesList(projectId)
        .find { it.name == instanceName }?.id
        ?: run {
            println("Instance not found. Creating new one")
            client.createNewInstance(
                Instance(
                    project = projectId,
                    name = instanceName,
                    flavor = flavor,
                    os = os,
                    bootOptions = BootOptions(
                        screen = screen
                    )
                )
            )
        }

    println("Wait until instance is ready (may take some time ~3-5 min)")
    client.waitUntilInstanceIsReady(instanceId)

    val instance = client.getInstanceInfo(instanceId)

    println("Creating agent")
    val agent = client.createAgent(instance.agent?.info ?: error("Agent info is not present"))
    println("Await agent is connected and ready to use")
    agent.waitForAgentReady()
    println("Agent ready")

    runProcess("adb", "connect", "${instance.serviceIp}:${instance.portAdb}")
    runProcess("adb", "devices")
    runProcess("adb", "install", apkPath)
    runProcess("adb", "install", "-t", testApkPath)

    println(
        """
        Running instrumentation tests:
          app: $apkPath
          test: $testApkPath
        """.trimIndent()
    )
    runProcess(
        "adb",
        "shell",
        "am",
        "instrument",
        "-w",
        "com.example.test_app.test/androidx.test.runner.AndroidJUnitRunner"
    )

    println("Cleaning up...")
    runProcess("adb", "uninstall", "com.example.test_app")
    runProcess("adb", "uninstall", "com.example.test_app.test")
}

private fun runProcess(vararg command: String) {
    ProcessBuilder(*command)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start().waitFor()
}

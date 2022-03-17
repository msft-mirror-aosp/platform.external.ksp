package com.google.devtools.ksp.test

import com.google.devtools.ksp.DualLookupTracker
import com.google.devtools.ksp.KotlinSymbolProcessingExtension
import com.google.devtools.ksp.KspOptions
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import com.google.devtools.ksp.processor.AbstractTestProcessor
import com.google.devtools.ksp.testutils.AbstractKSPTest
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoot
import org.jetbrains.kotlin.codegen.GenerationUtils
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.compilerConfigurationProvider
import org.jetbrains.kotlin.test.services.javaFiles
import org.junit.jupiter.api.Assertions
import java.io.File

abstract class AbstractKSPCompilerPluginTest : AbstractKSPTest(FrontendKinds.ClassicFrontend) {
    override fun runTest(testServices: TestServices, mainModule: TestModule, libModules: List<TestModule>) {
        val compilerConfiguration = testServices.compilerConfigurationProvider.getCompilerConfiguration(mainModule)
        compilerConfiguration.put(CommonConfigurationKeys.MODULE_NAME, mainModule.name)
        compilerConfiguration.put(CommonConfigurationKeys.LOOKUP_TRACKER, DualLookupTracker())
        if (!mainModule.javaFiles.isEmpty()) {
            mainModule.writeJavaFiles()
            compilerConfiguration.addJavaSourceRoot(mainModule.javaDir)
        }

        // TODO: other platforms
        val kotlinCoreEnvironment = KotlinCoreEnvironment.createForTests(
            disposable,
            compilerConfiguration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        val ktFiles = mainModule.loadKtFiles(kotlinCoreEnvironment.project)

        val logger = MessageCollectorBasedKSPLogger(
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false),
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false),
            false
        )

        val contents = mainModule.files.first().originalFile.readLines()
        val testProcessorName = contents
            .filter { it.startsWith(TEST_PROCESSOR) }
            .single()
            .substringAfter(TEST_PROCESSOR)
            .trim()
        val testProcessor: AbstractTestProcessor =
            Class.forName("com.google.devtools.ksp.processor.$testProcessorName")
                .getDeclaredConstructor().newInstance() as AbstractTestProcessor

        val testRoot = mainModule.testRoot
        val analysisExtension =
            KotlinSymbolProcessingExtension(
                KspOptions.Builder().apply {
                    if (!mainModule.javaFiles.isEmpty()) {
                        javaSourceRoots.add(mainModule.javaDir)
                    }
                    classOutputDir = File(testRoot, "kspTest/classes/main")
                    javaOutputDir = File(testRoot, "kspTest/src/main/java")
                    kotlinOutputDir = File(testRoot, "kspTest/src/main/kotlin")
                    resourceOutputDir = File(testRoot, "kspTest/src/main/resources")
                    projectBaseDir = testRoot
                    cachesDir = File(testRoot, "kspTest/kspCaches")
                    kspOutputDir = File(testRoot, "kspTest")
                }.build(),
                logger, testProcessor
            )
        AnalysisHandlerExtension.registerExtension(kotlinCoreEnvironment.project, analysisExtension)

        GenerationUtils.compileFilesTo(ktFiles, kotlinCoreEnvironment, mainModule.outDir)

        val result = testProcessor.toResult()
        val expectedResults = contents
            .dropWhile { !it.startsWith(EXPECTED_RESULTS) }
            .drop(1)
            .takeWhile { !it.startsWith("// END") }
            .map { it.substring(3).trim() }
        Assertions.assertEquals(expectedResults.joinToString("\n"), result.joinToString("\n"))
    }
}
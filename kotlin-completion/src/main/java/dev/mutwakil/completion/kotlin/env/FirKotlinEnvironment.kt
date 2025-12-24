package dev.mutwakil.completion.kotlin.env

import com.tyron.builder.project.api.Module
import com.tyron.builder.project.impl.AndroidModuleImpl
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import java.io.File
import org.jetbrains.kotlin.com.intellij.openapi.util.Key

class FirKotlinEnvironment(
    val coreEnvironment: KotlinCoreEnvironment
) {

    companion object {
        val ENV_KEY =
            Key.create<FirKotlinEnvironment>("firKotlinEnvironment")

        fun get(module: Module): FirKotlinEnvironment? {
            val androidModule = module as? AndroidModuleImpl ?: return null

            val cached = androidModule.getUserData(ENV_KEY)
            if (cached != null) return cached

             
            val jars = androidModule.codeAssistLibraries
                .map { it.sourceFile }
                .filter(File::exists)

            setIdeaIoUseFallback()
            setupIdeaStandaloneExecution()

            val configuration = CompilerConfiguration().apply {
                put(CommonConfigurationKeys.MODULE_NAME, "fir-completion")
                put(CommonConfigurationKeys.USE_FIR, true)
                put(CommonConfigurationKeys.USE_LIGHT_TREE, true)
                addJvmClasspathRoots(jars)
            }

            val env = KotlinCoreEnvironment.createForProduction(
                {},
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            val firEnv = FirKotlinEnvironment(env)
            androidModule.putUserData(ENV_KEY, firEnv)
            return firEnv
        }
    }
}
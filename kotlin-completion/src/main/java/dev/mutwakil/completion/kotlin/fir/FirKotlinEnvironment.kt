package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile
import com.tyron.completion.model.CompletionItem
import com.tyron.builder.project.api.Module
import com.tyron.builder.project.impl.AndroidModuleImpl
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.com.intellij.openapi.util.Key
import java.io.File

class FirKotlinEnvironment private constructor(
    val coreEnvironment: KotlinCoreEnvironment
) {

    /* ========================
     * Completion entry point
     * ======================== */

        fun complete(
           file: KotlinFile,
           line: Int,
           column: Int
        ): List<CompletionItem> {

            FirImportCompletion.tryComplete(file, line, column)
              ?.let { return it }

            FirKeywordCompletion.tryComplete(file, line, column)
              ?.let { return it }

            FirMemberCompletion.tryComplete(file, line, column)
             ?.let { return it }

           return emptyList()
        }   

    /* ========================
     * Factory + caching
     * ======================== */

    companion object {

        private val ENV_KEY =
            Key.create<FirKotlinEnvironment>("firKotlinEnvironment")

        fun get(module: Module): FirKotlinEnvironment? {
            val androidModule = module as? AndroidModuleImpl ?: return null

            val cached = androidModule.getUserData(ENV_KEY)
            if (cached != null) return cached

            val jars = androidModule.codeAssistLibraries
                .map { it.sourceFile }
                .filter(File::exists)

            val env = create(jars)
            androidModule.putUserData(ENV_KEY, env)
            return env
        }

        private fun create(classpath: List<File>): FirKotlinEnvironment {
            setIdeaIoUseFallback()
            setupIdeaStandaloneExecution()

            val configuration = CompilerConfiguration().apply {

                put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
                put(CommonConfigurationKeys.USE_FIR, true)
                put(CommonConfigurationKeys.USE_LIGHT_TREE, true)

                put(AnalysisFlags.ideMode, true)
                put(AnalysisFlags.skipMetadataVersionCheck, true)
                put(AnalysisFlags.skipPrereleaseCheck, true)

                addJvmClasspathRoots(classpath)

                val languageVersion =
                    LanguageVersion.fromVersionString("2.3")!!

                put(
                    CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                    LanguageVersionSettingsImpl(
                        languageVersion,
                        ApiVersion.createByLanguageVersion(languageVersion)
                    )
                )
            }

            val coreEnv = KotlinCoreEnvironment.createForProduction(
                {},
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            return FirKotlinEnvironment(coreEnv)
        }
    }
}
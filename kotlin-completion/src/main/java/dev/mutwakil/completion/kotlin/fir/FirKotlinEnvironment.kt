/*package dev.mutwakil.completion.kotlin.fir

import com.tyron.kotlin.completion.KotlinFile
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.model.DrawableKind
import com.tyron.completion.util.CompletionUtils
import com.tyron.completion.DefaultInsertHandler
import com.tyron.builder.project.api.Module
import com.tyron.builder.project.impl.AndroidModuleImpl
import dev.mutwakil.completion.kotlin.jar.JarImportIndex
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
    val coreEnvironment: KotlinCoreEnvironment,
    private val importIndex: JarImportIndex
) {



    fun complete(
        file: KotlinFile,
        line: Int,
        column: Int
    ): List<CompletionItem> {

        // ---------- import context ----------
        val lastLine =
            FirCompletionUtil.lastLineBeforeCursor(file, line, column)

        if (lastLine.trimStart().startsWith("import")) {
            val prefix =
                FirCompletionUtil.importPrefix(file, line, column)

            return importIndex.complete(prefix)
                .map { name ->
                    CompletionItem.create(
                        name,
                        "import",
                        name,
                        DrawableKind.Package
                    ).apply {
                        cursorOffset = commitText.length
                        setInsertHandler(
                            DefaultInsertHandler(
                                CompletionUtils.JAVA_PREDICATE,
                                this
                            )
                        )
                    }
                }
        }

        // ---------- keyword completion ----------
        FirKeywordCompletion.tryComplete(file, line, column)
            ?.let { return it }

        // ---------- member completion (ممكن يكون stub حالياً) ----------
        FirMemberCompletion.tryComplete(file, line, column)
            ?.let { return it }

        return emptyList()
    }



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
                put(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM, true)

                addJvmClasspathRoots(classpath)

                val langFeatures =
                    mutableMapOf<LanguageFeature, LanguageFeature.State>().apply {
                        for (langFeature in LanguageFeature.entries) {
                            this[langFeature] = LanguageFeature.State.ENABLED
                        }
                    }

                val languageVersion =
                    LanguageVersion.fromVersionString("2.3")!!

                val analysisFlags: Map<AnalysisFlag<*>, Any?> = mapOf(
                    AnalysisFlags.ideMode to true,
                    AnalysisFlags.skipMetadataVersionCheck to true,
                    AnalysisFlags.skipPrereleaseCheck to true,
                )

                put(
                    CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                    LanguageVersionSettingsImpl(
                        languageVersion,
                        ApiVersion.createByLanguageVersion(languageVersion),
                        analysisFlags,
                        langFeatures
                    )
                )
            }

            val coreEnv = KotlinCoreEnvironment.createForProduction(
                {},
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            val importIndex = JarImportIndex(classpath)

            return FirKotlinEnvironment(coreEnv, importIndex)
        }
    }
}*/
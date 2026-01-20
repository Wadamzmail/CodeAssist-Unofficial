@file:OptIn(FrontendInternals::class)

package com.tyron.kotlin.completion

import com.tyron.builder.project.api.Module
import com.tyron.builder.project.impl.AndroidModuleImpl
import com.tyron.completion.DefaultInsertHandler
import com.tyron.completion.model.CompletionItem
import com.tyron.completion.model.CompletionList
import com.tyron.completion.model.DrawableKind
import com.tyron.kotlin.completion.codeInsight.ReferenceVariantsHelper
import com.tyron.kotlin.completion.model.Analysis
import com.tyron.kotlin.completion.util.*
import com.tyron.kotlin_completion.util.PsiUtils
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.editor.impl.DocumentWriteAccessGuard
import org.jetbrains.kotlin.com.intellij.openapi.util.Key
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible
import java.io.File
import java.util.*
import kotlin.collections.set
import com.tyron.completion.util.CompletionUtils
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import com.tyron.common.Prefs
import com.tyron.common.SharedPreferenceKeys
import org.jetbrains.kotlin.resolve.TopDownAnalysisContext
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.container.ComponentProvider
import com.tyron.builder.BuildModule;

data class KotlinEnvironment(
    val classpath: List<File>,
    val kotlinEnvironment: KotlinCoreEnvironment
) {
    @JvmField
    val kotlinFiles = mutableMapOf<String, KotlinFile>()

    var currentItemCount = 0

    fun updateKotlinFile(name: String, contents: String): KotlinFile {
        val kotlinFile = KotlinFile.from(kotlinEnvironment.project, name, contents)
        kotlinFiles[name] = kotlinFile
        return kotlinFile
    }

    fun removeKotlinFile(name: String) {
        kotlinFiles.remove(name)
    }

    fun getKotlinFile(name: String): KotlinFile? {
        return kotlinFiles[name]
    }

    private data class DescriptorInfo(
        val isTipsManagerCompletion: Boolean,
        val descriptors: List<DeclarationDescriptor>
    )

    private val renderer = IdeDescriptorRenderersScripting.SOURCE_CODE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SHORT
        typeNormalizer = IdeDescriptorRenderersScripting.APPROXIMATE_FLEXIBLE_TYPES
        parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
        typeNormalizer = {
            if (it.isFlexible()) it.asFlexibleType().upperBound
            else it
        }
    }
    
    data class CodeIssue(
        val startOffset: Int,
        val endOffset: Int,
        val message: String,
        val severity: CompilerMessageSeverity
    )

    private var issueListener = { _: CodeIssue -> }

    fun addIssueListener(listener: (issue: CodeIssue) -> Unit) {
        issueListener = listener
    }

    private val messageCollector = object : MessageCollector {
        private var hasError = false
        override fun clear() {}

        override fun hasErrors() = hasError

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            if (location == null) {
                println(message)
                return
            }
            if (severity.isError) {
                hasError = true
            }
            val kotlinFile = kotlinFiles[location.path.substring(1)]
            if (kotlinFile == null) {
                println("no kotlin file for ${location.path}")
                return
            }
            val issue = CodeIssue(
                kotlinFile.offsetFor(location.line - 1, location.column - 1),
                kotlinFile.offsetFor(location.lineEnd - 1, location.columnEnd - 1),
                message,
                severity
            )
            issueListener(issue)
        }
    }

    init {
        kotlinEnvironment.configuration.put(
            CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            messageCollector
        )
    }
    @JvmField
    var analysis: TopDownAnalysisContext? = null 

    fun getPrefix(element: PsiElement): String {
        var text = (element as? KtSimpleNameExpression)?.text
        if (text == null) {
            val type = PsiUtils.findParent(element, KtSimpleNameExpression::class.java)
            if (type != null) {
                text = type.text
            }
        }
        if (text == null) {
            text = element.text
        }
        return (text ?: "").substringBefore(COMPLETION_SUFFIX)
            .let {
                if (it.endsWith(".")) "" else it
            }
    }


    fun complete(file: KotlinFile, line: Int, character: Int): CompletionList {
        currentItemCount = 0
        
        return with(file.insert("$COMPLETION_SUFFIX ", line, character)) {
            kotlinFiles[file.name] = this

            val position = elementAt(line, character)
            val prefix = position?.let { getPrefix(it) } ?: ""

            val reference = (position?.parent as? KtSimpleNameExpression)?.mainReference

            val list = position?.let { element ->
                val descriptorInfo = descriptorsFrom(element,file.kotlinFile)

                descriptorInfo.descriptors.toMutableList().apply {
                    sortWith { a, b ->
                        val (a1, a2) = a.presentableName()
                        val (b1, b2) = b.presentableName()
                        ("$a1$a2").compareTo("$b1$b2", true)
                    }
                }.mapNotNull { descriptor ->
                    completionVariantFor(
                        prefix,
                        descriptor
                    )
                }
            } ?: emptyList()

            val builder = CompletionList.builder(prefix)
            builder.addItems(if (isAfterDot(position)) list else list + keywordsList(prefix))
            if (currentItemCount >= MAX_ITEMS_COUNT) {
                builder.incomplete()
            }

            builder.build()
        }
    }
    
    fun keywordsList(prefix: String): List<CompletionItem> = 
       keywordsCompletionVariants(KtTokens.KEYWORDS, prefix) + 
       keywordsCompletionVariants(KtTokens.SOFT_KEYWORDS, prefix)
    
    private fun isAfterDot(element: PsiElement?): Boolean {
       if (element == null) return false
       val prev = element.prevSibling
       if (prev?.text == ".") return true

       val parent = element.parent
       return parent is KtQualifiedExpression &&
                    parent.selectorExpression == element
    }


    private fun completionVariantFor(
        prefix: String,
        descriptor: DeclarationDescriptor
    ): CompletionItem? {
        val (name, tail) = descriptor.presentableName()
        val fullName: String = formatName(name, 40)
        var completionText = fullName
        var position = completionText.indexOf('(')
        if (position != -1) {
            if (completionText[position - 1] == ' ') position -= 2
            if (completionText[position + 1] == ')') position++
            completionText = completionText.substring(0, position + 1)
        }
        position = completionText.indexOf(":")
        if (position != -1) completionText = completionText.substring(0, position - 1)
        return if (prefix.isEmpty() || fullName.startsWith(prefix)) {
            CompletionItem(fullName).apply {
                iconKind = iconFrom(descriptor)
                detail = tail
                commitText = completionText
                position = commitText.length
                sortText = fullName
                setInsertHandler(DefaultInsertHandler(this))
            }
        } else null
    }

    private fun iconFrom(descriptor: DeclarationDescriptor) = when (descriptor) {
        is FunctionDescriptor -> DrawableKind.Method
        is PropertyDescriptor -> DrawableKind.Attribute
        is LocalVariableDescriptor -> DrawableKind.LocalVariable
        is ClassDescriptor -> DrawableKind.Class
        is PackageFragmentDescriptor -> DrawableKind.Package
        is PackageViewDescriptor -> DrawableKind.Package
        is ValueParameterDescriptor -> DrawableKind.LocalVariable
        is TypeParameterDescriptorImpl -> DrawableKind.Class
        else -> DrawableKind.Snippet
    }

    private fun formatName(builder: String, symbols: Int) =
        if (builder.length > symbols) builder.substring(0, symbols) + "..." else builder


    private fun keywordsCompletionVariants(keywords: TokenSet, prefix: String) =
        keywords.types.mapNotNull {
            if (it is KtKeywordToken && it.value.startsWith(prefix)) {
                CompletionItem(it.value, "Keyword", it.value, DrawableKind.Keyword).apply {
                    setInsertHandler(DefaultInsertHandler(this))
                    addFilterText(it.value)
                }
            } else {
                null
            }
        }

    private fun descriptorsFrom(element: PsiElement, current: KtFile): DescriptorInfo {
        val files = kotlinFiles.values.map { it.kotlinFile }.toList()
        val analysis = analysisOf(files,current)
        return with(analysis) {
            logTime("referenceVariants") {
                (referenceVariantsFrom(element)
                    ?: referenceVariantsFrom(element.parent))?.let { descriptors ->
                    DescriptorInfo(true, descriptors)
                } ?: element.parent.let { parent ->
                    DescriptorInfo(
                        isTipsManagerCompletion = false,
                        descriptors = when (parent) {
                            is KtQualifiedExpression -> {
                                analysisResult.bindingContext.get(
                                    BindingContext.EXPRESSION_TYPE_INFO,
                                    parent.receiverExpression
                                )?.type?.let { expressionType ->
                                    analysisResult.bindingContext.get(
                                        BindingContext.LEXICAL_SCOPE,
                                        parent.receiverExpression
                                    )?.let {
                                        expressionType.memberScope.getContributedDescriptors(
                                            DescriptorKindFilter.ALL,
                                            MemberScope.ALL_NAME_FILTER
                                        )
                                    }
                                }?.toList() ?: emptyList()
                            }

                            else -> analysisResult.bindingContext.get(
                                BindingContext.LEXICAL_SCOPE,
                                element as KtExpression
                            )
                                ?.getContributedDescriptors(
                                    DescriptorKindFilter.ALL,
                                    MemberScope.ALL_NAME_FILTER
                                )
                                ?.toList() ?: emptyList()
                        }
                    )
                }

            }
        }
    }

   /* private fun analysisOf(files: List<KtFile>,current: KtFile): Analysis {
        val trace = CliBindingTrace()
        val project = files.first().project
        val componentProvider = TopDownAnalyzerFacadeForJVM.createContainer(
            kotlinEnvironment.project,
            files,
            trace,
            kotlinEnvironment.configuration,
            { globalSearchScope -> kotlinEnvironment.createPackagePartProvider(globalSearchScope) },
            { storageManager, ktFiles ->
                FileBasedDeclarationProviderFactory(
                    storageManager,
                    ktFiles
                )
            },
            sourceModuleSearchScope = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(
                project,
                files
            )
        )
        return logTime("analysis") {
            componentProvider.getService(LazyTopDownAnalyzer::class.java)
                .analyzeDeclarations(
                    TopDownAnalysisMode.TopLevelDeclarations,
                    files,
                    DataFlowInfo.EMPTY
                )
            val moduleDescriptor = componentProvider.getService(ModuleDescriptor::class.java)
            AnalysisHandlerExtension.getInstances(project)
                .find { it.analysisCompleted(project, moduleDescriptor, trace, files) != null }
            return@logTime Analysis(
                componentProvider,
                AnalysisResult.success(trace.bindingContext, moduleDescriptor)
            )
        }
    }*/
    
    private val analyzerWithCompilerReport =
        AnalyzerWithCompilerReport(kotlinEnvironment.configuration)
    
    fun analysisOf(files: List<KtFile>, current: KtFile): Analysis {
        val project = files.first().project
        val bindingTrace = CliBindingTrace(kotlinEnvironment.project)
        var componentProvider: ComponentProvider? = null
        analyzerWithCompilerReport.analyzeAndReport(files) {
            componentProvider = logTime("componentProvider") {
                TopDownAnalyzerFacadeForJVM.createContainer(
                    kotlinEnvironment.project,
                    files,
                    bindingTrace,
                    kotlinEnvironment.configuration,
                    kotlinEnvironment::createPackagePartProvider,
                    { storageManager, _ ->
                        FileBasedDeclarationProviderFactory(
                            storageManager,
                            files
                        )
                    },
                    sourceModuleSearchScope = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(
                      project,
                       files
                    ) 
                )
            }
            logTime("analyzeDeclarations") {
                analysis = componentProvider!!
                    .getService(LazyTopDownAnalyzer::class.java)
                    .analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations,files, DataFlowInfo.EMPTY )
            }

            val moduleDescriptor = componentProvider!!.getService(ModuleDescriptor::class.java)
            AnalysisHandlerExtension.getInstances(project).find {
                it.analysisCompleted(
                    project,
                    moduleDescriptor,
                    bindingTrace,
                    listOf(current)
                ) != null
            }

            return@analyzeAndReport AnalysisResult.success(
                bindingTrace.bindingContext,
                componentProvider!!.getService(ModuleDescriptor::class.java)
            )
        }
        return Analysis(
            componentProvider!!,
            analyzerWithCompilerReport.analysisResult
        )
    }

    private fun Analysis.referenceVariantsFrom(element: PsiElement): List<DeclarationDescriptor>? {
        val prefix = getPrefix(element)
        val elementKt = element as? KtElement ?: return emptyList()
        val bindingContext = analysisResult.bindingContext
        val resolutionFacade = KotlinResolutionFacade(
            project = element.project,
            componentProvider = componentProvider,
            moduleDescriptor = analysisResult.moduleDescriptor
        )
        val inDescriptor: DeclarationDescriptor =
            elementKt.getResolutionScope(bindingContext, resolutionFacade).ownerDescriptor

        return when (element) {
            is KtSimpleNameExpression -> ReferenceVariantsHelper(
                analysisResult.bindingContext,
                resolutionFacade = resolutionFacade,
                moduleDescriptor = analysisResult.moduleDescriptor,
                visibilityFilter = VisibilityFilter(
                    inDescriptor,
                    bindingContext,
                    element,
                    resolutionFacade
                )
            ).getReferenceVariants(
                element,
                DescriptorKindFilter.ALL,
                nameFilter = {
                    if (prefix.isNotEmpty()) {
                        it.identifier.startsWith(prefix)
                    } else {
                        true
                    }
                },
                filterOutJavaGettersAndSetters = true,
                filterOutShadowed = true,
                excludeNonInitializedVariable = true,
                useReceiverType = null
            ).toList()
            else -> null
        }
    }

    private fun DeclarationDescriptor.presentableName() = when (this) {
        is FunctionDescriptor -> name.asString() + renderer.renderFunctionParameters(this) to when {
            returnType != null -> renderer.renderType(returnType!!)
            else -> (extensionReceiverParameter?.let { param ->
                " for ${renderer.renderType(param.type)} in ${
                    DescriptorUtils.getFqName(
                        containingDeclaration
                    )
                }"
            } ?: "")
        }
        else -> name.asString() to when (this) {
            is VariableDescriptor -> renderer.renderType(type)
            is ClassDescriptor -> " (${DescriptorUtils.getFqName(containingDeclaration)})"
            else -> renderer.render(this)
        }
    }


    // This code is a fragment of org.jetbrains.kotlin.idea.completion.CompletionSession from Kotlin IDE Plugin
    // with a few simplifications which were possible because webdemo has very restricted environment (and well,
    // because requirements on compeltion' quality in web-demo are lower)
    private inner class VisibilityFilter    (
        private val inDescriptor: DeclarationDescriptor,
        private val bindingContext: BindingContext,
        private val element: KtElement,
        private val resolutionFacade: KotlinResolutionFacade
    ) : (DeclarationDescriptor) -> Boolean {
        override fun invoke(descriptor: DeclarationDescriptor): Boolean {
            if (currentItemCount >= MAX_ITEMS_COUNT) {
                println("MAX COUNT EXCEEDED")
                return false
            }

            val a = "a"

            currentItemCount++

            if (descriptor is TypeParameterDescriptor && !isTypeParameterVisible(descriptor)) return false

            if (descriptor is DeclarationDescriptorWithVisibility) {
                return descriptor.isVisible(element, null, bindingContext, resolutionFacade)
            }

            if (descriptor.isInternalImplementationDetail()) return false

            return true
        }

        private fun isTypeParameterVisible(typeParameter: TypeParameterDescriptor): Boolean {
            val owner = typeParameter.containingDeclaration
            var parent: DeclarationDescriptor? = inDescriptor
            while (parent != null) {
                if (parent == owner) return true
                if (parent is ClassDescriptor && !parent.isInner) return false
                parent = parent.containingDeclaration
            }
            return true
        }

        private fun DeclarationDescriptor.isInternalImplementationDetail(): Boolean =
            importableFqName?.asString() in excludedFromCompletion
    }

    companion object {
        private const val COMPLETION_SUFFIX = "IntellijIdeaRulezzz"

        private val MAX_ITEMS_COUNT = Prefs.get().getString(SharedPreferenceKeys.KOTLIN_MAX_ITEMS_COUNT,"50")?.toIntOrNull()?:50

        val ENVIRONMENT_KEY = Key.create<KotlinEnvironment>("kotlinEnvironmentKey")


        private val excludedFromCompletion: List<String> = listOf(
            "kotlin.jvm.internal",
            "kotlin.coroutines.experimental.intrinsics",
            "kotlin.coroutines.intrinsics",
            "kotlin.coroutines.experimental.jvm.internal",
            "kotlin.coroutines.jvm.internal",
            "kotlin.reflect.jvm.internal"
        )

        fun with(classpath: List<File>): KotlinEnvironment {
            setIdeaIoUseFallback()
            setupIdeaStandaloneExecution()

            val kotlinCoreEnvironment = KotlinCoreEnvironment.createForProduction(
                projectDisposable = {},
                configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES,
                configuration = CompilerConfiguration().apply {
                    addJvmClasspathRoots(classpath.filter { it.exists() && it.isFile && it.extension == "jar" })
                   // put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
                    put(CommonConfigurationKeys.MODULE_NAME, UUID.randomUUID().toString())
                    
                    put(JVMConfigurationKeys.NO_JDK, true)
                    put(JVMConfigurationKeys.NO_REFLECT, true)

                    val langFeatures = mutableMapOf<LanguageFeature, LanguageFeature.State>()
                    for (langFeature in LanguageFeature.values()) {
                        langFeatures[langFeature] = LanguageFeature.State.ENABLED
                    }
                  //  var langV = LanguageVersion.fromVersionString("2.5")!!
                    var langV = LanguageVersion.LATEST_STABLE
                    val languageVersionSettings = LanguageVersionSettingsImpl(
                        langV,
                        ApiVersion.createByLanguageVersion(langV),
                        mapOf(                
                       // AnalysisFlags.extendedCompilerChecks to false,
                        AnalysisFlags.ideMode to true,
                        AnalysisFlags.skipMetadataVersionCheck to true,
                        AnalysisFlags.skipPrereleaseCheck to true 
                        ),
                        langFeatures
                    )
                    put(
                        CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                        languageVersionSettings
                    )
                    put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, Prefs.get().getBoolean(SharedPreferenceKeys.USE_PSI_CLASS_FILES_READING,true))
                    put(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM, Prefs.get().getBoolean(SharedPreferenceKeys.USE_FAST_JAR_FILE_SYSTEM,true))
                    put(JVMConfigurationKeys.DISABLE_RECEIVER_ASSERTIONS, true)
//                    put(CommonConfigurationKeys.INCREMENTAL_COMPILATION, true)
                    put(CommonConfigurationKeys.USE_FIR, Prefs.get().getBoolean(SharedPreferenceKeys.USE_FIR,true))
                    put(CommonConfigurationKeys.USE_LIGHT_TREE, Prefs.get().getBoolean(SharedPreferenceKeys.USE_FIR,true))
                    put(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS, Prefs.get().getString(SharedPreferenceKeys.PARALLEL_BACKEND_THREADS,"10")?.toIntOrNull()?:10)
                    put(CommonConfigurationKeys.VERIFY_IR, IrVerificationMode.NONE);
                    put(CommonConfigurationKeys.USE_FIR_EXTRA_CHECKERS,false)
                     
                    with(K2JVMCompilerArguments()) {
                        put(JVMConfigurationKeys.DISABLE_PARAM_ASSERTIONS, noParamAssertions)
                        put(JVMConfigurationKeys.DISABLE_CALL_ASSERTIONS, noCallAssertions)
                    }
                }
            )

            return KotlinEnvironment(classpath, kotlinCoreEnvironment)
        }

        fun get(module: Module, reIndex : Boolean): KotlinEnvironment? {
            val androidModule = module as? AndroidModuleImpl ?: return null

            val existingEnvironment = androidModule.getUserData(ENVIRONMENT_KEY)
            if (existingEnvironment != null || !reIndex) {
                return existingEnvironment
            }

            val jars = androidModule.codeAssistLibraries.map {
                it.sourceFile
            }.filter(File::exists).toMutableList()
            jars.add(BuildModule.getLambdaStubs())
            jars.add(BuildModule.getAndroidJar()) 
            val environment = with(jars)
            androidModule.kotlinFiles.values.forEach {
                environment.updateKotlinFile(it.absolutePath, it.readText())
            }
            androidModule.putUserData(ENVIRONMENT_KEY, environment)
            return environment
        }
        
        fun get(module: Module): KotlinEnvironment? {
          return get(module,false)
        }
    }
}
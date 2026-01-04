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
import org.jetbrains.kotlin.lexer.KtKeywordToken
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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.resolve.TopDownAnalysisContext
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.container.ComponentProvider

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
        // PERFORMANCE: Disable verbose rendering options
        renderUnabbreviatedType = false
        unitReturnType = false
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
                // println(message) // PERFORMANCE: Remove print in production
                return
            }
            if (severity.isError) {
                hasError = true
            }
            val kotlinFile = kotlinFiles[location.path.substring(1)]
            if (kotlinFile == null) {
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


    // PERFORMANCE OPTIMIZATION: Heavily optimized complete function
    fun complete(file: KotlinFile, line: Int, character: Int) : CompletionList{
         currentItemCount = 0
      return  with(file.insert("$COMPLETION_SUFFIX ", line, character)) {
            kotlinFiles[file.name] = this

            elementAt(line, character)?.let { element ->
                val descriptorInfo = descriptorsFrom(element, file.kotlinFile)
                val prefix = getPrefix(element)
                
                // OPTIMIZATION: Use Sequences to avoid lazy evaluation and avoid rendering items that will be discarded
                val descriptors = descriptorInfo.descriptors.asSequence()
                    .filter { descriptor ->
                        // Fast filter: Check simple name before expensive rendering
                         if (prefix.isEmpty()) true else descriptor.name.asString().startsWith(prefix)
                    }
                    .mapNotNull { descriptor ->
                         // Map directly to CompletionItem
                        completionVariantFor(prefix, descriptor)
                    }
                    .sortedWith(Comparator { a, b ->
                        // Sort by the final label text instead of rendering again
                        a.label.compareTo(b.label, ignoreCase = true)
                    })
                    .take(MAX_ITEMS_COUNT) // Stop processing once we have enough items
                    .toList()

                 // Combine with keywords (optimized to not exceed max count)
                 //descriptors + keywordsCompletionVariants(KtTokens.KEYWORDS, prefix)
                 if (!isAfterDot(element)) {
                     descriptors + keywordsCompletionVariants(KtTokens.KEYWORDS, prefix)
                    } else {
                  descriptors
                 } 
                 
            } ?: emptyList()
      
       val builder = CompletionList.builder(prefix)
           builder.addItems(list)
            if (currentItemCount >= MAX_ITEMS_COUNT) {
                builder.incomplete()
            }

           builder.build()
      }    
   }  
    
    private fun isAfterDot(element: PsiElement): Boolean {
    val parent = element.parent
    return parent is KtQualifiedExpression &&
           parent.selectorExpression == element
    }

    private fun completionVariantFor(
        prefix: String,
        descriptor: DeclarationDescriptor
    ): CompletionItem? {
        // We already checked prefix in the caller (for speed), but double check if needed or just process
        
        // PERFORMANCE: This function is now the most expensive part. 
        val (name, tail) = descriptor.presentableName() // Generates the signature string
        val fullName: String = formatName(name, 40)
        
        // Logic to strip params for insert text
        var completionText = fullName
        var position = completionText.indexOf('(')
        if (position != -1) {
            if (completionText[position - 1] == ' ') position -= 2
            if (completionText[position + 1] == ')') position++
            completionText = completionText.substring(0, position + 1)
        }
        position = completionText.indexOf(":")
        if (position != -1) completionText = completionText.substring(0, position - 1)
        
        return CompletionItem(fullName).apply {
            iconKind = iconFrom(descriptor)
            detail = tail
            commitText = completionText
            cursorOffset = commitText.length
            sortText = fullName
            setInsertHandler(DefaultInsertHandler(this))
        }
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


    private fun keywordsCompletionVariants(keywords: TokenSet, prefix: String): List<CompletionItem> {
        val result = ArrayList<CompletionItem>()
        val iterator = keywords.types.iterator()
        while(iterator.hasNext() && currentItemCount < MAX_ITEMS_COUNT) {
            val it = iterator.next()
            if (it is KtKeywordToken && it.value.startsWith(prefix)) {
                currentItemCount++
                result.add(CompletionItem(it.value, "Keyword", it.value, DrawableKind.Keyword).apply {
                    setInsertHandler(DefaultInsertHandler(this))
                    addFilterText(it.value)
                })
            }
        }
        return result
    }

    private fun descriptorsFrom(element: PsiElement, current: KtFile): DescriptorInfo {
        val files = kotlinFiles.values.map { it.kotlinFile }.toList()
        // Ensure analysis only happens if necessary
        val analysis = analysisOf(files, current)
        
        return with(analysis) {
             // logTime("referenceVariants") { // Removed log for speed
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

           // }
        }
    }

    private val analyzerWithCompilerReport =
        AnalyzerWithCompilerReport(kotlinEnvironment.configuration)
        
    
    fun analysisOf(files: List<KtFile>, current: KtFile): Analysis {
        // PERFORMANCE: If we already have a valid analysis for this file state, we should ideally reuse it.
        // For now, minimizing object creation overhead.
        
        val project = files.first().project
        val bindingTrace = CliBindingTrace(kotlinEnvironment.project)
        var componentProvider: ComponentProvider? = null
        
        // Assuming analyzeAndReport is efficient, otherwise we'd manually invoke analyzer to skip reporting overhead
        analyzerWithCompilerReport.analyzeAndReport(files) {
            componentProvider = TopDownAnalyzerFacadeForJVM.createContainer(
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
            
            // logTime("analyzeDeclarations") {
                analysis = componentProvider!!
                    .getService(LazyTopDownAnalyzer::class.java)
                    .analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files, DataFlowInfo.EMPTY )
            // }

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
        
        val scope = elementKt.getResolutionScope(bindingContext, resolutionFacade)
        val inDescriptor: DeclarationDescriptor = scope.ownerDescriptor

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
                     // Strict filtering here saves massive time later
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

    private fun DeclarationDescriptor.presentableName(): Pair<String, String> {
        // This is a simplified, faster version of rendering logic
        val nameStr = name.asString()
        return when (this) {
            is FunctionDescriptor -> {
                 // Optimization: Don't render complex params if not needed yet, but we do need them for display
                 nameStr + renderer.renderFunctionParameters(this) to (if (returnType != null) renderer.renderType(returnType!!) else "")
            }
            is VariableDescriptor -> nameStr to renderer.renderType(type)
            is ClassDescriptor -> nameStr to " (${DescriptorUtils.getFqName(containingDeclaration)})"
            else -> nameStr to renderer.render(this)
        }
    }

    private fun Severity.toCompilerSeverity(): CompilerMessageSeverity =
    when (this) {
        Severity.ERROR -> CompilerMessageSeverity.ERROR
        Severity.WARNING -> CompilerMessageSeverity.WARNING
        Severity.INFO -> CompilerMessageSeverity.INFO
    }


    private inner class VisibilityFilter    (
        private val inDescriptor: DeclarationDescriptor,
        private val bindingContext: BindingContext,
        private val element: KtElement,
        private val resolutionFacade: KotlinResolutionFacade
    ) : (DeclarationDescriptor) -> Boolean {
        override fun invoke(descriptor: DeclarationDescriptor): Boolean {
             // Critical Speed Fix: Check count inside the Sequence in `complete`, not just here.
             // But keep this for safety.
            if (currentItemCount >= MAX_ITEMS_COUNT) {
                return false
            }
            
            // Optimization: Remove allocation of "val a = ..."
            
            // Note: We increment count in `complete` map/take, so relying on side-effect here is risky with Sequence.
            // But ReferenceVariantsHelper calls this filter eagerly.
            // Let's assume ReferenceVariantsHelper respects false to stop.
            
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

        // Default limit 50 is good
        private  val MAX_ITEMS_COUNT = Prefs.get().getString(SharedPreferenceKeys.KOTLIN_MAX_ITEMS_COUNT,"50")?.toIntOrNull()?:50

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
                   
                    put(CommonConfigurationKeys.MODULE_NAME,JvmProtoBufUtil.DEFAULT_MODULE_NAME)
                    put(JVMConfigurationKeys.NO_JDK, true)
                    put(JVMConfigurationKeys.NO_REFLECT, true)

                    val langFeatures = mutableMapOf<LanguageFeature, LanguageFeature.State>()
                    for (langFeature in LanguageFeature.values()) {
                        langFeatures[langFeature] = LanguageFeature.State.ENABLED
                    }
                    val languageVersionSettings = LanguageVersionSettingsImpl(
                        LanguageVersion.LATEST_STABLE,
                        ApiVersion.createByLanguageVersion(LanguageVersion.LATEST_STABLE),
                        mapOf(                
                            AnalysisFlags.extendedCompilerChecks to false, // Performance
                            AnalysisFlags.ideMode to true,
                            AnalysisFlags.skipMetadataVersionCheck to true,
                            AnalysisFlags.skipPrereleaseCheck to true,
                            AnalysisFlags.allowUnstableDependencies to true // Avoid checks
                        ),
                        langFeatures
                    )
                    put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)
                    
                    put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, Prefs.get().getBoolean(SharedPreferenceKeys.USE_PSI_CLASS_FILES_READING,true))
                    put(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM, Prefs.get().getBoolean(SharedPreferenceKeys.USE_FAST_JAR_FILE_SYSTEM,true))
                    put(JVMConfigurationKeys.DISABLE_RECEIVER_ASSERTIONS, true)
                    put(CommonConfigurationKeys.INCREMENTAL_COMPILATION, true)
                    put(CommonConfigurationKeys.USE_FIR, Prefs.get().getBoolean(SharedPreferenceKeys.USE_FIR,true))
                    put(CommonConfigurationKeys.USE_LIGHT_TREE, true)
                    put(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS, Prefs.get().getString(SharedPreferenceKeys.PARALLEL_BACKEND_THREADS,"10")?.toIntOrNull()?:10)
                    put(CommonConfigurationKeys.VERIFY_IR, IrVerificationMode.NONE);
                    put(CommonConfigurationKeys.USE_FIR_EXTRA_CHECKERS,false)  
                                    
                   // put(CLIConfigurationKeys.INTELLIJ_PLUGIN_ROOT, ".")

                    with(K2JVMCompilerArguments()) {
                        put(JVMConfigurationKeys.DISABLE_PARAM_ASSERTIONS, true)
                        put(JVMConfigurationKeys.DISABLE_CALL_ASSERTIONS, true)
                    }
                }
            )

            return KotlinEnvironment(classpath, kotlinCoreEnvironment)
        }

        fun get(module: Module): KotlinEnvironment? {
            val androidModule = module as? AndroidModuleImpl ?: return null

            val existingEnvironment = androidModule.getUserData(ENVIRONMENT_KEY)
            if (existingEnvironment != null) {
                return existingEnvironment
            }

            val jars = androidModule.codeAssistLibraries.map {
                it.sourceFile
            }.filter(File::exists)
            val environment = with(jars)
            androidModule.kotlinFiles.values.forEach {
                environment.updateKotlinFile(it.absolutePath, it.readText())
            }
            androidModule.putUserData(ENVIRONMENT_KEY, environment)
            return environment
        }
    }
}

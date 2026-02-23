/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.lsp.kotlin

import androidx.core.net.toUri
import com.itsaky.androidide.eventbus.events.editor.ChangeType
import com.itsaky.androidide.eventbus.events.editor.DocumentChangeEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentCloseEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentOpenEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSelectedEvent
import com.tyron.completion.lsp.api.ILanguageClient
import com.tyron.completion.lsp.api.ILanguageServer
import com.tyron.completion.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.kotlin.adapters.toIde
import com.itsaky.androidide.lsp.kotlin.adapters.toLsp4j
import com.tyron.completion.CompletionParameters
import com.tyron.completion.model.CompletionList
import com.tyron.completion.model.DefinitionParams
import com.tyron.completion.model.DefinitionResult
import com.tyron.completion.model.DiagnosticResult
import com.tyron.completion.model.ExpandSelectionParams
import com.tyron.completion.model.references.ReferenceParams
import com.tyron.completion.model.references.ReferenceResult
import com.tyron.completion.model.signatures.SignatureHelp
import com.tyron.completion.model.signatures.SignatureHelpParams
import com.tyron.completion.model.Range
import com.tyron.builder.project.api.AndroidModule
import com.tyron.builder.project.api.Module
import com.tyron.builder.project.api.JavaModule
import com.tyron.builder.project.Project
import com.tyron.builder.BuildModule
//import com.itsaky.androidide.projects.models.bootClassPaths
//import com.itsaky.androidide.projects.models.projectDir
import com.tyron.common.util.DocumentUtils
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.appdevforall.codeonthego.lsp.kotlin.server.KotlinLanguageServer as KtLspServer
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory
import java.nio.file.Path

import org.eclipse.lsp4j.Position as Lsp4jPosition

class KotlinLanguageServer : ILanguageServer {

    private val ktLspServer = KtLspServer()
    private var clientBridge: KotlinLanguageClientBridge? = null
    private var _client: ILanguageClient? = null
    private var _settings: IServerSettings? = null
    private var selectedFile: Path? = null
    private var initialized = false

    override val serverId: String = SERVER_ID

    override val client: ILanguageClient?
        get() = _client

    val settings: IServerSettings
        get() = _settings ?: KotlinServerSettings.getInstance().also { _settings = it }

    companion object {
        const val SERVER_ID = "ide.lsp.kotlin"
        private val log = LoggerFactory.getLogger(KotlinLanguageServer::class.java)
    }

    init {
        applySettings(KotlinServerSettings.getInstance())

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun shutdown() {
        ktLspServer.shutdown().get()
        EventBus.getDefault().unregister(this)
        initialized = false
    }

    override fun connectClient(client: ILanguageClient?) {
        this._client = client
        if (client != null) {
            val positionResolver: PositionToOffsetResolver = { uri ->
                val normalizedUri = normalizeUri(uri)
                val state = ktLspServer.getDocumentManager().get(normalizedUri)
                if (state == null) {
                    log.debug("positionResolver: no document state for URI: {} (normalized: {})", uri, normalizedUri)
                }
                state?.let { it::positionToOffset }
            }
            clientBridge = KotlinLanguageClientBridge(client, positionResolver)
            ktLspServer.connect(clientBridge!!)
        }
    }

    private fun normalizeUri(uri: String): String {
        return try {
            java.net.URI(uri).normalize().toString()
        } catch (e: Exception) {
            uri
        }
    }

    override fun applySettings(settings: IServerSettings?) {
        this._settings = settings
    }

    override fun setupWorkspace(workspace: Module) {
        log.info("setupWithProject called, initialized={}", initialized)
        if (!initialized) {
            loadStdlibIndex()

            val initParams = InitializeParams().apply {
                rootUri = workspace.project.rootFile.toUri().toString()
            }
            ktLspServer.initialize(initParams).get()
            ktLspServer.initialized(null)
            log.info("Kotlin LSP initialized with stdlib index")
            initialized = true
        }
        var javaModule = workspace as? JavaModule?:return
        indexClasspaths(javaModule)
    }

    private fun loadStdlibIndex() {
        try {
            val startTime = System.currentTimeMillis()
            val stdlibStream = javaClass.getResourceAsStream("/stdlib-index.json")
            if (stdlibStream != null) {
                stdlibStream.use { inputStream ->
                    val stdlibIndex = org.appdevforall.codeonthego.lsp.kotlin.index.StdlibIndexLoader.loadFromStream(inputStream)
                    ktLspServer.loadStdlibIndex(stdlibIndex)
                    val elapsed = System.currentTimeMillis() - startTime
                    log.info("Loaded stdlib index: {} symbols in {}ms", stdlibIndex.size, elapsed)
                }
            } else {
                log.warn("stdlib-index.json not found in resources, using minimal index")
            }
        } catch (e: Exception) {
            log.error("Failed to load stdlib-index.json, using minimal index", e)

        }
    }

    private fun indexClasspaths(workspace: JavaModule) {
        log.info("indexClasspaths called, subProjects count={}", workspace.getSubprojects().size)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val classpaths = mutableSetOf<File>()
                val bootClasspaths = mutableSetOf<File>()
                
                    classpaths.addAll(workspace.getLibraries().toMutableList())
                    bootClasspaths.add(BuildModule.getLambdaStubs())
                    bootClasspaths.add(BuildModule.getAndroidJar()) 

                for (project in workspace.getSubprojects()) {
                    log.debug("Checking project: {} (type={})", project.moduleName, project::class.simpleName)
                    if (project is JavaModule) {
                        val projectClasspaths = project.getLibraries().toMutableList()
                        log.debug("Project {} has {} classpath entries", project.moduleName, projectClasspaths.size)
                        classpaths.addAll(projectClasspaths)

                     //   if (project is AndroidModule) {
                     //       val projectBootClasspaths = project.bootClassPaths
                     //       log.debug("Project {} has {} boot classpath entries", project.name, projectBootClasspaths.size)
                     //       bootClasspaths.addAll(projectBootClasspaths)
                     //   }
                    }
                }

                classpaths.addAll(bootClasspaths.filter { it.exists() })

                log.info("Total classpath entries found: {} (including {} boot classpaths)", classpaths.size, bootClasspaths.size)
                if (classpaths.isNotEmpty()) {
                    val files = classpaths.filter { it.exists() }
                    log.info("Indexing {} existing classpath entries for Kotlin LSP", files.size)
                    ktLspServer.setClasspathAsync(files).thenAccept { index ->
                        log.info("Kotlin LSP classpath indexed: {} symbols from {} jars", index.size, index.jarCount)
                    }.exceptionally { e ->
                        log.error("Error in classpath indexing async", e)
                        null
                    }
                } else {
                    log.warn("No classpath entries found for Kotlin LSP")
                }
            } catch (e: Exception) {
                log.error("Error indexing classpaths for Kotlin LSP", e)
            }
        }
    }

    override fun complete(params: CompletionParameters?): CompletionList {
        log.debug("complete() called, params={}", params != null)
        if (params == null || !settings.completionsEnabled()) {
            log.debug("complete() returning EMPTY: params={}, completionsEnabled={}", params != null, settings.completionsEnabled())
            return CompletionList.EMPTY
        }

        if (!DocumentUtils.isKotlinFile(params.file.toPath())) {
            log.debug("complete() returning EMPTY: not a Kotlin file")
            return CompletionList.EMPTY
        }

        val uri = params.file.toUri().toString()

        ktLspServer.getAnalysisScheduler().analyzeSync(uri)

        log.debug("complete() uri={}, position={}:{}, prefix={}", uri, params.line, params.column, params.prefix)
        val lspParams = org.eclipse.lsp4j.CompletionParams().apply {
            textDocument = TextDocumentIdentifier(uri)
            position = Lsp4jPosition(params.line, params.column)
        }

        return try {
            val future = ktLspServer.textDocumentService.completion(lspParams)
            val result = future.get()
            val items = result?.right?.items ?: result?.left ?: emptyList()
            log.debug("complete() got {} items from ktlsp", items.size)
           // CompletionList(items.map { it.toIde(params.prefix ?: "") })
            CompletionList.builder(params.prefix?:"")
            .addItems(items.map { it.toIde(params.prefix ?: "") })
            .build()
              
            
        } catch (e: Exception) {
            log.error("Error during completion", e)
            CompletionList.EMPTY
        }
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        if (!settings.referencesEnabled()) {
            return ReferenceResult(emptyList())
        }

        if (!DocumentUtils.isKotlinFile(params.file)) {
            return ReferenceResult(emptyList())
        }

        val uri = params.file.toUri().toString()
        val lspParams = org.eclipse.lsp4j.ReferenceParams().apply {
            textDocument = TextDocumentIdentifier(uri)
            position = params.position.toLsp4j()
            context = org.eclipse.lsp4j.ReferenceContext(params.includeDeclaration)
        }

        return try {
            val future = ktLspServer.textDocumentService.references(lspParams)
            val locations = future.get() ?: emptyList()
            ReferenceResult(locations.map { it.toIde() })
        } catch (e: Exception) {
            log.error("Error finding references", e)
            ReferenceResult(emptyList())
        }
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        if (!settings.definitionsEnabled()) {
            return DefinitionResult(emptyList())
        }

        if (!DocumentUtils.isKotlinFile(params.file)) {
            return DefinitionResult(emptyList())
        }

        val uri = params.file.toUri().toString()
        val lspParams = org.eclipse.lsp4j.DefinitionParams().apply {
            textDocument = TextDocumentIdentifier(uri)
            position = params.position.toLsp4j()
        }

        return try {
            val future = ktLspServer.textDocumentService.definition(lspParams)
            val result = future.get()
            val locations = result?.left ?: emptyList()
            DefinitionResult(locations.map { it.toIde() })
        } catch (e: Exception) {
            log.error("Error finding definition", e)
            DefinitionResult(emptyList())
        }
    }

    override suspend fun expandSelection(params: ExpandSelectionParams): Range {
        return params.selection
    }

    override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
        if (!settings.signatureHelpEnabled()) {
            return SignatureHelp(emptyList(), -1, -1)
        }

        if (!DocumentUtils.isKotlinFile(params.file)) {
            return SignatureHelp(emptyList(), -1, -1)
        }

        val uri = params.file.toUri().toString()
        val lspParams = org.eclipse.lsp4j.SignatureHelpParams().apply {
            textDocument = TextDocumentIdentifier(uri)
            position = params.position.toLsp4j()
        }

        return try {
            val future = ktLspServer.textDocumentService.signatureHelp(lspParams)
            val result = future.get()
            result?.toIde() ?: SignatureHelp(emptyList(), -1, -1)
        } catch (e: Exception) {
            log.error("Error getting signature help", e)
            SignatureHelp(emptyList(), -1, -1)
        }
    }

    override suspend fun analyze(file: Path): DiagnosticResult {
        log.debug("analyze() called for file: {}", file)

        if (!settings.diagnosticsEnabled() || !settings.codeAnalysisEnabled()) {
            log.debug("analyze() skipped: diagnosticsEnabled={}, codeAnalysisEnabled={}",
                settings.diagnosticsEnabled(), settings.codeAnalysisEnabled())
            return DiagnosticResult.NO_UPDATE
        }

        if (!DocumentUtils.isKotlinFile(file)) {
            log.debug("analyze() skipped: not a Kotlin file")
            return DiagnosticResult.NO_UPDATE
        }

        val uri = file.toUri().toString()
        val state = ktLspServer.getDocumentManager().get(uri)
        if (state == null) {
            log.warn("analyze() skipped: document state not found for URI: {}", uri)
            return DiagnosticResult.NO_UPDATE
        }

        ktLspServer.getAnalysisScheduler().analyzeSync(uri)

        val diagnostics = state.diagnostics
        log.info("analyze() completed: {} diagnostics found for {}", diagnostics.size, file.fileName)

        return DiagnosticResult(file, diagnostics.map { it.toIde(state::positionToOffset) })
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onDocumentOpen(event: DocumentOpenEvent) {
        if (!DocumentUtils.isKotlinFile(event.openedFile)) {
            return
        }

        selectedFile = event.openedFile
        val uri = event.openedFile.toUri().toString()

        log.debug("onDocumentOpen: uri={}, version={}, textLen={}", uri, event.version, event.text.length)

        val params = DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem(uri, "kotlin", event.version, event.text)
        }
        ktLspServer.textDocumentService.didOpen(params)

        analyzeCurrentFileAsync()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onDocumentChange(event: DocumentChangeEvent) {
        if (!DocumentUtils.isKotlinFile(event.changedFile)) {
            return
        }

        val uri = event.changedFile.toUri().toString()

        log.debug("onDocumentChange: uri={}, version={}, changeType={}", uri, event.version, event.changeType)
        log.debug("  changeRange={}, changedText='{}', newText.len={}",
            event.changeRange, event.changedText, event.newText?.length ?: -1)

        val changeText = when (event.changeType) {
            ChangeType.DELETE -> ""
            else -> event.changedText
        }

        val startIndex = event.changeRange.start.index
        val endIndex = if (event.changeType == ChangeType.INSERT) {
            startIndex
        } else {
            event.changeRange.end.index
        }

        log.debug("  using index-based sync: indices=$startIndex-$endIndex (adjusted for {}), text='{}' ({} chars)",
            event.changeType, changeText, changeText.length)

        ktLspServer.didChangeByIndex(
            uri = uri,
            startIndex = startIndex,
            endIndex = endIndex,
            newText = changeText,
            version = event.version
        )
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onDocumentClose(event: DocumentCloseEvent) {
        if (!DocumentUtils.isKotlinFile(event.closedFile)) {
            return
        }

        val uri = event.closedFile.toUri().toString()
        val params = DidCloseTextDocumentParams().apply {
            textDocument = TextDocumentIdentifier(uri)
        }
        ktLspServer.textDocumentService.didClose(params)

        if (selectedFile == event.closedFile) {
            selectedFile = null
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onDocumentSelected(event: DocumentSelectedEvent) {
        if (!DocumentUtils.isKotlinFile(event.selectedFile)) {
            return
        }

        selectedFile = event.selectedFile
        val uri = event.selectedFile.toUri().toString()

        log.debug("onDocumentSelected: uri={}", uri)

        val existingState = ktLspServer.getDocumentManager().get(uri)
        if (existingState == null) {
            log.info("onDocumentSelected: document not open in KtLsp, opening it first: {}", uri)
            log.debug("  available uris: {}", ktLspServer.getDocumentManager().openUris.take(5))
            try {
                val content = event.selectedFile.toFile().readText()
                log.debug("  read {} chars from disk", content.length)
                val params = DidOpenTextDocumentParams().apply {
                    textDocument = TextDocumentItem(uri, "kotlin", 0, content)
                }
                ktLspServer.textDocumentService.didOpen(params)
            } catch (e: Exception) {
                log.error("Failed to open document in KtLsp: {}", uri, e)
            }
        } else {
            log.debug("onDocumentSelected: document already open, version={}, contentLen={}",
                existingState.version, existingState.content.length)
        }

        analyzeCurrentFileAsync()
    }

    private fun analyzeCurrentFileAsync() {
        val file = selectedFile ?: return
        val client = _client ?: return

        CoroutineScope(Dispatchers.Default).launch {
            val result = analyze(file)
            if (result != DiagnosticResult.NO_UPDATE) {
                withContext(Dispatchers.Main) {
                    client.publishDiagnostics(result)
                }
            }
        }
    }
}

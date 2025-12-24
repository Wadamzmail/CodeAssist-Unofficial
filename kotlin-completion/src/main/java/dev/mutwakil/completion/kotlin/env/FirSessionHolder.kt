package dev.mutwakil.completion.kotlin.env

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.session.FirSessionFactory
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.moduleData.FirModuleDataImpl
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.phase.FirResolvePhase
import org.jetbrains.kotlin.fir.analysis.checkers.FirSessionConfigurator

class FirSessionHolder(
    private val configuration: CompilerConfiguration
) {

    private val scopeSession = ScopeSession()

    private val moduleData = FirModuleDataImpl(
        Name.identifier("fir-completion"),
        dependencies = emptyList(),
        dependsOnDependencies = emptyList(),
        friendDependencies = emptyList()
    )

    val session: FirSession by lazy {
        FirSessionFactory.createSession(
            moduleData,
            scopeSession,
            configuration
        ).also {
            FirSessionConfigurator.configure(it)
        }
    }

    fun resolveTypes(file: FirFile) {
        file.resolve(FirResolvePhase.TYPES)
    }
}
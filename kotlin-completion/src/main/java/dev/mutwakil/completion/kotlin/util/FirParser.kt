package dev.mutwakil.completion.kotlin.util

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.lightTree.LightTree2Fir
import org.jetbrains.kotlin.fir.session.FirSession
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.builder.RawFirBuilderContext
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

object FirParser {

    fun parse(
        code: String,
        session: FirSession
    ): FirFile {
        val psiFactory = KtPsiFactory(session.moduleData.session.project)
        val ktFile: KtFile = psiFactory.createFile(code)

        val builder = RawFirBuilder(
            session,
            RawFirBuilderContext(session, emptyList())
        )

        return builder.buildFirFile(ktFile)
    }
}
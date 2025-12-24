package dev.mutwakil.completion.kotlin.fir

import com.tyron.completion.model.DrawableKind
import org.jetbrains.kotlin.analysis.api.symbols.*

object FirIconMapper {

    fun iconFrom(symbol: KaSymbol): DrawableKind =
        when (symbol) {
            is KaFunctionSymbol -> DrawableKind.Method

            // variables تشمل properties + locals + parameters
            is KaVariableSymbol -> DrawableKind.Attribute

            // classes + objects + interfaces
            is KaClassLikeSymbol -> DrawableKind.Class

            is KaPackageSymbol -> DrawableKind.Package

            else -> DrawableKind.Snippet
        }
}
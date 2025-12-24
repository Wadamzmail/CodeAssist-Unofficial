package dev.mutwakil.completion.kotlin.fir

import com.tyron.completion.model.DrawableKind
import org.jetbrains.kotlin.analysis.api.symbols.*

object FirIconMapper {

    fun iconFrom(symbol: KtSymbol): DrawableKind =
        when (symbol) {
            is KtFunctionSymbol -> DrawableKind.Method
            is KtPropertySymbol -> DrawableKind.Attribute
            is KtValueParameterSymbol -> DrawableKind.LocalVariable
            is KtClassOrObjectSymbol -> DrawableKind.Class
            is KtPackageSymbol -> DrawableKind.Package
            is KtTypeParameterSymbol -> DrawableKind.Class
            else -> DrawableKind.Snippet
        }
}
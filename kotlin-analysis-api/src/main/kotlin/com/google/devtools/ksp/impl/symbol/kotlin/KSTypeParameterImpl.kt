/*
 * Copyright 2022 Google LLC
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.KSObjectCache
import com.google.devtools.ksp.processing.impl.KSNameImpl
import com.google.devtools.ksp.symbol.*
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol

class KSTypeParameterImpl private constructor(internal val ktTypeParameterSymbol: KtTypeParameterSymbol) :
    KSTypeParameter,
    AbstractKSDeclarationImpl(ktTypeParameterSymbol),
    KSExpectActual by KSExpectActualImpl(ktTypeParameterSymbol) {
    companion object : KSObjectCache<KtTypeParameterSymbol, KSTypeParameterImpl>() {
        fun getCached(ktTypeParameterSymbol: KtTypeParameterSymbol) =
            cache.getOrPut(ktTypeParameterSymbol) { KSTypeParameterImpl(ktTypeParameterSymbol) }
    }

    override val name: KSName by lazy {
        KSNameImpl.getCached(ktTypeParameterSymbol.name.asString())
    }

    override val variance: Variance by lazy {
        when (ktTypeParameterSymbol.variance) {
            org.jetbrains.kotlin.types.Variance.IN_VARIANCE -> Variance.COVARIANT
            org.jetbrains.kotlin.types.Variance.OUT_VARIANCE -> Variance.CONTRAVARIANT
            org.jetbrains.kotlin.types.Variance.INVARIANT -> Variance.INVARIANT
        }
    }

    override val isReified: Boolean = ktTypeParameterSymbol.isReified

    override val bounds: Sequence<KSTypeReference> by lazy {
        ktTypeParameterSymbol.upperBounds.asSequence().mapIndexed { index, type ->
            KSTypeReferenceImpl.getCached(type, this@KSTypeParameterImpl, index)
        }
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val qualifiedName: KSName? by lazy {
        KSNameImpl.getCached("${this.parentDeclaration!!.qualifiedName!!.asString()}.${simpleName.asString()}")
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeParameter(this, data)
    }
}

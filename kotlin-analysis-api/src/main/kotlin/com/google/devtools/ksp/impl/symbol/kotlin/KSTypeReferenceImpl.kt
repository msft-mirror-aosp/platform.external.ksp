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

import com.google.devtools.ksp.ExceptionMessage
import com.google.devtools.ksp.IdKeyTriple
import com.google.devtools.ksp.KSObjectCache
import com.google.devtools.ksp.symbol.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeParameter

class KSTypeReferenceImpl private constructor(
    private val ktType: KtType,
    override val parent: KSNode?,
    private val index: Int
) : KSTypeReference {
    companion object : KSObjectCache<IdKeyTriple<KtType, KSNode?, Int>, KSTypeReference>() {
        fun getCached(type: KtType, parent: KSNode? = null, index: Int = -1): KSTypeReference =
            cache.getOrPut(IdKeyTriple(type, parent, index)) { KSTypeReferenceImpl(type, parent, index) }
    }

    override val element: KSReferenceElement? by lazy {
        if (parent == null || parent.origin == Origin.SYNTHETIC) {
            null
        } else {
            when (ktType) {
                is KtFunctionalType -> KSCallableReferenceImpl.getCached(ktType, this@KSTypeReferenceImpl)
                is KtDynamicType -> KSDynamicReferenceImpl.getCached(this@KSTypeReferenceImpl)
                is KtUsualClassType -> KSClassifierReferenceImpl.getCached(ktType, this@KSTypeReferenceImpl)
                is KtFlexibleType -> KSClassifierReferenceImpl.getCached(
                    ktType.lowerBound as KtUsualClassType,
                    this@KSTypeReferenceImpl
                )
                is KtErrorType -> null
                is KtTypeParameterType -> null
                else -> throw IllegalStateException("Unexpected type element ${ktType.javaClass}, $ExceptionMessage")
            }
        }
    }

    override fun resolve(): KSType {
        // TODO: non exist type returns KtNonErrorClassType, check upstream for KtClassErrorType usage.
        return if (
            analyze {
                ktType is KtClassErrorType || (ktType.classifierSymbol() == null)
            }
        ) {
            KSErrorType
        } else {
            KSTypeImpl.getCached(ktType)
        }
    }

    override val annotations: Sequence<KSAnnotation> by lazy {
        ktType.annotations()
    }

    override val origin: Origin = parent?.origin ?: Origin.SYNTHETIC

    override val location: Location by lazy {
        if (index != -1) {
            parent?.location ?: NonExistLocation
        } else {
            when (parent) {
                is KSClassDeclarationImpl -> {
                    when (val psi = parent.ktClassOrObjectSymbol.psi) {
                        is KtClassOrObject -> psi.superTypeListEntries.get(index).toLocation()
                        is PsiClass -> (psi as? PsiClassReferenceType)?.reference?.toLocation() ?: NonExistLocation
                        else -> NonExistLocation
                    }
                }
                is KSTypeParameterImpl -> {
                    when (val psi = parent.ktTypeParameterSymbol.psi) {
                        is KtTypeParameter -> parent.location
                        is PsiTypeParameter -> (psi.extendsListTypes[index] as? PsiClassReferenceType)
                            ?.reference?.toLocation() ?: NonExistLocation
                        else -> NonExistLocation
                    }
                }
                else -> NonExistLocation
            }
        }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeReference(this, data)
    }

    override val modifiers: Set<Modifier>
        get() = if (ktType is KtFunctionalType && ktType.isSuspend) {
            setOf(Modifier.SUSPEND)
        } else {
            emptySet()
        }

    override fun toString(): String {
        return ktType.render()
    }
}

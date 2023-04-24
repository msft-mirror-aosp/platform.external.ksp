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
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotationApplication
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.KtNamedAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.*
import org.jetbrains.kotlin.psi.KtParameter

class KSAnnotationImpl private constructor(private val annotationApplication: KtAnnotationApplication) : KSAnnotation {
    companion object : KSObjectCache<KtAnnotationApplication, KSAnnotationImpl>() {
        fun getCached(annotationApplication: KtAnnotationApplication) =
            cache.getOrPut(annotationApplication) { KSAnnotationImpl(annotationApplication) }
    }

    override val annotationType: KSTypeReference by lazy {
        analyze {
            KSTypeReferenceImpl.getCached(buildClassType(annotationApplication.classId!!))
        }
    }

    override val arguments: List<KSValueArgument> by lazy {
        val presentArgs = annotationApplication.arguments.map { KSValueArgumentImpl.getCached(it) }
        val presentNames = presentArgs.mapNotNull { it.name?.asString() }
        val absentArgs = analyze {
            annotationApplication.classId?.toKtClassSymbol()?.let { symbol ->
                symbol.getMemberScope().getConstructors().singleOrNull()?.let {
                    it.valueParameters.filter { valueParameter ->
                        valueParameter.name.asString() !in presentNames
                    }.mapNotNull { valueParameterSymbol ->
                        valueParameterSymbol.getDefaultValue()?.let { constantValue ->
                            KSValueArgumentImpl.getCached(
                                KtNamedAnnotationValue(
                                    valueParameterSymbol.name, KtConstantAnnotationValue(constantValue)
                                )
                            )
                        }
                    }
                }
            } ?: emptyList<KSValueArgument>()
        }
        presentArgs + absentArgs
    }

    override val defaultArguments: List<KSValueArgument>
        get() = TODO("Not yet implemented")

    override val shortName: KSName by lazy {
        KSNameImpl.getCached(annotationApplication.classId!!.shortClassName.asString())
    }

    override val useSiteTarget: AnnotationUseSiteTarget? by lazy {
        when (annotationApplication.useSiteTarget) {
            null -> null
            FILE -> AnnotationUseSiteTarget.FILE
            PROPERTY -> AnnotationUseSiteTarget.PROPERTY
            FIELD -> AnnotationUseSiteTarget.FIELD
            PROPERTY_GETTER -> AnnotationUseSiteTarget.GET
            PROPERTY_SETTER -> AnnotationUseSiteTarget.SET
            RECEIVER -> AnnotationUseSiteTarget.RECEIVER
            CONSTRUCTOR_PARAMETER -> AnnotationUseSiteTarget.PARAM
            SETTER_PARAMETER -> AnnotationUseSiteTarget.SETPARAM
            PROPERTY_DELEGATE_FIELD -> AnnotationUseSiteTarget.DELEGATE
        }
    }

    override val origin: Origin = Origin.KOTLIN

    override val location: Location by lazy {
        annotationApplication.psi?.toLocation() ?: NonExistLocation
    }

    override val parent: KSNode?
        get() = TODO("Not yet implemented")

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitAnnotation(this, data)
    }

    override fun toString(): String {
        return "@${shortName.asString()}"
    }
}

internal fun KtValueParameterSymbol.getDefaultValue(): KtConstantValue? {
    return this.psi?.let {
        when (it) {
            is KtParameter -> analyze {
                it.defaultValue?.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)
            }
            else -> throw IllegalStateException("Unhandled default value type ${it.javaClass}")
        }
    }
}

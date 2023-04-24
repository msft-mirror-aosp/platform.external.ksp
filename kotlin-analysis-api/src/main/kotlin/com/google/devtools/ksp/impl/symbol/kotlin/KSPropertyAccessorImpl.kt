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
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.toKSModifiers
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyGetterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySetterSymbol
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtModifierListOwner

abstract class KSPropertyAccessorImpl(
    private val ktPropertyAccessorSymbol: KtPropertyAccessorSymbol,
    override val receiver: KSPropertyDeclaration
) : KSPropertyAccessor {

    override val annotations: Sequence<KSAnnotation> by lazy {
        ktPropertyAccessorSymbol.annotations.asSequence()
            .filter { it.useSiteTarget != AnnotationUseSiteTarget.SETTER_PARAMETER }
            .map { KSAnnotationImpl.getCached(it) }
            .plus(findAnnotationFromUseSiteTarget())
    }

    internal val originalAnnotations = ktPropertyAccessorSymbol.annotations()

    override val location: Location by lazy {
        ktPropertyAccessorSymbol.psi?.toLocation() ?: NonExistLocation
    }

    override val modifiers: Set<Modifier> by lazy {
        ((ktPropertyAccessorSymbol.psi as? KtModifierListOwner)?.toKSModifiers() ?: emptySet()).let {
            if (origin == Origin.SYNTHETIC &&
                (receiver.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE
            ) {
                it + Modifier.ABSTRACT
            } else {
                it
            }
        }
    }

    override val origin: Origin by lazy {
        val symbolOrigin = mapAAOrigin(ktPropertyAccessorSymbol)
        if (symbolOrigin == Origin.KOTLIN && ktPropertyAccessorSymbol.psi == null) {
            Origin.SYNTHETIC
        } else {
            symbolOrigin
        }
    }

    override val parent: KSNode?
        get() = ktPropertyAccessorSymbol.getContainingKSSymbol()
}

class KSPropertySetterImpl private constructor(
    owner: KSPropertyDeclaration,
    setter: KtPropertySetterSymbol
) : KSPropertyAccessorImpl(setter, owner), KSPropertySetter {
    companion object : KSObjectCache<Pair<KSPropertyDeclaration, KtPropertySetterSymbol>, KSPropertySetterImpl>() {
        fun getCached(owner: KSPropertyDeclaration, setter: KtPropertySetterSymbol) =
            cache.getOrPut(Pair(owner, setter)) { KSPropertySetterImpl(owner, setter) }
    }

    override val parameter: KSValueParameter by lazy {
        KSValueParameterImpl.getCached(setter.parameter, this)
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertySetter(this, data)
    }

    override fun toString(): String {
        return "$receiver.setter()"
    }
}

class KSPropertyGetterImpl private constructor(
    owner: KSPropertyDeclaration,
    getter: KtPropertyGetterSymbol
) : KSPropertyAccessorImpl(getter, owner), KSPropertyGetter {
    companion object : KSObjectCache<Pair<KSPropertyDeclaration, KtPropertyGetterSymbol>, KSPropertyGetterImpl>() {
        fun getCached(owner: KSPropertyDeclaration, getter: KtPropertyGetterSymbol) =
            cache.getOrPut(Pair(owner, getter)) { KSPropertyGetterImpl(owner, getter) }
    }

    override val returnType: KSTypeReference? by lazy {
        KSTypeReferenceImpl.getCached(getter.returnType, this@KSPropertyGetterImpl)
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyGetter(this, data)
    }

    override fun toString(): String {
        return "$receiver.getter()"
    }
}

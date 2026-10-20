package com.dddheroes.sdk.axon

import org.axonframework.common.nullability.Nullability
import org.axonframework.common.nullability.NullabilityResolver
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Parameter
import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.kotlinFunction

/**
 * [NullabilityResolver] that reports the nullability Kotlin declared for a parameter, so a nullable type such as
 * `eventSourced: MyState?` is honored exactly like a Java `@Nullable` parameter. This lets `@InjectEntity` command
 * handler parameters stay idiomatic Kotlin (plain `?`) instead of carrying a jspecify `@Nullable` annotation plus the
 * `-Xemit-jvm-type-annotations` compiler flag.
 *
 * Kotlin does not express nullability through a runtime-visible annotation: `T?` compiles to
 * `org.jetbrains.annotations.Nullable`, which has CLASS retention and is absent from the class file at runtime.
 * Reflecting over the JVM signature can never see it, so this resolver reads the declaration as Kotlin models it.
 *
 * This mirrors the `KotlinReflectNullabilityResolver` shipped by the axon-kotlin extension (since 5.3.0). We keep a
 * local copy rather than depend on the extension because the extension also registers a
 * `KotlinReflectPropertyAccessStrategy` that boxes value-class ids (e.g. `CalendarId`), which breaks this project's
 * repository lookups that register ids under the unboxed `String`. The two extension points are independent, so we
 * take only the nullability resolver via [java.util.ServiceLoader]
 * (see META-INF/services/org.axonframework.common.nullability.NullabilityResolver).
 *
 * Reports [Nullability.UNKNOWN] and defers to the built-in annotation check when the declaring class was not compiled
 * by Kotlin, when the declaration cannot be mapped to a Kotlin function, or when the JVM parameter maps to no Kotlin
 * value parameter (such as the trailing `Continuation` of a `suspend` function).
 */
class KotlinReflectNullabilityResolver : NullabilityResolver {

    override fun resolve(parameter: Parameter): Nullability {
        val executable = parameter.declaringExecutable
        // kotlin-reflect maps Java members too, but reports their platform types as non-null. Without this guard
        // every Java parameter would resolve to NON_NULL, short-circuiting the annotation check meant to handle them.
        if (!executable.declaringClass.isKotlinClass()) {
            return Nullability.UNKNOWN
        }
        val function = executable.kotlinFunctionOrNull() ?: return Nullability.UNKNOWN
        val valueParameters = function.valueParameters
        val index = executable.parameters.indexOf(parameter) - executable.headOffset()
        if (index !in valueParameters.indices) {
            return Nullability.UNKNOWN
        }
        return if (valueParameters[index].type.isMarkedNullable) Nullability.NULLABLE else Nullability.NON_NULL
    }

    /**
     * Resolving the Kotlin declaration throws rather than returning null for some synthetic members, so both outcomes
     * are folded into null.
     */
    private fun Executable.kotlinFunctionOrNull(): KFunction<*>? = runCatching {
        when (this) {
            is Method -> kotlinFunction
            is Constructor<*> -> kotlinFunction
        }
    }.getOrNull()

    /**
     * The number of JVM parameters preceding the first Kotlin value parameter. Only constructors carry any: an enum
     * constructor is handed its name and ordinal, and an inner class constructor its outer instance.
     */
    private fun Executable.headOffset(): Int {
        if (this !is Constructor<*>) {
            return 0
        }
        return when {
            declaringClass.isEnum -> ENUM_CONSTRUCTOR_HEAD_PARAMETERS
            declaringClass.isMemberClass && !Modifier.isStatic(declaringClass.modifiers) -> 1
            else -> 0
        }
    }

    private fun Class<*>.isKotlinClass(): Boolean = isAnnotationPresent(Metadata::class.java)

    private companion object {
        /** An enum constructor receives `(String name, int ordinal)` before its declared parameters. */
        private const val ENUM_CONSTRUCTOR_HEAD_PARAMETERS = 2
    }
}

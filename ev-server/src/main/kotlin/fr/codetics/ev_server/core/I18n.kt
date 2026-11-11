// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.core

import jakarta.servlet.http.HttpServletRequest
import java.util.Locale
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver

object I18n {
    val DEFAULT: Locale = Locale.FRENCH
    val SUPPORTED: List<Locale> = listOf(Locale.FRENCH, Locale.ENGLISH, Locale.of("es"))
    val LANGUAGES: Set<String> = SUPPORTED.map { it.language }.toSet()

    val resolver: AcceptHeaderLocaleResolver =
        AcceptHeaderLocaleResolver().apply {
            supportedLocales = SUPPORTED
            setDefaultLocale(DEFAULT)
        }

    private val override = ThreadLocal<Locale?>()

    @Volatile var source: MessageSource = fallbackSource()

    fun locale(): Locale {
        override.get()?.let { return it }
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        return request?.let(::resolve) ?: DEFAULT
    }

    fun resolve(request: HttpServletRequest): Locale = of(resolver.resolveLocale(request).language)

    fun requested(): String? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.let(::requested)

    fun requested(request: HttpServletRequest): String? =
        request.getHeader("Accept-Language")?.takeIf { it.isNotBlank() }?.let { resolve(request).language }

    fun of(language: String?): Locale {
        val code = language?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
        return SUPPORTED.firstOrNull { it.language == code } ?: DEFAULT
    }

    fun get(code: String, vararg args: Any?): String = get(locale(), code, *args)

    fun get(locale: Locale, code: String, vararg args: Any?): String {
        val values = args.map { it?.toString() ?: "" }.toTypedArray()
        return try {
            source.getMessage(code, values, of(locale.language))
        } catch (_: NoSuchMessageException) {
            code
        }
    }

    fun <T> with(language: String?, block: () -> T): T = with(of(language), block)

    fun <T> with(locale: Locale, block: () -> T): T {
        val previous = override.get()
        override.set(of(locale.language))
        try {
            return block()
        } finally {
            if (previous == null) override.remove() else override.set(previous)
        }
    }

    private fun fallbackSource(): MessageSource =
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
            setFallbackToSystemLocale(false)
            setAlwaysUseMessageFormat(true)
        }
}

fun msg(code: String, vararg args: Any?): String = I18n.get(code, *args)

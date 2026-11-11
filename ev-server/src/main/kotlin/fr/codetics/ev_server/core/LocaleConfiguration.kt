// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.core

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver

@Configuration(proxyBeanMethods = false)
class LocaleConfiguration(messageSource: MessageSource) {
    init {
        I18n.source = messageSource
    }

    @Bean
    fun localeResolver(): LocaleResolver = I18n.resolver
}

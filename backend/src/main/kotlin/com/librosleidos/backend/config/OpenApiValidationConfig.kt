package com.librosleidos.backend.config

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.report.LevelResolver
import com.atlassian.oai.validator.report.ValidationReport
import com.atlassian.oai.validator.springmvc.OpenApiValidationFilter
import com.atlassian.oai.validator.springmvc.OpenApiValidationInterceptor
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Validates every request under `/api` against `openapi.yaml`. The filter runs at order 0, after the
 * Spring Security chain (-100), so anonymous callers get 401 before any schema is consulted.
 * A request for a path the spec does not declare is not a validation error: Spring answers 404.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiValidationConfig : WebMvcConfigurer {
    private val validator: OpenApiInteractionValidator =
        OpenApiInteractionValidator.createForSpecificationUrl(
            ClassPathResource(SPEC).url.toString()
        )
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request.path.missing", ValidationReport.Level.IGNORE)
                    .build()
            )
            .build()

    @Bean
    fun openApiValidationFilter(): FilterRegistrationBean<OpenApiValidationFilter> =
        FilterRegistrationBean(OpenApiValidationFilter(true, false)).apply {
            order = 0
            addUrlPatterns("/api/*")
        }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(OpenApiValidationInterceptor(validator)).addPathPatterns("/api/**")
    }

    private companion object {
        const val SPEC = "openapi.yaml"
    }
}

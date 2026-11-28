package org.snippetsearcher.snippet.rule

import org.snippetsearcher.snippet.common.CurrentUser
import org.snippetsearcher.snippet.language.PRINTSCRIPT
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

const val JOB_ID_HEADER = "X-Job-Id"

// {type} acepta formatting/linting (rutas de printscript-ui) y también format/lint
@RestController
@RequestMapping("/rules")
class RuleController(
    private val service: RuleService,
) {
    @GetMapping("/{type}")
    fun getRules(
        @CurrentUser userId: String,
        @PathVariable type: String,
        @RequestParam(defaultValue = PRINTSCRIPT) language: String,
    ): List<RuleResponse> = service.getRules(userId, type, language)

    // Las reglas se guardan en el momento, por eso 200 con las reglas guardadas (lo que espera la UI).
    // Lo único asincrónico es el reproceso masivo: su job se sigue por Location / X-Job-Id -> GET /jobs/{id}
    @PutMapping("/{type}")
    fun updateRules(
        @CurrentUser userId: String,
        @PathVariable type: String,
        @RequestParam(defaultValue = PRINTSCRIPT) language: String,
        @RequestBody updates: List<UpdateRuleRequest>,
    ): ResponseEntity<List<RuleResponse>> {
        val result = service.updateRules(userId, type, language, updates)
        return ResponseEntity
            .ok()
            .location(URI.create("/jobs/${result.jobId}"))
            .header(JOB_ID_HEADER, result.jobId.toString())
            .body(result.rules)
    }
}

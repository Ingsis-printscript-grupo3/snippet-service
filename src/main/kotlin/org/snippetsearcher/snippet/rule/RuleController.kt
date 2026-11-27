package org.snippetsearcher.snippet.rule

import org.snippetsearcher.snippet.common.CurrentUser
import org.snippetsearcher.snippet.language.PRINTSCRIPT
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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

    @PutMapping("/{type}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun updateRules(
        @CurrentUser userId: String,
        @PathVariable type: String,
        @RequestParam(defaultValue = PRINTSCRIPT) language: String,
        @RequestBody updates: List<UpdateRuleRequest>,
    ): BulkJobCreatedResponse = service.updateRules(userId, type, language, updates)
}

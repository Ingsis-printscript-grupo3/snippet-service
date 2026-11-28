package org.snippetsearcher.snippet.test

import org.snippetsearcher.snippet.common.SnippetTestNotFoundException
import org.snippetsearcher.snippet.language.ExecutionResult
import org.snippetsearcher.snippet.language.LanguageRegistry
import org.snippetsearcher.snippet.snippet.SnippetAccess
import org.snippetsearcher.snippet.storage.ContentStore
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

private const val ONLY_OWNER = "Solo el owner puede modificar los tests del snippet"

// US8 y US9. El permiso sobre los tests es el del snippet: el owner los crea y modifica,
// y quien tiene lectura los ve y los corre (US7)
@Service
class SnippetTestService(
    private val tests: SnippetTestRepository,
    private val access: SnippetAccess,
    private val languages: LanguageRegistry,
    private val contents: ContentStore,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun list(
        userId: String,
        snippetId: UUID,
    ): List<SnippetTestResponse> {
        access.readable(snippetId, userId)
        // la tabla no guarda un orden: por nombre queda estable entre pedidos
        return tests.findBySnippetId(snippetId).map { it.toResponse() }.sortedBy { it.name.lowercase() }
    }

    // un test que no pasa se puede guardar igual (US8): crearlo no lo corre
    @Transactional
    fun create(
        userId: String,
        snippetId: UUID,
        request: SnippetTestRequest,
    ): SnippetTestResponse {
        request.requireValidName()
        access.owned(snippetId, userId, ONLY_OWNER)
        val test = SnippetTest(UUID.randomUUID(), snippetId, request.name, json(request.input), json(request.output))
        return tests.save(test).toResponse()
    }

    @Transactional
    fun update(
        userId: String,
        snippetId: UUID,
        testId: UUID,
        request: SnippetTestRequest,
    ): SnippetTestResponse {
        request.requireValidName()
        access.owned(snippetId, userId, ONLY_OWNER)
        val test = find(snippetId, testId)
        test.name = request.name
        test.inputsJson = json(request.input)
        test.expectedOutputsJson = json(request.output)
        // el resultado guardado era de los valores anteriores
        test.status = TestStatus.PENDING
        test.lastOutputJson = null
        test.lastRunAt = null
        return test.toResponse()
    }

    @Transactional
    fun delete(
        userId: String,
        snippetId: UUID,
        testId: UUID,
    ) {
        access.owned(snippetId, userId, ONLY_OWNER)
        tests.delete(find(snippetId, testId))
    }

    // sin transaccion: no escribe nada y no hay que retener una conexion mientras se espera la ejecucion
    fun run(
        userId: String,
        snippetId: UUID,
        request: RunTestRequest,
    ): TestRunResponse {
        val snippet = access.readable(snippetId, userId)
        val content = contents.get(snippet.contentKey)
        val result = languages.get(snippet.language).execute(snippet.version, content, request.input)
        return TestRunResponse(statusOf(result, request.output), result.outputs, result.errors)
    }

    // el mismo criterio que el worker que corre los tests por la cola (US16), en el printscript-service
    private fun statusOf(
        result: ExecutionResult,
        expected: List<String>,
    ): TestStatus =
        when {
            result.errors.isNotEmpty() -> TestStatus.ERROR
            result.outputs == expected -> TestStatus.PASSED
            else -> TestStatus.FAILED
        }

    // el id de un test de otro snippet no sirve: el permiso se chequeo sobre este
    private fun find(
        snippetId: UUID,
        testId: UUID,
    ): SnippetTest =
        tests.findByIdOrNull(testId)?.takeIf { it.snippetId == snippetId }
            ?: throw SnippetTestNotFoundException(testId)

    private fun json(values: List<String>): String = objectMapper.writeValueAsString(values)

    private fun values(json: String): List<String> = objectMapper.readValue(json)

    private fun SnippetTest.toResponse() =
        SnippetTestResponse(
            id,
            name,
            values(inputsJson),
            values(expectedOutputsJson),
            status,
            lastOutputJson?.let { values(it) },
            lastRunAt,
        )
}

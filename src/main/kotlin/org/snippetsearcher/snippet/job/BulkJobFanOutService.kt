package org.snippetsearcher.snippet.job

import org.snippetsearcher.snippet.language.RuleType
import org.snippetsearcher.snippet.permission.PermissionClient
import org.snippetsearcher.snippet.permission.Relation
import org.snippetsearcher.snippet.rule.RuleService
import org.snippetsearcher.snippet.snippet.LintStatus
import org.snippetsearcher.snippet.snippet.Snippet
import org.snippetsearcher.snippet.snippet.SnippetRepository
import org.snippetsearcher.snippet.storage.ContentStore
import org.snippetsearcher.snippet.streams.BulkJobRequestMessage
import org.snippetsearcher.snippet.streams.FormatRequestMessage
import org.snippetsearcher.snippet.streams.LintRequestMessage
import org.snippetsearcher.snippet.streams.StreamNames
import org.snippetsearcher.snippet.streams.StreamProducer
import org.snippetsearcher.snippet.streams.publishAfterCommit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BulkJobFanOutService(
    private val store: JobTrackingStore,
    private val permissions: PermissionClient,
    private val snippets: SnippetRepository,
    private val contents: ContentStore,
    private val ruleService: RuleService,
    private val producer: StreamProducer,
) {
    // Los pedidos salen a Redis recien despues del commit. Un worker puede contestar en milisegundos, y si
    // el resultado llega antes de que el item exista en la base se descarta y el job no termina nunca.
    // Si la publicacion falla, la excepcion sube, el mensaje no se confirma y el reparto se repite:
    // los items ya existen y un pedido duplicado se ignora porque su item ya no esta pendiente
    @Transactional
    fun fanOut(msg: BulkJobRequestMessage) {
        val job = store.findJob(msg.jobId) ?: return
        val ownedIds = permissions.snippetIds(msg.userId, Relation.OWNED)
        val targets =
            if (ownedIds.isEmpty()) {
                emptyList()
            } else {
                snippets.findAllById(ownedIds).filter { it.language == msg.language }
            }
        job.total = targets.size
        if (targets.isNotEmpty()) {
            val type = RuleType.valueOf(msg.type)
            val rulesMap = ruleService.buildRulesMap(msg.userId, msg.language, type)
            targets.forEach { dispatchSnippet(job.id, msg.userId, type, it, rulesMap) }
        }
        // sin snippets, o en un reparto repetido donde ya estaban todos resueltos, no va a llegar ningun
        // resultado que cierre el job: se cierra aca
        job.closeIfResolved()
    }

    private fun dispatchSnippet(
        jobId: UUID,
        userId: String,
        type: RuleType,
        snippet: Snippet,
        rulesMap: Map<String, Any?>,
    ) {
        if (!store.claimPendingItem(jobId, snippet.id)) return
        val content = contents.get(snippet.contentKey)
        when (type) {
            RuleType.LINT -> publishLint(jobId, userId, snippet, content, rulesMap)
            RuleType.FORMAT -> publishFormat(jobId, userId, snippet, content, rulesMap)
        }
    }

    private fun publishLint(
        jobId: UUID,
        userId: String,
        snippet: Snippet,
        content: String,
        rulesMap: Map<String, Any?>,
    ) {
        snippet.lintStatus = LintStatus.PENDING
        val msg =
            LintRequestMessage(
                jobId = jobId,
                userId = userId,
                snippetId = snippet.id,
                snippetVersion = snippet.updatedAt,
                language = snippet.language,
                version = snippet.version,
                content = content,
                rules = rulesMap,
            )
        producer.publishAfterCommit(StreamNames.LINT_REQUESTS, msg)
    }

    private fun publishFormat(
        jobId: UUID,
        userId: String,
        snippet: Snippet,
        content: String,
        rulesMap: Map<String, Any?>,
    ) {
        val msg =
            FormatRequestMessage(
                jobId = jobId,
                userId = userId,
                snippetId = snippet.id,
                snippetVersion = snippet.updatedAt,
                language = snippet.language,
                version = snippet.version,
                content = content,
                rules = rulesMap,
            )
        producer.publishAfterCommit(StreamNames.FORMAT_REQUESTS, msg)
    }
}

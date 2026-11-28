package org.snippetsearcher.snippet.support

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.jdbc.JdbcTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

// La base y los fakes los comparten todas las clases de test: lo que una deja, otra lo ve.
// Junta en un lugar como dejar todo como recien levantado. Viene con FakeCollaborators
class TestEnvironment(
    private val jdbc: JdbcTemplate,
    transactions: PlatformTransactionManager,
    val permissions: FakePermissionClient,
    val contents: FakeContentStore,
    val language: FakeLanguageService,
    val producer: FakeStreamProducer,
) {
    private val otherConnection =
        TransactionTemplate(transactions).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    // las tablas hijas (violaciones, items de job, tests) se van solas por el on delete cascade
    fun reset() {
        JdbcTestUtils.deleteFromTables(jdbc, "bulk_job", "rule_config", "snippet")
        permissions.reset()
        contents.contents.clear()
        language.reset()
        producer.reset()
    }

    // cuantas filas ve otra conexion, es decir, cuantas estan confirmadas en este instante
    fun committedRows(table: String): Int = otherConnection.execute { JdbcTestUtils.countRowsInTable(jdbc, table) } ?: 0
}

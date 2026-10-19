package com.example.coletornotificacao

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica que a migração v1 -> v2 preserva as notificações já existentes e
 * preenche os novos campos de sincronização.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migra_1_para_2_preserva_dados_antigos() {
        // Cria o banco na versão 1 com o esquema antigo e insere um registro.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO notifications (packageName, title, message, createdAt)
                VALUES ('com.nu.production', 'Compra', 'R$ 10', 1000)
                """.trimIndent()
            )
            close()
        }

        // Aplica a migração para a versão 2.
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, AppDatabase.MIGRATION_1_2
        )

        val cursor = db.query(
            "SELECT packageName, title, message, createdAt, syncState, stableId, attemptCount FROM notifications"
        )
        assertEquals(1, cursor.count)
        cursor.moveToFirst()

        assertEquals("com.nu.production", cursor.getString(cursor.getColumnIndexOrThrow("packageName")))
        assertEquals("Compra", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        assertEquals("R$ 10", cursor.getString(cursor.getColumnIndexOrThrow("message")))
        assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        // Novos campos preenchidos de forma segura.
        assertEquals("PENDING", cursor.getString(cursor.getColumnIndexOrThrow("syncState")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("attemptCount")))
        val stableId = cursor.getString(cursor.getColumnIndexOrThrow("stableId"))
        assertTrue("stableId deve ser preenchido", stableId.isNotBlank())
        cursor.close()
    }

    /**
     * Verifica que a migração v2 -> v3 preserva as notificações existentes e cria
     * o módulo de despesas (cards, categories, expenses, merchant_categories) com
     * as categorias iniciais semeadas (RF07). Operação puramente aditiva.
     */
    @Test
    fun migra_2_para_3_preserva_notificacoes_e_cria_modulo_despesas() {
        // Cria o banco na v2 e insere uma notificação (esquema v2 completo).
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO notifications
                    (stableId, packageName, title, message, createdAt, syncState, attemptCount)
                VALUES ('abc', 'com.nu.production', 'Compra', 'R$ 10', 1000, 'PENDING', 0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        // 1) Notificação preservada.
        db.query("SELECT COUNT(*) FROM notifications WHERE stableId = 'abc'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // 2) Novas tabelas existem e aceitam consulta.
        db.query("SELECT COUNT(*) FROM expenses").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM cards").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM merchant_categories").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }

        // 3) Categorias iniciais semeadas (RF07).
        db.query("SELECT COUNT(*) FROM categories").use { c ->
            c.moveToFirst()
            assertEquals(9, c.getInt(0))
        }
    }
}

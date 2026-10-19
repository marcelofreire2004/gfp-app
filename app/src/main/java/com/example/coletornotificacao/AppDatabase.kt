package com.example.coletornotificacao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.coletornotificacao.expense.CardDao
import com.example.coletornotificacao.expense.CardEntity
import com.example.coletornotificacao.expense.CategoryDao
import com.example.coletornotificacao.expense.CategoryEntity
import com.example.coletornotificacao.expense.ExpenseDao
import com.example.coletornotificacao.expense.ExpenseEntity
import com.example.coletornotificacao.expense.MerchantCategoryDao
import com.example.coletornotificacao.expense.MerchantCategoryEntity

@Database(
    entities = [
        NotificationEntity::class,
        CardEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        MerchantCategoryEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun cardDao(): CardDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun merchantCategoryDao(): MerchantCategoryDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migração v1 -> v2: adiciona os campos de sincronização SEM apagar dados.
         *
         * - Adiciona colunas syncState, stableId, lastAttemptAt, attemptCount, lastError.
         * - Registros antigos são marcados como PENDING para serem sincronizados.
         * - stableId dos registros antigos recebe um valor determinístico e único
         *   derivado do id local ("legacy-<id>"), preservando estabilidade. SQLite
         *   não calcula SHA-256; o hash de conteúdo passa a valer para capturas novas.
         * - Cria índice único em stableId.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notifications ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE notifications ADD COLUMN lastAttemptAt INTEGER")
                db.execSQL("ALTER TABLE notifications ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notifications ADD COLUMN lastError TEXT")

                // Backfill de stableId para registros antigos (determinístico e único).
                db.execSQL("UPDATE notifications SET stableId = 'legacy-' || id WHERE stableId = ''")

                // Índice único exigido pela entidade v2.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_notifications_stableId ON notifications(stableId)"
                )
            }
        }

        /**
         * Migração v2 -> v3: adiciona o módulo de despesas SEM tocar na tabela
         * `notifications` (preserva todas as notificações já capturadas — RF, req. técnico).
         *
         * Cria as tabelas cards, categories, expenses e merchant_categories com
         * seus índices, e semeia as categorias iniciais (RF07). Operação puramente
         * aditiva: nenhuma coluna/linha existente é alterada ou removida.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // cards
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cards` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `institution` TEXT NOT NULL,
                        `nickname` TEXT,
                        `last4` TEXT,
                        `dueDay` INTEGER,
                        `closingDaysBeforeDue` INTEGER,
                        `matchKeyword` TEXT,
                        `active` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_last4` ON `cards` (`last4`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_institution` ON `cards` (`institution`)")

                // categories
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `name` TEXT NOT NULL,
                        `seeded` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

                // expenses
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `sourceStableId` TEXT NOT NULL,
                        `amountCents` INTEGER,
                        `merchant` TEXT,
                        `purchaseDate` INTEGER NOT NULL,
                        `institution` TEXT,
                        `cardId` INTEGER,
                        `cardLast4` TEXT,
                        `installmentNumber` INTEGER,
                        `installmentTotal` INTEGER,
                        `type` TEXT NOT NULL,
                        `category` TEXT,
                        `status` TEXT NOT NULL,
                        `autoRegistered` INTEGER NOT NULL DEFAULT 0,
                        `reviewReasons` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_sourceStableId` ON `expenses` (`sourceStableId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_status` ON `expenses` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_cardId` ON `expenses` (`cardId`)")

                // merchant_categories
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_categories` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `merchantKey` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_categories_merchantKey` ON `merchant_categories` (`merchantKey`)")

                // Seed das categorias iniciais (RF07). INSERT OR IGNORE respeita o índice único.
                for (nome in CategoryEntity.INICIAIS) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO `categories` (`name`, `seeded`) VALUES (?, 1)",
                        arrayOf<Any>(nome)
                    )
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notification_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(SeedCallback)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Semeia as categorias iniciais em instalações NOVAS (onCreate), onde as
         * migrações não rodam. Em bancos existentes, o seed vem da MIGRATION_2_3.
         */
        private val SeedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                for (nome in CategoryEntity.INICIAIS) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO `categories` (`name`, `seeded`) VALUES (?, 1)",
                        arrayOf<Any>(nome)
                    )
                }
            }
        }
    }
}

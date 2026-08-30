package dev.nichidori.saku.data


import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.nichidori.saku.data.dao.*
import dev.nichidori.saku.data.entity.*
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TrxEntity::class,
        BudgetEntity::class,
        BudgetTemplateEntity::class,
        CreditEntity::class,
        TrxTemplateEntity::class,
        MonthlyNetWorthEntity::class,
        InstallmentEntity::class,
    ],
    version = 13,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trxDao(): TrxDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetTemplateDao(): BudgetTemplateDao
    abstract fun creditDao(): CreditDao
    abstract fun trxTemplateDao(): TrxTemplateDao
    abstract fun monthlyNetWorthDao(): MonthlyNetWorthDao
    abstract fun installmentDao(): InstallmentDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE category ADD COLUMN icon TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        // Create budget_template
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budget_template` (
                `id` TEXT NOT NULL,
                `category_id` TEXT NOT NULL,
                `default_amount` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`category_id`) REFERENCES `category`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_template_category_id`
            ON `budget_template` (`category_id`)
            """.trimIndent()
        )

        // Create budget table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budget` (
                `id` TEXT NOT NULL,
                `template_id` TEXT NOT NULL,
                `category_id` TEXT NOT NULL,
                `month` INTEGER NOT NULL,
                `year` INTEGER NOT NULL,
                `base_amount` INTEGER NOT NULL,
                `spent_amount` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`category_id`) REFERENCES `category`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`template_id`) REFERENCES `budget_template`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_category_id_month_year`
            ON `budget` (`category_id`, `month`, `year`)
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_budget_template_id`
            ON `budget` (`template_id`)
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `monthly_account_balance` (
                `year` INTEGER NOT NULL,
                `month` INTEGER NOT NULL,
                `account_id` TEXT NOT NULL,
                `balance` INTEGER NOT NULL,
                PRIMARY KEY(`year`, `month`, `account_id`),
                FOREIGN KEY(`account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_monthly_account_balance_account_id`
            ON `monthly_account_balance` (`account_id`)
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            UPDATE `trx`
            SET `description` = `description` || ' ' || `note`
            WHERE `note` IS NOT NULL AND `note` != ''
            """.trimIndent()
        )
        connection.execSQL("ALTER TABLE `trx` DROP COLUMN `note`")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        // 1. Create credit table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `credit` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `limit` INTEGER NOT NULL DEFAULT 0,
                `current_amount` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // 2. Copy CREDIT rows from account → credit
        connection.execSQL(
            """
            INSERT INTO `credit` (`id`, `name`, `limit`, `current_amount`, `created_at`, `updated_at`)
            SELECT `id`, `name`, 0, 0, `created_at`, `updated_at`
            FROM `account` WHERE `type` = 'Credit'
            """.trimIndent()
        )

        // 3. Create monthly_credit_balance table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `monthly_credit_balance` (
                `year` INTEGER NOT NULL,
                `month` INTEGER NOT NULL,
                `credit_id` TEXT NOT NULL,
                `balance` INTEGER NOT NULL,
                PRIMARY KEY(`year`, `month`, `credit_id`),
                FOREIGN KEY(`credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_monthly_credit_balance_credit_id` ON `monthly_credit_balance` (`credit_id`)")

        // 4. Recreate trx with separate source/target columns + FKs CASCADE
        connection.execSQL("ALTER TABLE `trx` RENAME TO `trx_old`")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trx` (
                `id` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `category_id` TEXT,
                `source_account_id` TEXT,
                `source_credit_id` TEXT,
                `target_account_id` TEXT,
                `target_credit_id` TEXT,
                `transaction_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                `type` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`source_account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`source_credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`category_id`) REFERENCES `category`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `trx` (
                `id`, `description`, `amount`, `category_id`,
                `source_account_id`, `source_credit_id`,
                `target_account_id`, `target_credit_id`,
                `transaction_at`, `created_at`, `updated_at`, `type`
            )
            SELECT
                t.`id`, t.`description`, t.`amount`, t.`category_id`,
                CASE WHEN c.`id` IS NULL THEN t.`source_account_id` ELSE NULL END,
                c.`id`,
                CASE WHEN ct.`id` IS NULL THEN t.`target_account_id` ELSE NULL END,
                ct.`id`,
                t.`transaction_at`, t.`created_at`, t.`updated_at`, t.`type`
            FROM `trx_old` t
            LEFT JOIN `credit` c ON t.`source_account_id` = c.`id`
            LEFT JOIN `credit` ct ON t.`target_account_id` = ct.`id`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `trx_old`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_category_id` ON `trx` (`category_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_source_account_id` ON `trx` (`source_account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_source_credit_id` ON `trx` (`source_credit_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_target_account_id` ON `trx` (`target_account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_target_credit_id` ON `trx` (`target_credit_id`)")

        // 5. Move credit history from monthly_account_balance to monthly_credit_balance
        connection.execSQL(
            """
            INSERT INTO `monthly_credit_balance` (`year`, `month`, `credit_id`, `balance`)
            SELECT `year`, `month`, `account_id`, `balance`
            FROM `monthly_account_balance`
            WHERE `account_id` IN (SELECT `id` FROM `credit`)
            """.trimIndent()
        )

        // 6. Remove credit rows from monthly_account_balance
        connection.execSQL(
            """
            DELETE FROM `monthly_account_balance`
            WHERE `account_id` IN (SELECT `id` FROM `credit`)
            """.trimIndent()
        )

        // 6. Delete Credit rows from account
        connection.execSQL("DELETE FROM `account` WHERE `type` = 'Credit'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS monthly_account_balance")
        connection.execSQL("DROP TABLE IF EXISTS monthly_credit_balance")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trx_template` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `category_id` TEXT,
                `source_account_id` TEXT,
                `source_credit_id` TEXT,
                `target_account_id` TEXT,
                `target_credit_id` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`category_id`) REFERENCES `category`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`source_account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`source_credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_template_category_id` ON `trx_template` (`category_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_template_source_account_id` ON `trx_template` (`source_account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_template_source_credit_id` ON `trx_template` (`source_credit_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_template_target_account_id` ON `trx_template` (`target_account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_template_target_credit_id` ON `trx_template` (`target_credit_id`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `monthly_net_worth` (
                `year` INTEGER NOT NULL,
                `month` INTEGER NOT NULL,
                `net_worth` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`year`, `month`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        // 1. Create installment table
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `installment` (
                `id` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `category_id` TEXT NOT NULL,
                `credit_id` TEXT NOT NULL,
                `principal` INTEGER NOT NULL,
                `months` INTEGER NOT NULL,
                `monthly_rate` REAL NOT NULL,
                `total_amount` INTEGER NOT NULL,
                `monthly_payment` INTEGER NOT NULL,
                `last_payment` INTEGER NOT NULL,
                `start_at` INTEGER NOT NULL,
                `due_day` INTEGER NOT NULL,
                `next_index` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`category_id`) REFERENCES `category`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_category_id` ON `installment` (`category_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_credit_id` ON `installment` (`credit_id`)")

        // 2. Recreate trx with installment columns + FK
        connection.execSQL("ALTER TABLE `trx` RENAME TO `trx_old`")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `trx` (
                `id` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `category_id` TEXT,
                `source_account_id` TEXT,
                `source_credit_id` TEXT,
                `target_account_id` TEXT,
                `target_credit_id` TEXT,
                `transaction_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                `type` TEXT NOT NULL,
                `installment_id` TEXT,
                `installment_index` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`source_account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`source_credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_account_id`) REFERENCES `account`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`target_credit_id`) REFERENCES `credit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`category_id`) REFERENCES `category`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`installment_id`) REFERENCES `installment`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `trx` (
                `id`, `description`, `amount`, `category_id`,
                `source_account_id`, `source_credit_id`,
                `target_account_id`, `target_credit_id`,
                `transaction_at`, `created_at`, `updated_at`, `type`,
                `installment_id`, `installment_index`
            )
            SELECT
                `id`, `description`, `amount`, `category_id`,
                `source_account_id`, `source_credit_id`,
                `target_account_id`, `target_credit_id`,
                `transaction_at`, `created_at`, `updated_at`, `type`,
                NULL, NULL
            FROM `trx_old`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `trx_old`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_category_id` ON `trx` (`category_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_source_account_id` ON `trx` (`source_account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_source_credit_id` ON `trx` (`source_credit_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_target_account_id` ON `trx` (`target_account_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_target_credit_id` ON `trx` (`target_credit_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trx_installment_id` ON `trx` (`installment_id`)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        // Recreate account without the initial_amount column. Drop the parent
        // table then rename the new one into place so that trx/trx_template
        // foreign keys keep referencing `account` (renaming `account` would
        // rewrite those references to `account_old` and fail validation).
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `account_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `current_amount` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO `account_new` (`id`, `name`, `current_amount`, `type`, `created_at`, `updated_at`)
            SELECT `id`, `name`, `current_amount`, `type`, `created_at`, `updated_at`
            FROM `account`
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `account`")
        connection.execSQL("ALTER TABLE `account_new` RENAME TO `account`")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `account` ADD COLUMN `deleted_at` INTEGER")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `category` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_category_sort_order` ON `category` (`sort_order`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_category_parent_id_sort_order` ON `category` (`parent_id`, `sort_order`)")
        // Backfill deterministic 0..n-1 per partition by name ASC (preserve existing UI order)
        connection.execSQL(
            """
            UPDATE `category` SET `sort_order` = (
                SELECT COUNT(*) FROM `category` AS c2
                WHERE c2.`parent_id` IS `category`.`parent_id`
                AND (
                    c2.`name` < `category`.`name`
                    OR (c2.`name` = `category`.`name` AND c2.`id` < `category`.`id`)
                )
            )
            """.trimIndent()
        )
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13
        )
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

package com.example.coletornotificacao.expense

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Categoria de despesa (RF07). As categorias iniciais são semeadas na migração.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Categorias semeadas pelo sistema (não removíveis pela UI básica). */
    @ColumnInfo(name = "seeded", defaultValue = "0") val seeded: Boolean = false
) {
    companion object {
        /** Categorias iniciais exigidas pelo RF07. */
        val INICIAIS = listOf(
            "Supermercado",
            "Combustível",
            "Alimentação fora de casa",
            "Saúde",
            "Educação",
            "Transporte",
            "Compras pessoais",
            "Casa",
            "Outros"
        )
        const val FALLBACK = "Outros"
    }
}

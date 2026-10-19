package com.example.coletornotificacao.expense

import kotlinx.coroutines.flow.Flow

/** Acesso às categorias (RF07). As iniciais são semeadas na migração/onCreate. */
class CategoryRepository(private val categoryDao: CategoryDao) {

    val allCategories: Flow<List<CategoryEntity>> = categoryDao.observeAll()

    /** Cria uma categoria do usuário (não semeada). Ignora duplicata (índice único). */
    suspend fun criar(nome: String): Long =
        categoryDao.insert(CategoryEntity(name = nome.trim(), seeded = false))

    /** Garante que as categorias iniciais existam (idempotente). */
    suspend fun garantirIniciais() {
        if (categoryDao.count() == 0) {
            for (nome in CategoryEntity.INICIAIS) {
                categoryDao.insert(CategoryEntity(name = nome, seeded = true))
            }
        }
    }
}

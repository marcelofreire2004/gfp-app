package com.example.coletornotificacao.expense

import kotlinx.coroutines.flow.Flow

/** Acesso às categorias (RF07). As iniciais são semeadas na migração/onCreate. */
class CategoryRepository(private val categoryDao: CategoryDao) {

    val allCategories: Flow<List<CategoryEntity>> = categoryDao.observeAll()

    /** Garante que as categorias iniciais existam (idempotente). */
    suspend fun garantirIniciais() {
        if (categoryDao.count() == 0) {
            for (nome in CategoryEntity.INICIAIS) {
                categoryDao.insert(CategoryEntity(name = nome, seeded = true))
            }
        }
    }
}

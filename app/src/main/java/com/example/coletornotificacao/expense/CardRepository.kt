package com.example.coletornotificacao.expense

import kotlinx.coroutines.flow.Flow

/** CRUD mínimo de cartões (RF04). Não guarda dados sensíveis do cartão. */
class CardRepository(private val cardDao: CardDao) {

    val allCards: Flow<List<CardEntity>> = cardDao.observeAll()

    suspend fun criar(card: CardEntity): Long = cardDao.insert(card)

    suspend fun atualizar(card: CardEntity) = cardDao.update(card)

    suspend fun listarAtivos(): List<CardEntity> = cardDao.listActive()
}

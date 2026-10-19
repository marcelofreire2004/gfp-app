package com.example.coletornotificacao.expense

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards ORDER BY active DESC, name ASC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE active = 1")
    suspend fun listActive(): List<CardEntity>

    @Query("SELECT * FROM cards ORDER BY active DESC, name ASC")
    suspend fun observeAllOnce(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun findById(id: Long): CardEntity?
}

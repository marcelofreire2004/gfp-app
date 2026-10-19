package com.example.coletornotificacao

import androidx.room.TypeConverter

/** Conversores de tipos customizados para o Room. */
class Converters {

    @TypeConverter
    fun fromSyncState(state: SyncState): String = state.name

    @TypeConverter
    fun toSyncState(value: String): SyncState =
        runCatching { SyncState.valueOf(value) }.getOrDefault(SyncState.PENDING)
}

package com.meetingnotes.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(listSerializer, value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(listSerializer, value)
}

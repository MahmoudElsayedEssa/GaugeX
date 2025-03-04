package com.binissa.core.data.datasource.remote

class JsonSerializerImpl : JsonSerializer {
    override fun serialize(data: Any): String {
        return when (data) {
            is Map<*, *> -> mapToJson(data)
            is String -> "\"$data\""
            is Number, is Boolean -> "$data"
            else -> "{}"
        }
    }

    private fun mapToJson(map: Map<*, *>): String {
        val entries = map.entries.joinToString(",") { (key, value) ->
            "\"$key\":${valueToJson(value)}"
        }
        return "{$entries}"
    }

    private fun valueToJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"$value\""
            is Number, is Boolean -> "$value"
            is Map<*, *> -> mapToJson(value)
            is Collection<*> -> collectionToJson(value)
            else -> "\"$value\""
        }
    }

    private fun collectionToJson(collection: Collection<*>): String {
        val items = collection.joinToString(",") { valueToJson(it) }
        return "[$items]"
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(json: String, clazz: Class<T>): T {
        // Very basic implementation
        return when {
            clazz == String::class.java -> json as T
            json == "{}" -> Any() as T
            else -> Any() as T
        }
    }
}
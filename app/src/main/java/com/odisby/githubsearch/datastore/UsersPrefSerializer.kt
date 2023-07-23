package com.odisby.githubsearch.datastore

import androidx.datastore.core.Serializer
import com.odisby.githubsearch.domain.UsersPref
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object UsersPrefSerializer : Serializer<UsersPref> {
    override val defaultValue: UsersPref
        get() = UsersPref()

    override suspend fun readFrom(input: InputStream): UsersPref {
        return try{
            Json.decodeFromString(
                deserializer = UsersPref.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (e: SerializationException){
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: UsersPref, output: OutputStream) {
        output.write(
            Json.encodeToString(
                serializer = UsersPref.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }
}
package com.odisby.githubsearch.domain

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

@Serializable
data class UsersPref (
    val lastUser: String? = null,
    val historic: PersistentList<String> = persistentListOf()
)
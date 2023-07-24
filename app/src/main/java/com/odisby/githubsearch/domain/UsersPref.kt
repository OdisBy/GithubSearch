package com.odisby.githubsearch.domain

import kotlinx.serialization.Serializable

@Serializable
data class UsersPref (
    val lastUser: String? = null,
    val favorites: MutableSet<String> = mutableSetOf()
)
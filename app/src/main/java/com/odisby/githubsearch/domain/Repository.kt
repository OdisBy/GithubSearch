package com.odisby.githubsearch.domain

import com.google.gson.annotations.SerializedName

data class Repository(
    val name: String,
    @SerializedName("html_url")
    val htmlUrl: String,
    val owner: Owner
)

data class Owner(
    val login: String
)
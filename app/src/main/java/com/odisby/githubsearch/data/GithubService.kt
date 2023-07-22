package com.odisby.githubsearch.data

import com.odisby.githubsearch.domain.Repository
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubService {
    /**
     * Get repositories of user on Github
     * @param user username of user on Github
     * @return List of repositories found in user account
     */
    @GET("users/{user}/repos")
    fun getAllRepositoriesByUser(@Path("user") user: String): Call<List<Repository>>
}
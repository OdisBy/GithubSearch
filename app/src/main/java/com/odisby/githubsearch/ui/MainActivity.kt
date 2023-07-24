package com.odisby.githubsearch.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.odisby.githubsearch.R
import com.odisby.githubsearch.ui.adapter.RepositoryAdapter
import com.odisby.githubsearch.data.GithubService
import com.odisby.githubsearch.databinding.ActivityMainBinding
import com.odisby.githubsearch.datastore.UsersPrefSerializer
import com.odisby.githubsearch.domain.UsersPref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var api: GithubService
    private lateinit var adapter: RepositoryAdapter
    private var favoriteUsers = emptySet<String>()
    private var usernameOriginal: String? = null
    private var firstOpen = true

    private val Context.usersDataStore: DataStore<UsersPref> by dataStore(
        fileName = "users_prefs.pb",
        serializer = UsersPrefSerializer
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRetrofit()
        setupListeners()
        adapter = RepositoryAdapter()
        binding.rvListaRepositories.adapter = adapter
        favoriteUsersFlow

        var lastUserCache: String? = null

        lifecycleScope.launch {
            lastUserFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { lastUser ->
                    lastUser?.let {
                        if(firstOpen) {
                            binding.etNomeUsuario.setText(lastUser)
                            firstOpen = !firstOpen
                        }
                        if (lastUser != lastUserCache) {
                            lastUserCache = lastUser
                            getAllReposByUserName(lastUser)
                        }
                    }
                }
        }
        lifecycleScope.launch {
            favoriteUsersFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { fav ->
                    favoriteUsers = fav
                    setupAutoCompleteFavorites()
                    usernameOriginal?.let {
                        setupUsernameAndFavView()
                    }
                }
        }
    }

    private fun setupAutoCompleteFavorites() {
        val arrayAdapter = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, favoriteUsers.toList())
        binding.etNomeUsuario.apply {
            setAdapter(arrayAdapter)
            setOnFocusChangeListener { v, hasFocus ->
                if(hasFocus){
                    showDropDown()
                } else {
                    hideKeyboard()
                }
            }
            setOnItemClickListener { parent, view, position, id ->
                hideKeyboard()
            }
        }
    }

    private fun setupListeners() {
        binding.btnConfirmar.setOnClickListener {
            val username = binding.etNomeUsuario.text.toString()
            lifecycleScope.launch {
                saveLastUser(username)
            }
        }
    }

    private fun userIsFavorite(userText: String): Boolean {
        return favoriteUsers.contains(userText)
    }

    private val lastUserFlow: Flow<String?>
        get() = usersDataStore.data.map { it.lastUser }

    private val favoriteUsersFlow: Flow<MutableSet<String>>
        get() = usersDataStore.data.map { it.favorites }

    private suspend fun saveLastUser(username: String) {
        usersDataStore.updateData { currentPref ->
            currentPref.copy(lastUser = username)
        }
    }

    private fun addUserToFavorites(username: String) = lifecycleScope.launch {
        try{
            usersDataStore.updateData { currentPrefs ->
                currentPrefs.copy(favorites = currentPrefs.favorites.toMutableSet().apply { add(username.lowercase()) })
            }
        } catch (e: Exception) {
            e.stackTrace
            Log.e("MainActivity", "Erro ao salvar usuário nos favoritos")
        }

        favoriteUsersFlow
    }

    private fun removeUserFromFavorites(username: String) = lifecycleScope.launch {
        try{
            usersDataStore.updateData { currentPrefs ->
                currentPrefs.copy(favorites = currentPrefs.favorites.toMutableSet().apply { remove(username.lowercase()) })
            }
        } catch (e: Exception) {
            e.stackTrace
            Log.e("MainActivity", "Erro ao remover usuário dos favoritos")
        }
        favoriteUsersFlow
    }


    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(GithubService::class.java)
    }

    //Metodo responsavel por buscar todos os repositorios do usuario fornecido
    private suspend fun getAllReposByUserName(username: String) {
        try{
            val data = api.getAllRepositoriesByUser(username)
            usernameOriginal = data.first().owner.login
            setupUsernameAndFavView()
            adapter.updateList(data)
            noErrors()
        } catch (e: HttpException) {
            when(e.code()) {
                404 -> {
                    Log.e("MainActivity", "Usuário não encontrado: $username")
                    adapter.updateList(emptyList())
                    lastUserNotFound()
                }
                else -> {
                    Log.e("MainActivity", "Erro na comunicação com a API: ${e.message()}")
                    adapter.updateList(emptyList())
                    noConnectionError()
                }
            }
        }
        catch (e: Exception) {
            e.stackTrace
            Log.e("MainActivity", "Erro ao getAllReposByUserName: ${e.message.toString()}")
            adapter.updateList(emptyList())
            noConnectionError()

        }
    }

    private fun noErrors() {
        binding.rvListaRepositories.visibility = View.VISIBLE
        binding.tvNotFound.visibility = View.INVISIBLE
        binding.ivNotFound.visibility = View.INVISIBLE
    }

    private fun setupUsernameAndFavView() {
        binding.tvNameSearched.text = usernameOriginal
        binding.linearUserFav.visibility = View.VISIBLE
        binding.btnFavorite.apply {
            setOnClickListener {
                usernameOriginal?.let {
                    if (userIsFavorite(it)) {
                        removeUserFromFavorites(it)
                    } else {
                        addUserToFavorites(it)
                    }
                }
            }
            usernameOriginal?.let {
                it.lowercase().let {
                    if(userIsFavorite(it)){
                        setImageDrawable(
                            AppCompatResources.getDrawable(
                                applicationContext,
                                R.drawable.ic_star_yellow
                            )
                        )
                    } else {
                        setImageDrawable(
                            AppCompatResources.getDrawable(
                                applicationContext,
                                R.drawable.ic_star_border
                            )
                        )
                    }
                }
            }
        }
    }

    private fun disableUsernameAndFavView() {
        binding.tvNameSearched.text = null
        binding.linearUserFav.visibility = View.GONE
    }

    private fun noConnectionError() {
        setError(
            drawable = AppCompatResources.getDrawable(applicationContext, R.drawable.ic_no_internet)!!,
            errorText = getString(R.string.error_no_internet)
        )
    }

    private suspend fun lastUserNotFound() {
        usersDataStore.updateData { currentPref ->
            currentPref.copy(lastUser = null)
        }
        setError(
            drawable = AppCompatResources.getDrawable(applicationContext, R.drawable.ic_person_search)!!,
            errorText = getString(R.string.error_not_found)
        )
    }

    private fun setError(drawable: Drawable, errorText: String) {
        disableUsernameAndFavView()
        binding.rvListaRepositories.visibility = View.INVISIBLE


        binding.tvNotFound.apply {
            visibility = View.VISIBLE
            text = errorText
        }

        binding.ivNotFound.apply {
            visibility = View.VISIBLE
            setImageDrawable(drawable)
        }

        binding.tvNotFound.visibility = View.VISIBLE
        binding.ivNotFound.visibility = View.VISIBLE

    }


    // Metodo responsavel por compartilhar o link do repositorio selecionado
    // @Todo 11 - Colocar esse metodo no click do share item do adapter
    fun shareRepositoryLink(urlRepository: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, urlRepository)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    // Metodo responsavel por abrir o browser com o link informado do repositorio

    // @Todo 12 - Colocar esse metodo no click item do adapter
    fun openBrowser(urlRepository: String) {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(urlRepository)
            )
        )
    }

    fun View.hideKeyboard() {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    private companion object{
        const val USERS_HIST = "users_historic"
    }
}
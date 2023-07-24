package com.odisby.githubsearch.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
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
                        Log.d("MainActivity", "Novo usuário: $lastUser capturado no flow")
                        if (lastUser != lastUserCache) {
                            lastUserCache = lastUser
                            binding.etNomeUsuario.setText(lastUser)
                            getAllReposByUserName(lastUser)
                        }
                    }
                }
        }
        lifecycleScope.launch {
            favoriteUsersFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { fav ->
                    Log.d("MainActivity", "Favorite users update flow fav: $fav")
                    favoriteUsers = fav
                    usernameOriginal?.let {
                        setupUsernameAndFavView()
                    }
                }
        }
    }
    private fun setupListeners() {
        binding.btnConfirmar.setOnClickListener {
            val username = binding.etNomeUsuario.text.toString()
            lifecycleScope.launch {
                hideKeyboard(this@MainActivity)
                saveLastUser(username)
            }
        }
    }

    private fun userIsFavorite(userText: String): Boolean {
        Log.d("MainActivity", "userIsFavorite: favoriteUsers: $favoriteUsers --- UserText: $userText")
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
        Log.d("MainActivity", "Adicionando ${username.lowercase()} para favoritos")
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
            Log.d("MainActivity", "Chamando get all repositories by username")
            val data = api.getAllRepositoriesByUser(username)
            Log.d("MainActivity", "Get all data and set usernameoriginal")
            usernameOriginal = data.first().owner.login
            setupUsernameAndFavView()
            adapter.updateList(data)
        } catch (e: HttpException) {
            when(e.code()) {
                404 -> {
                    Log.e("MainActivity", "Usuário não encontrado: $username")
                    lastUserNotFound()
                }
                else -> {
                    Log.e("MainActivity", "Erro na comunicação com a API: ${e.message()}")
                }
            }
            disableUsernameAndFavView()
        }
        catch (e: Exception) {
            e.stackTrace
            Log.e("MainActivity", "Erro ao getAllReposByUserName: ${e.message.toString()}")
            disableUsernameAndFavView()
        }
    }

    private fun setupUsernameAndFavView() {
        Log.d("MainActivity", "Setup username and favorite view")
        Log.d("MainActivity", "usernameOriginal: $usernameOriginal")
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

    private suspend fun lastUserNotFound() {
        usersDataStore.updateData { currentPref ->
            currentPref.copy(lastUser = null)
        }
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

    fun hideKeyboard(activity: Activity) {
        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = activity.currentFocus
        if (currentFocusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)
        }
    }

    private companion object{
        const val USERS_HIST = "users_historic"
    }
}
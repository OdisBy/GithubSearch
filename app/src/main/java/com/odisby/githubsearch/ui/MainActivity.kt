package com.odisby.githubsearch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.odisby.githubsearch.ui.adapter.RepositoryAdapter
import com.odisby.githubsearch.data.GithubService
import com.odisby.githubsearch.databinding.ActivityMainBinding
import com.odisby.githubsearch.datastore.UsersPrefSerializer
import com.odisby.githubsearch.domain.Repository
import com.odisby.githubsearch.domain.UsersPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.await
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var api: GithubService
    private lateinit var adapter: RepositoryAdapter

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

        lifecycleScope.launch {
            lastUserFlow.collect { lastUser ->
                if(lastUser != null){
                    binding.etNomeUsuario.setText(lastUser)
                    getAllReposByUserName(lastUser)
                }
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

    private val lastUserFlow: Flow<String?>
        get() = usersDataStore.data.map { it.lastUser }

    private suspend fun saveLastUser(username: String) {
        usersDataStore.updateData { currentPref ->
            currentPref.copy(lastUser = username)
        }
    }

    private suspend fun addUserToFavorites(username: String) {
        usersDataStore.updateData { currentPrefs ->
            currentPrefs.copy(historic = currentPrefs.historic.add(username))
        }
    }

    private suspend fun removeUserFromFavorites(username: String) {
        usersDataStore.updateData { currentPrefs ->
            currentPrefs.copy(historic = currentPrefs.historic.remove(username))
        }
    }
    val favoriteUsersFlow: Flow<List<String>>
        get() = usersDataStore.data.map { it.historic }


    private fun setupRetrofit() {

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(GithubService::class.java)
    }

    //Metodo responsavel por buscar todos os repositorios do usuario fornecido
    private fun getAllReposByUserName(username: String) {
        var data: List<Repository>
        CoroutineScope(Dispatchers.Main).launch {
            data = api.getAllRepositoriesByUser(username)
            // TODO rodando no dispatchers main plmds arruma
            adapter.updateList(data)
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

    private companion object{
        const val USERS_HIST = "users_historic"
    }
}
package com.example.coletornotificacao.expense

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.AppDatabase
import com.example.coletornotificacao.R
import kotlinx.coroutines.launch

/** Tela de categorias (RF07): lista as categorias e permite adicionar novas. */
class CategoriesActivity : AppCompatActivity() {

    private val repo by lazy { CategoryRepository(AppDatabase.getDatabase(this).categoryDao()) }
    private lateinit var adapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)
        title = "Categorias"

        adapter = CategoryAdapter()
        findViewById<RecyclerView>(R.id.recyclerCategories).apply {
            layoutManager = LinearLayoutManager(this@CategoriesActivity)
            adapter = this@CategoriesActivity.adapter
        }

        findViewById<Button>(R.id.btnAddCategory).setOnClickListener { adicionar() }

        // Garante as categorias iniciais e observa a lista.
        lifecycleScope.launch { repo.garantirIniciais() }
        lifecycleScope.launch {
            repo.allCategories.collect { adapter.submitList(it) }
        }
    }

    private fun adicionar() {
        val campo = findViewById<EditText>(R.id.editCategoryName)
        val nome = campo.text.toString().trim()
        if (nome.isBlank()) {
            Toast.makeText(this, "Informe o nome da categoria", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val id = repo.criar(nome)
            if (id == -1L) {
                Toast.makeText(this@CategoriesActivity, "Categoria já existe", Toast.LENGTH_SHORT).show()
            } else {
                campo.text.clear()
                Toast.makeText(this@CategoriesActivity, "Categoria adicionada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

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

/** Cadastro básico de cartões (RF04). Não coleta dados sensíveis. */
class CardsActivity : AppCompatActivity() {

    private val repo by lazy { CardRepository(AppDatabase.getDatabase(this).cardDao()) }
    private lateinit var adapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cards)
        title = "Cartões"

        adapter = CardAdapter()
        val rv = findViewById<RecyclerView>(R.id.recyclerCards)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<Button>(R.id.btnAddCard).setOnClickListener { adicionar() }

        lifecycleScope.launch {
            repo.allCards.collect { adapter.submitList(it) }
        }
    }

    private fun adicionar() {
        val nome = findViewById<EditText>(R.id.editCardName).text.toString().trim()
        val inst = findViewById<EditText>(R.id.editCardInstitution).text.toString().trim()
        if (nome.isBlank() || inst.isBlank()) {
            Toast.makeText(this, "Nome e instituição são obrigatórios", Toast.LENGTH_SHORT).show()
            return
        }
        val nickname = findViewById<EditText>(R.id.editCardNickname).text.toString().trim().ifBlank { null }
        val last4 = findViewById<EditText>(R.id.editCardLast4).text.toString().trim()
            .takeIf { it.length == 4 && it.all(Char::isDigit) }
        val dueDay = findViewById<EditText>(R.id.editCardDueDay).text.toString().trim().toIntOrNull()
        val closing = findViewById<EditText>(R.id.editCardClosingDays).text.toString().trim().toIntOrNull()

        lifecycleScope.launch {
            repo.criar(
                CardEntity(
                    name = nome,
                    institution = inst,
                    nickname = nickname,
                    last4 = last4,
                    dueDay = dueDay?.takeIf { it in 1..31 },
                    closingDaysBeforeDue = closing?.takeIf { it in 0..28 },
                    // Palavra-chave de origem: por padrão, a instituição em minúsculas
                    // ajuda a casar notificações cujo pacote contenha o nome.
                    matchKeyword = inst.lowercase()
                )
            )
            Toast.makeText(this@CardsActivity, "Cartão adicionado", Toast.LENGTH_SHORT).show()
            // Limpa campos principais.
            findViewById<EditText>(R.id.editCardName).text.clear()
            findViewById<EditText>(R.id.editCardLast4).text.clear()
        }
    }
}

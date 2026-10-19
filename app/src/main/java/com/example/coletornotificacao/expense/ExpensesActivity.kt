package com.example.coletornotificacao.expense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.AppDatabase
import com.example.coletornotificacao.R
import kotlinx.coroutines.launch

/**
 * Tela de despesas (RF08): lista + resumo. O resumo considera SOMENTE
 * lançamentos confirmados; itens em revisão são contados à parte, nunca
 * somados silenciosamente às despesas confirmadas.
 */
class ExpensesActivity : AppCompatActivity() {

    private lateinit var adapter: ExpenseAdapter
    private lateinit var txtConfirmed: TextView
    private lateinit var txtReview: TextView
    private lateinit var txtByCard: TextView
    private lateinit var txtByCategory: TextView

    private val repo by lazy {
        val db = AppDatabase.getDatabase(this)
        ExpenseRepository(db.expenseDao(), db.cardDao(), db.merchantCategoryDao())
    }
    private val cardDao by lazy { AppDatabase.getDatabase(this).cardDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenses)
        title = "Despesas"

        txtConfirmed = findViewById(R.id.txtSummaryConfirmed)
        txtReview = findViewById(R.id.txtSummaryReview)
        txtByCard = findViewById(R.id.txtSummaryByCard)
        txtByCategory = findViewById(R.id.txtSummaryByCategory)

        adapter = ExpenseAdapter { expense ->
            startActivity(
                Intent(this, ExpenseDetailActivity::class.java)
                    .putExtra(ExpenseDetailActivity.EXTRA_ID, expense.id)
            )
        }
        val rv = findViewById<RecyclerView>(R.id.recyclerExpenses)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<Button>(R.id.btnManageCards).setOnClickListener {
            startActivity(Intent(this, CardsActivity::class.java))
        }

        observar()
    }

    private fun observar() {
        lifecycleScope.launch {
            repo.allExpenses.collect { adapter.submitList(it) }
        }
        lifecycleScope.launch {
            repo.confirmedTotalCents.collect { txtConfirmed.text = "Confirmadas: ${Money.format(it)}" }
        }
        lifecycleScope.launch {
            repo.reviewCount.collect { txtReview.text = "Em revisão: $it" }
        }
        // Gastos por cartão (resolve nome do cartão).
        lifecycleScope.launch {
            repo.totalsByCard.collect { totais ->
                val cartoes = cardDao.listActive().associateBy { it.id }
                val txt = if (totais.isEmpty()) "Sem gastos confirmados por cartão"
                else totais.joinToString("\n") { t ->
                    val nome = t.cardId?.let { cartoes[it]?.name } ?: "Sem cartão"
                    "• $nome: ${Money.format(t.totalCents)}"
                }
                txtByCard.text = "Por cartão:\n$txt"
            }
        }
        lifecycleScope.launch {
            repo.totalsByCategory.collect { totais ->
                val txt = if (totais.isEmpty()) "Sem gastos confirmados por categoria"
                else totais.joinToString("\n") { "• ${it.category}: ${Money.format(it.totalCents)}" }
                txtByCategory.text = "Por categoria:\n$txt"
            }
        }
    }
}

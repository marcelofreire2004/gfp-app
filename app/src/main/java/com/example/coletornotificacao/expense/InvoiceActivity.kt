package com.example.coletornotificacao.expense

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.AppDatabase
import com.example.coletornotificacao.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fatura de um cartão: mostra o total da fatura atual (ciclo de fechamento) e
 * lista os lançamentos do período. Quando o cartão não tem ciclo definido
 * (sem dueDay/closingDaysBeforeDue), cai no total/lista geral do cartão.
 */
class InvoiceActivity : AppCompatActivity() {

    private lateinit var adapter: ExpenseAdapter
    private lateinit var txtCard: TextView
    private lateinit var txtTotal: TextView
    private lateinit var txtDates: TextView
    private lateinit var txtEmpty: TextView

    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invoice)
        title = "Fatura"

        txtCard = findViewById(R.id.txtInvoiceCard)
        txtTotal = findViewById(R.id.txtInvoiceTotal)
        txtDates = findViewById(R.id.txtInvoiceDates)
        txtEmpty = findViewById(R.id.txtInvoiceEmpty)

        adapter = ExpenseAdapter { e ->
            startActivity(
                Intent(this, ExpenseDetailActivity::class.java)
                    .putExtra(ExpenseDetailActivity.EXTRA_ID, e.id)
            )
        }
        findViewById<RecyclerView>(R.id.recyclerInvoice).apply {
            layoutManager = LinearLayoutManager(this@InvoiceActivity)
            adapter = this@InvoiceActivity.adapter
        }

        val cardId = intent.getLongExtra(EXTRA_CARD_ID, -1L)
        if (cardId <= 0) { finish(); return }
        carregar(cardId)
    }

    private fun carregar(cardId: Long) {
        lifecycleScope.launch {
            val card = db.cardDao().findById(cardId)
            if (card == null) { finish(); return@launch }
            txtCard.text = "${card.name} · ${card.institution}"

            val cycle = InvoiceCycle.compute(card.dueDay, card.closingDaysBeforeDue)
            val expenseDao = db.expenseDao()

            val totalFlow: Flow<Long>
            val listFlow: Flow<List<ExpenseEntity>>
            if (cycle != null) {
                txtDates.text = "Fecha ${fmt(cycle.closingDate)} · Vence ${fmt(cycle.dueDate)}"
                totalFlow = expenseDao.observeInvoiceTotal(cardId, cycle.periodStart, cycle.periodEnd)
                listFlow = expenseDao.observeByCardAndPeriod(cardId, cycle.periodStart, cycle.periodEnd)
            } else {
                txtDates.text = "Ciclo não definido · exibindo todos os lançamentos"
                totalFlow = expenseDao.observeCardTotal(cardId)
                listFlow = expenseDao.observeByCard(cardId)
            }

            lifecycleScope.launch {
                totalFlow.collect { txtTotal.text = Money.format(it) }
            }
            lifecycleScope.launch {
                listFlow.collect { lista ->
                    adapter.submitList(lista)
                    txtEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun fmt(ts: Long): String = DATE_FMT.format(Date(ts))

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    }
}

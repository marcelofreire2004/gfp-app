package com.example.coletornotificacao.expense

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.coletornotificacao.AppDatabase
import com.example.coletornotificacao.R
import kotlinx.coroutines.launch

/**
 * Detalhe/edição de uma despesa (RF09). Permite corrigir valor, estabelecimento,
 * categoria e cartão; confirmar ou rejeitar um item em revisão. A confirmação
 * ATUALIZA o registro existente (nunca cria outro) e não apaga a notificação.
 */
class ExpenseDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "expense_id" }

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val repo by lazy { ExpenseRepository(db.expenseDao(), db.cardDao(), db.merchantCategoryDao()) }

    private var expenseId: Long = -1L
    private var cards: List<CardEntity> = emptyList()
    private var categories: List<String> = emptyList()

    private lateinit var editAmount: EditText
    private lateinit var editMerchant: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerCard: Spinner
    private lateinit var txtTypeStatus: TextView
    private lateinit var txtReasons: TextView
    private lateinit var txtSource: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_detail)
        title = "Detalhe da despesa"
        expenseId = intent.getLongExtra(EXTRA_ID, -1L)

        editAmount = findViewById(R.id.editAmount)
        editMerchant = findViewById(R.id.editMerchant)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerCard = findViewById(R.id.spinnerCard)
        txtTypeStatus = findViewById(R.id.txtTypeStatus)
        txtReasons = findViewById(R.id.txtReasons)
        txtSource = findViewById(R.id.txtSourceNotification)

        findViewById<Button>(R.id.btnSaveCorrection).setOnClickListener { salvarCorrecao() }
        findViewById<Button>(R.id.btnConfirm).setOnClickListener { confirmar() }
        findViewById<Button>(R.id.btnReject).setOnClickListener { rejeitar() }

        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            val e = db.expenseDao().findById(expenseId) ?: run {
                Toast.makeText(this@ExpenseDetailActivity, "Despesa não encontrada", Toast.LENGTH_SHORT).show()
                finish(); return@launch
            }
            cards = db.cardDao().observeAllOnce()
            categories = db.categoryDao().list().map { it.name }

            // Spinners
            val catAdapter = ArrayAdapter(this@ExpenseDetailActivity, android.R.layout.simple_spinner_item,
                listOf("(sem categoria)") + categories)
            catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = catAdapter
            e.category?.let { c -> categories.indexOf(c).takeIf { it >= 0 }?.let { spinnerCategory.setSelection(it + 1) } }

            val cardLabels = listOf("(sem cartão)") + cards.map { it.name }
            val cardAdapter = ArrayAdapter(this@ExpenseDetailActivity, android.R.layout.simple_spinner_item, cardLabels)
            cardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCard.adapter = cardAdapter
            e.cardId?.let { id -> cards.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { spinnerCard.setSelection(it + 1) } }

            // Campos
            editAmount.setText(e.amountCents?.let { String.format("%.2f", it / 100.0) } ?: "")
            editMerchant.setText(e.merchant ?: "")
            txtTypeStatus.text = "Tipo: ${e.type.name} · Estado: ${e.status.name}" +
                if (e.autoRegistered) " (automático)" else ""
            txtReasons.text = e.reviewReasons?.let { "Motivos de revisão: $it" } ?: ""
            txtSource.text = "Notificação de origem: ${e.sourceStableId}"
        }
    }

    private fun categoriaSelecionada(): String? {
        val pos = spinnerCategory.selectedItemPosition
        return if (pos <= 0) null else categories[pos - 1]
    }

    private fun cartaoSelecionado(): Long? {
        val pos = spinnerCard.selectedItemPosition
        return if (pos <= 0) null else cards[pos - 1].id
    }

    private fun salvarCorrecao() {
        val cents = Money.parseToCents(editAmount.text.toString())
        lifecycleScope.launch {
            repo.corrigir(
                id = expenseId,
                merchant = editMerchant.text.toString().ifBlank { null },
                amountCents = cents,
                cardId = cartaoSelecionado(),
                category = categoriaSelecionada()
            )
            Toast.makeText(this@ExpenseDetailActivity, "Correção salva", Toast.LENGTH_SHORT).show()
            carregar()
        }
    }

    private fun confirmar() {
        val cents = Money.parseToCents(editAmount.text.toString())
        lifecycleScope.launch {
            repo.confirmar(
                id = expenseId,
                merchant = editMerchant.text.toString().ifBlank { null },
                amountCents = cents,
                cardId = cartaoSelecionado(),
                category = categoriaSelecionada()
            )
            Toast.makeText(this@ExpenseDetailActivity, "Lançamento confirmado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun rejeitar() {
        lifecycleScope.launch {
            repo.rejeitar(expenseId)
            Toast.makeText(this@ExpenseDetailActivity, "Item rejeitado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

package com.example.coletornotificacao.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R
import com.example.coletornotificacao.expense.CardEntity
import com.example.coletornotificacao.expense.InvoiceCycle
import com.example.coletornotificacao.expense.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Carrossel de cartões (ViewPager2). Mostra os cartões cadastrados com valor da
 * fatura atual, data de fechamento e de vencimento. Tocar em um cartão abre a
 * fatura ([onOpenInvoice]). Quando a lista está VAZIA, exibe um único item
 * "Adicionar cartão", que leva ao cadastro ([onAddOrManage]).
 */
class CardCarouselAdapter(
    private val onAddOrManage: () -> Unit,
    private val onOpenInvoice: (CardEntity) -> Unit
) : RecyclerView.Adapter<CardCarouselAdapter.VH>() {

    private var cards: List<CardEntity> = emptyList()

    /** Total da fatura atual por cardId (centavos). Atualizado de forma assíncrona. */
    private val invoiceTotals = mutableMapOf<Long, Long>()

    fun submit(list: List<CardEntity>) {
        cards = list
        notifyDataSetChanged()
    }

    /** Atualiza o total da fatura de um cartão e redesenha só aquele item. */
    fun setInvoiceTotal(cardId: Long, cents: Long) {
        invoiceTotals[cardId] = cents
        val idx = cards.indexOfFirst { it.id == cardId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    override fun getItemCount() = if (cards.isEmpty()) 1 else cards.size

    private fun isAddItem() = cards.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card_carousel, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (isAddItem()) {
            holder.bindAdd(onAddOrManage)
        } else {
            val card = cards[position]
            holder.bindCard(card, invoiceTotals[card.id], onOpenInvoice)
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val cardContent: LinearLayout = view.findViewById(R.id.cardContent)
        private val addContent: LinearLayout = view.findViewById(R.id.addContent)
        private val institution: TextView = view.findViewById(R.id.txtCarouselInstitution)
        private val name: TextView = view.findViewById(R.id.txtCarouselName)
        private val last4: TextView = view.findViewById(R.id.txtCarouselLast4)
        private val due: TextView = view.findViewById(R.id.txtCarouselDue)
        private val closing: TextView = view.findViewById(R.id.txtCarouselClosing)
        private val invoice: TextView = view.findViewById(R.id.txtCarouselInvoice)

        fun bindAdd(onAddOrManage: () -> Unit) {
            cardContent.visibility = View.GONE
            addContent.visibility = View.VISIBLE
            itemView.setOnClickListener { onAddOrManage() }
        }

        fun bindCard(c: CardEntity, invoiceCents: Long?, onOpenInvoice: (CardEntity) -> Unit) {
            addContent.visibility = View.GONE
            cardContent.visibility = View.VISIBLE
            institution.text = c.institution
            name.text = c.name + if (!c.active) " (inativo)" else ""
            last4.text = c.last4?.let { "•••• $it" } ?: "••••"
            invoice.text = Money.format(invoiceCents ?: 0L)

            val cycle = InvoiceCycle.compute(c.dueDay, c.closingDaysBeforeDue)
            if (cycle != null) {
                closing.text = "Fecha ${fmt(cycle.closingDate)}"
                due.text = "Vence ${fmt(cycle.dueDate)}"
            } else {
                closing.text = c.closingDaysBeforeDue?.let { "Fecha —" } ?: "Fecha —"
                due.text = c.dueDay?.let { "Vence dia $it" } ?: "Vence —"
            }

            itemView.setOnClickListener { onOpenInvoice(c) }
        }

        private fun fmt(ts: Long): String = DATE_FMT.format(Date(ts))

        companion object {
            private val DATE_FMT = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
        }
    }
}

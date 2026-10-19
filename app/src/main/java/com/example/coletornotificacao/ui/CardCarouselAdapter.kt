package com.example.coletornotificacao.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R
import com.example.coletornotificacao.expense.CardEntity

/**
 * Carrossel de cartões (ViewPager2). Mostra os cartões cadastrados; quando a
 * lista está VAZIA, exibe um único item "Adicionar cartão". Em ambos os casos,
 * tocar leva ao cadastro de cartões (RF04 / pedido de UX).
 */
class CardCarouselAdapter(
    private val onAddOrManage: () -> Unit
) : RecyclerView.Adapter<CardCarouselAdapter.VH>() {

    private var cards: List<CardEntity> = emptyList()

    fun submit(list: List<CardEntity>) {
        cards = list
        notifyDataSetChanged()
    }

    // Quando não há cartões, há 1 item (o "adicionar").
    override fun getItemCount() = if (cards.isEmpty()) 1 else cards.size

    private fun isAddItem(position: Int) = cards.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card_carousel, parent, false)
        return VH(v, onAddOrManage)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (isAddItem(position)) holder.bindAdd()
        else holder.bindCard(cards[position])
    }

    class VH(view: View, val onAddOrManage: () -> Unit) : RecyclerView.ViewHolder(view) {
        private val cardContent: LinearLayout = view.findViewById(R.id.cardContent)
        private val addContent: LinearLayout = view.findViewById(R.id.addContent)
        private val institution: TextView = view.findViewById(R.id.txtCarouselInstitution)
        private val name: TextView = view.findViewById(R.id.txtCarouselName)
        private val last4: TextView = view.findViewById(R.id.txtCarouselLast4)
        private val due: TextView = view.findViewById(R.id.txtCarouselDue)

        fun bindAdd() {
            cardContent.visibility = View.GONE
            addContent.visibility = View.VISIBLE
            itemView.setOnClickListener { onAddOrManage() }
        }

        fun bindCard(c: CardEntity) {
            addContent.visibility = View.GONE
            cardContent.visibility = View.VISIBLE
            institution.text = c.institution
            name.text = c.name + if (!c.active) " (inativo)" else ""
            last4.text = c.last4?.let { "•••• $it" } ?: "••••"
            due.text = c.dueDay?.let { "Vencimento dia $it" } ?: "Vencimento não informado"
            itemView.setOnClickListener { onAddOrManage() }
        }
    }
}

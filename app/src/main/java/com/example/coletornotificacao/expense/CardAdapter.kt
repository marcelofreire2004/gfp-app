package com.example.coletornotificacao.expense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R

/** Lista simples de cartões cadastrados (RF04). */
class CardAdapter : ListAdapter<CardEntity, CardAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.txtCardName)
        private val meta: TextView = view.findViewById(R.id.txtCardMeta)

        fun bind(c: CardEntity) {
            name.text = c.name + if (!c.active) " (inativo)" else ""
            val last4 = c.last4?.let { "•••• $it" } ?: "sem dígitos"
            val venc = c.dueDay?.let { "venc. dia $it" } ?: "venc. —"
            meta.text = "${c.institution} · $last4 · $venc"
        }
    }

    class Diff : DiffUtil.ItemCallback<CardEntity>() {
        override fun areItemsTheSame(o: CardEntity, n: CardEntity) = o.id == n.id
        override fun areContentsTheSame(o: CardEntity, n: CardEntity) = o == n
    }
}

package com.example.coletornotificacao.expense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista de despesas (RF08). Clique abre o detalhe/edição. */
class ExpenseAdapter(
    private val onClick: (ExpenseEntity) -> Unit
) : ListAdapter<ExpenseEntity, ExpenseAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View, val onClick: (ExpenseEntity) -> Unit) : RecyclerView.ViewHolder(view) {
        private val amount: TextView = view.findViewById(R.id.txtExpAmount)
        private val merchant: TextView = view.findViewById(R.id.txtExpMerchant)
        private val meta: TextView = view.findViewById(R.id.txtExpMeta)
        private val status: TextView = view.findViewById(R.id.txtExpStatus)
        private val reasons: TextView = view.findViewById(R.id.txtExpReasons)
        private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun bind(e: ExpenseEntity) {
            amount.text = Money.format(e.amountCents)
            merchant.text = e.merchant ?: "(estabelecimento não identificado)"
            val cat = e.category ?: "sem categoria"
            meta.text = "${sdf.format(Date(e.purchaseDate))} · $cat · ${tipoLabel(e.type)}"
            status.text = when (e.status) {
                ExpenseStatus.CONFIRMED ->
                    if (e.autoRegistered) "✓ Confirmada (automática)" else "✓ Confirmada (manual)"
                ExpenseStatus.NEEDS_REVIEW -> "⏳ Em revisão"
                ExpenseStatus.REJECTED -> "✗ Rejeitada"
            }
            if (e.status == ExpenseStatus.NEEDS_REVIEW && !e.reviewReasons.isNullOrBlank()) {
                reasons.visibility = View.VISIBLE
                reasons.text = "Motivos: ${e.reviewReasons}"
            } else {
                reasons.visibility = View.GONE
            }
            itemView.setOnClickListener { onClick(e) }
        }

        private fun tipoLabel(t: ExpenseType): String = when (t) {
            ExpenseType.PURCHASE_APPROVED -> "Compra"
            ExpenseType.PURCHASE_DECLINED -> "Recusada"
            ExpenseType.REFUND -> "Estorno"
            ExpenseType.CANCELLATION -> "Cancelamento"
            ExpenseType.INVOICE_PAYMENT -> "Pgto fatura"
            ExpenseType.INVOICE_NOTICE -> "Aviso fatura"
            ExpenseType.UNRELATED -> "Outro"
            ExpenseType.UNKNOWN -> "Indefinido"
        }
    }

    class Diff : DiffUtil.ItemCallback<ExpenseEntity>() {
        override fun areItemsTheSame(o: ExpenseEntity, n: ExpenseEntity) = o.id == n.id
        override fun areContentsTheSame(o: ExpenseEntity, n: ExpenseEntity) = o == n
    }
}

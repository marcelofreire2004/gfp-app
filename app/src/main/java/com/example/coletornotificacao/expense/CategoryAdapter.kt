package com.example.coletornotificacao.expense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R

/** Lista simples de categorias (RF07). */
class CategoryAdapter : ListAdapter<CategoryEntity, CategoryAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.txtCategoryName)
        private val tag: TextView = view.findViewById(R.id.txtCategoryTag)

        fun bind(c: CategoryEntity) {
            name.text = c.name
            tag.text = if (c.seeded) "padrão" else "minha"
        }
    }

    class Diff : DiffUtil.ItemCallback<CategoryEntity>() {
        override fun areItemsTheSame(o: CategoryEntity, n: CategoryEntity) = o.id == n.id
        override fun areContentsTheSame(o: CategoryEntity, n: CategoryEntity) = o == n
    }
}

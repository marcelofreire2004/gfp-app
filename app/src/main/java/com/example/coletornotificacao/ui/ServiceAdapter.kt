package com.example.coletornotificacao.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R

/** Item da grade de serviços (ícone como emoji + rótulo + ação). */
data class Service(val icon: String, val label: String, val onClick: () -> Unit)

/** Adapter da grade de serviços (RecyclerView + GridLayoutManager). */
class ServiceAdapter(private val services: List<Service>) :
    RecyclerView.Adapter<ServiceAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_service, parent, false)
        return VH(v)
    }

    override fun getItemCount() = services.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(services[position])

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.txtServiceIcon)
        private val label: TextView = view.findViewById(R.id.txtServiceLabel)

        fun bind(s: Service) {
            icon.text = s.icon
            label.text = s.label
            itemView.setOnClickListener { s.onClick() }
        }
    }
}

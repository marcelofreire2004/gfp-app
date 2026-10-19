package com.example.coletornotificacao
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
class PackageAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<PackageCount,
PackageAdapter.ViewHolder>(
DiffCallback()
) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view =
        LayoutInflater.from(parent.context)
            .inflate(
                        R.layout.item_package,
                        parent,
                false
            )
        return ViewHolder(view)
    }
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        holder.bind(
                    getItem(position),
                    onClick
        )
    }
    class ViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {
        private val txtPackage =
        view.findViewById<TextView>(

                    R.id.txtPackage
        )
        private val txtCount =
        view.findViewById<TextView>(

                    R.id.txtCount
        )
        fun bind(
            item: PackageCount,
            onClick: (String) -> Unit
        ) {
        txtPackage.text =
        item.packageName
        txtCount.text =
        "${item.totalMessages} notificações"
        itemView.setOnClickListener {
            onClick(item.packageName)
        }
    }
    }
    class DiffCallback :
    DiffUtil.ItemCallback<PackageCount>() {
        override fun areItemsTheSame(
            oldItem: PackageCount,
            newItem: PackageCount
        ) = oldItem.packageName ==
        newItem.packageName
        override fun areContentsTheSame(
            oldItem: PackageCount,
            newItem: PackageCount
        ) = oldItem == newItem
    }
}
package com.example.coletornotificacao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter :
    ListAdapter<NotificationEntity, NotificationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val pkg: TextView = view.findViewById(R.id.txtPackage)
        private val title: TextView = view.findViewById(R.id.txtTitle)
        private val msg: TextView = view.findViewById(R.id.txtMessage)
        private val date: TextView = view.findViewById(R.id.txtDate)
        private val syncState: TextView = view.findViewById(R.id.txtSyncState)
        private val syncError: TextView = view.findViewById(R.id.txtSyncError)
        private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        fun bind(item: NotificationEntity) {
            pkg.text = item.packageName
            title.text = item.title
            msg.text = item.message
            date.text = sdf.format(Date(item.createdAt))

            syncState.text = when (item.syncState) {
                SyncState.SYNCED -> "✓ Sincronizado"
                SyncState.PENDING -> "⏳ Pendente"
                SyncState.FAILED -> "⚠ Falha (${item.attemptCount} tentativa(s))"
            }

            if (item.syncState == SyncState.FAILED && !item.lastError.isNullOrBlank()) {
                syncError.visibility = View.VISIBLE
                syncError.text = "Último erro: ${item.lastError}"
            } else {
                syncError.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NotificationEntity>() {
        override fun areItemsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity) =
            oldItem == newItem
    }
}

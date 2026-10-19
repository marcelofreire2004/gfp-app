package com.example.coletornotificacao

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * Tela de detalhe: notificações de um pacote específico, com estado de sync.
 * Preserva a navegação de agrupamento por aplicativo existente no projeto.
 */
class PackageNotificationsActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter

    private val repository by lazy {
        NotificationRepository(AppDatabase.getDatabase(this).notificationDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_notifications)

        val packageName = intent.getStringExtra("PACKAGE_NAME") ?: return
        title = packageName

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewDetails)
        adapter = NotificationAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            repository.notificationsByPackage(packageName).collect {
                adapter.submitList(it)
            }
        }
    }
}

package com.example.coletornotificacao

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * Tela de notificações capturadas. Mantém a funcionalidade que antes ficava na
 * MainActivity: lista observável do Room, contador de pendentes, sincronização
 * manual, geração de notificação de teste e exclusão segura (só SYNCED).
 *
 * A captura e o WorkManager não dependem desta tela — ela é apenas a visão.
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var txtPendingCount: TextView
    private val CHANNEL_ID = "test_channel"

    private val repository by lazy {
        NotificationRepository(AppDatabase.getDatabase(this).notificationDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        title = "Notificações"

        txtPendingCount = findViewById(R.id.txtPendingCount)

        checkPostNotificationPermission()
        setupRecyclerView()
        setupSyncButton()
        setupTestButton()
        setupClearButton()
        createNotificationChannel()

        observeNotifications()
        observePendingCount()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = NotificationAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            repository.allNotifications.collect { list -> adapter.submitList(list) }
        }
    }

    private fun observePendingCount() {
        lifecycleScope.launch {
            repository.observePendingCount().collect { count ->
                txtPendingCount.text = "Pendentes: $count"
            }
        }
    }

    private fun setupSyncButton() {
        findViewById<Button>(R.id.btnSyncNow).setOnClickListener {
            lifecycleScope.launch {
                repository.requeueFailed()
                SyncScheduler.syncNow(applicationContext)
                Toast.makeText(this@NotificationsActivity, "Sincronização iniciada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClearButton() {
        findViewById<Button>(R.id.btnClearNotifications).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Limpar histórico sincronizado")
                .setMessage(
                    "Isto apaga apenas as notificações já sincronizadas. " +
                        "As pendentes de sincronização serão mantidas. Deseja continuar?"
                )
                .setPositiveButton("Apagar sincronizadas") { _, _ ->
                    lifecycleScope.launch {
                        val removed = repository.deleteSynced()
                        Toast.makeText(
                            this@NotificationsActivity,
                            "Removidas: $removed (pendentes preservadas)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun checkPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101
                )
            }
        }
    }

    private fun setupTestButton() {
        findViewById<Button>(R.id.btnTestNotification).setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Teste de Compra")
                .setContentText("Compra no valor de R$ 50,00 aprovada")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33
            ) {
                NotificationManagerCompat.from(this)
                    .notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Testes", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

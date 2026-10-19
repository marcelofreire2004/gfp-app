package com.example.coletornotificacao

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.coletornotificacao.expense.CardRepository
import com.example.coletornotificacao.expense.CardsActivity
import com.example.coletornotificacao.expense.ExpensesActivity
import com.example.coletornotificacao.ui.Banner
import com.example.coletornotificacao.ui.BannerAdapter
import com.example.coletornotificacao.ui.CardCarouselAdapter
import com.example.coletornotificacao.ui.Service
import com.example.coletornotificacao.ui.ServiceAdapter
import kotlinx.coroutines.launch

/**
 * Tela inicial (home) no estilo de apps de cartão:
 *  - Cabeçalho com o nome do produto (GFP).
 *  - Carrossel de propaganda (banners locais).
 *  - Carrossel de cartões (ou "Adicionar cartão" quando vazio).
 *  - Grade de serviços que navega para as funções do app.
 *
 * A captura de notificações e o WorkManager seguem independentes desta tela.
 * As ações de sincronização/teste/limpeza vivem em [NotificationsActivity].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cardAdapter: CardCarouselAdapter

    private val cardRepository by lazy {
        CardRepository(AppDatabase.getDatabase(this).cardDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        verificarAcessoNotificacoes()
        setupBanners()
        setupCardCarousel()
        setupServices()
        observarCartoes()
    }

    // ── Propaganda ───────────────────────────────────────────────────────────
    private fun setupBanners() {
        val banners = listOf(
            Banner("Controle seus gastos", "Suas compras do cartão, automáticas", R.color.banner1),
            Banner("Sem digitar nada", "O GFP lê as notificações por você", R.color.banner2),
            Banner("Organize por categoria", "Veja para onde vai o seu dinheiro", R.color.banner3)
        )
        findViewById<ViewPager2>(R.id.bannerPager).adapter = BannerAdapter(banners)
    }

    // ── Carrossel de cartões ───────────────────────────────────────────────────
    private fun setupCardCarousel() {
        cardAdapter = CardCarouselAdapter(onAddOrManage = {
            startActivity(Intent(this, CardsActivity::class.java))
        })
        findViewById<ViewPager2>(R.id.cardPager).apply {
            adapter = cardAdapter
            offscreenPageLimit = 1
        }
    }

    private fun observarCartoes() {
        lifecycleScope.launch {
            cardRepository.allCards.collect { cards -> cardAdapter.submit(cards) }
        }
    }

    // ── Serviços ───────────────────────────────────────────────────────────────
    private fun setupServices() {
        val services = listOf(
            Service("💳", "Despesas") { startActivity(Intent(this, ExpensesActivity::class.java)) },
            Service("🔔", "Notificações") { startActivity(Intent(this, NotificationsActivity::class.java)) },
            Service("🗂️", "Meus cartões") { startActivity(Intent(this, CardsActivity::class.java)) },
            Service("🔄", "Sincronizar") {
                lifecycleScope.launch {
                    NotificationRepository(AppDatabase.getDatabase(this@MainActivity).notificationDao()).requeueFailed()
                    SyncScheduler.syncNow(applicationContext)
                    Toast.makeText(this@MainActivity, "Sincronização iniciada", Toast.LENGTH_SHORT).show()
                }
            },
            Service("⚙️", "Acesso a notif.") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
        val grid = findViewById<RecyclerView>(R.id.servicesGrid)
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = ServiceAdapter(services)
    }

    // ── Acesso a notificações ────────────────────────────────────────────────
    private fun verificarAcessoNotificacoes() {
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Ative o acesso às notificações", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, NotificationCaptureService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }
}

package com.example.coletornotificacao

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.coletornotificacao.expense.CardRepository
import com.example.coletornotificacao.expense.CardsActivity
import com.example.coletornotificacao.expense.CategoriesActivity
import com.example.coletornotificacao.expense.ExpensesActivity
import com.example.coletornotificacao.expense.InvoiceActivity
import com.example.coletornotificacao.expense.InvoiceCycle
import com.example.coletornotificacao.ui.Banner
import com.example.coletornotificacao.ui.BannerAdapter
import com.example.coletornotificacao.ui.CardCarouselAdapter
import com.example.coletornotificacao.ui.Service
import com.example.coletornotificacao.ui.ServiceAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tela inicial (home) no estilo de apps de cartão:
 *  - Cabeçalho (GFP) com sino de notificações no topo.
 *  - Carrossel de propaganda (banners locais).
 *  - Carrossel de cartões com valor da fatura, fechamento e vencimento.
 *    Tocar em um cartão abre a fatura ([InvoiceActivity]).
 *  - Grade de serviços.
 *  - Barra de menu no rodapé: Início, Despesa e menu (hambúrguer).
 *
 * O menu hambúrguer (bottom sheet) concentra: acesso a notificações, categorias
 * e cadastro de cartões.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cardAdapter: CardCarouselAdapter

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val cardRepository by lazy { CardRepository(db.cardDao()) }

    /** Jobs de observação dos totais de fatura (um por cartão); recriados a cada lista. */
    private val invoiceJobs = mutableListOf<Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        verificarAcessoNotificacoes()
        setupBell()
        setupBanners()
        setupCardCarousel()
        setupServices()
        setupBottomNav()
        observarCartoes()
    }

    // ── Sino de notificações (topo) ────────────────────────────────────────────
    private fun setupBell() {
        findViewById<View>(R.id.btnBell).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
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
        cardAdapter = CardCarouselAdapter(
            onAddOrManage = { startActivity(Intent(this, CardsActivity::class.java)) },
            onOpenInvoice = { card ->
                startActivity(
                    Intent(this, InvoiceActivity::class.java)
                        .putExtra(InvoiceActivity.EXTRA_CARD_ID, card.id)
                )
            }
        )
        findViewById<ViewPager2>(R.id.cardPager).apply {
            adapter = cardAdapter
            offscreenPageLimit = 1
        }
    }

    private fun observarCartoes() {
        lifecycleScope.launch {
            cardRepository.allCards.collect { cards ->
                cardAdapter.submit(cards)
                // Reconfigura os observadores de total de fatura por cartão.
                invoiceJobs.forEach { it.cancel() }
                invoiceJobs.clear()
                val expenseDao = db.expenseDao()
                for (card in cards) {
                    val cycle = InvoiceCycle.compute(card.dueDay, card.closingDaysBeforeDue)
                    val flow = if (cycle != null)
                        expenseDao.observeInvoiceTotal(card.id, cycle.periodStart, cycle.periodEnd)
                    else
                        expenseDao.observeCardTotal(card.id)
                    val job = lifecycleScope.launch {
                        flow.collect { cents -> cardAdapter.setInvoiceTotal(card.id, cents) }
                    }
                    invoiceJobs.add(job)
                }
            }
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
                    NotificationRepository(db.notificationDao()).requeueFailed()
                    SyncScheduler.syncNow(applicationContext)
                    Toast.makeText(this@MainActivity, "Sincronização iniciada", Toast.LENGTH_SHORT).show()
                }
            }
        )
        val grid = findViewById<RecyclerView>(R.id.servicesGrid)
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = ServiceAdapter(services)
    }

    // ── Barra de menu (rodapé) ──────────────────────────────────────────────────
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_inicio
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> true // já estamos na home
                R.id.nav_despesa -> {
                    startActivity(Intent(this, ExpensesActivity::class.java)); true
                }
                R.id.nav_menu -> { abrirMenuHamburguer(); false }
                else -> false
            }
        }
    }

    private fun abrirMenuHamburguer() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_menu, null)
        view.findViewById<View>(R.id.menuAcessoNotif).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        view.findViewById<View>(R.id.menuCategorias).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, CategoriesActivity::class.java))
        }
        view.findViewById<View>(R.id.menuCartoes).setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, CardsActivity::class.java))
        }
        sheet.setContentView(view)
        // Reposiciona o item de Início como selecionado quando o menu fecha.
        sheet.setOnDismissListener {
            findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_inicio
        }
        sheet.show()
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

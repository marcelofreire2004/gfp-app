package com.example.coletornotificacao.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.coletornotificacao.R

/** Banner de propaganda (placeholder local, sem imagens externas). */
data class Banner(val title: String, val subtitle: String, val colorRes: Int)

/** Adapter do carrossel de propaganda (ViewPager2). */
class BannerAdapter(private val banners: List<Banner>) :
    RecyclerView.Adapter<BannerAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_banner, parent, false)
        return VH(v)
    }

    override fun getItemCount() = banners.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(banners[position])

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val root: LinearLayout = view.findViewById(R.id.bannerRoot)
        private val title: TextView = view.findViewById(R.id.txtBannerTitle)
        private val subtitle: TextView = view.findViewById(R.id.txtBannerSubtitle)

        fun bind(b: Banner) {
            title.text = b.title
            subtitle.text = b.subtitle
            // Tinge o fundo arredondado com a cor do banner.
            val bg = ContextCompat.getDrawable(itemView.context, R.drawable.bg_rounded_banner)?.mutate()
            bg?.setTint(ContextCompat.getColor(itemView.context, b.colorRes))
            root.background = bg
        }
    }
}

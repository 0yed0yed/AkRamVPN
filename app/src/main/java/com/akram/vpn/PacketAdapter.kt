package com.akram.vpn

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PacketAdapter(
    private val items: List<PacketInfo>,
    private val onClick: (PacketInfo) -> Unit
) : RecyclerView.Adapter<PacketAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val protocol: TextView = v.findViewById(R.id.tvProtocol)
        val dest: TextView = v.findViewById(R.id.tvDest)
        val size: TextView = v.findViewById(R.id.tvSize)
        val number: TextView = v.findViewById(R.id.tvNumber)
        val time: TextView = v.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_packet, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.protocol.text = p.protocol
        holder.protocol.setTextColor(
            if (p.protocol == "TCP") Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
        )
        holder.dest.text = p.shortDest()
        holder.size.text = "${p.size} B"
        holder.number.text = "#${p.number}"
        holder.time.text = p.datetime

        holder.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount() = items.size
}

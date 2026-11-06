package ir.netsignal.monitor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * مدل یک ردیفِ سلول (سروینگ یا همسایه) برای نمایش در RecyclerView.
 * مقادیر متنی از قبل به رشته‌های نمایشی (با ارقام بومی یا «—») تبدیل شده‌اند.
 */
data class CellRow(
    val rat: String,
    val generation: String,
    val pciOrPsc: String,
    val cidOrNci: String,
    val tacOrLac: String,
    val earfcnOrArfcn: String,
    val dbm: Int?,
    val registered: Boolean,
    val distanceText: String = "—",
    val bandText: String = "—"
)

/**
 * آداپتور لیست سلول‌های اطراف / سروینگ.
 */
class CellAdapter : RecyclerView.Adapter<CellAdapter.CellViewHolder>() {

    private val items = mutableListOf<CellRow>()

    fun submit(rows: List<CellRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    class CellViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badge: TextView = itemView.findViewById(R.id.tvCellBadge)
        private val rat: TextView = itemView.findViewById(R.id.tvCellRat)
        private val ids: TextView = itemView.findViewById(R.id.tvCellIds)
        private val dbm: TextView = itemView.findViewById(R.id.tvCellDbm)
        private val distance: TextView = itemView.findViewById(R.id.tvCellDistance)

        fun bind(row: CellRow) {
            val context: Context = itemView.context
            // اگر ردیف از مسیر جایگزین (بدون CellInfo) آمده باشد نسل خالی است
            badge.text = row.generation.ifBlank { "—" }
            badge.setTextColor(badgeColor(context, row.generation))
            rat.text = row.rat + " • " + context.getString(
                if (row.registered) R.string.tag_serving else R.string.tag_neighbor
            )
            val bandPart = if (row.bandText != "—" && row.bandText.isNotBlank()) {
                context.getString(R.string.cell_band_fmt, row.bandText) + " • "
            } else {
                ""
            }
            ids.text = bandPart +
                "PCI/PSC: " + row.pciOrPsc +
                "  |  CID/NCI: " + row.cidOrNci +
                "\nTAC/LAC: " + row.tacOrLac +
                "  |  EARFCN/ARFCN: " + row.earfcnOrArfcn
            dbm.text = row.dbm?.let { it.toString().toDigits() + " dBm" } ?: "—"
            distance.text = row.distanceText
        }

        private fun badgeColor(context: Context, generation: String): Int =
            ContextCompat.getColor(
                context,
                when (generation) {
                    "5G" -> R.color.gen_5g
                    "4G", "4.5G" -> R.color.gen_4g
                    "3G", "3.9G" -> R.color.gen_3g
                    "2G" -> R.color.gen_2g
                    else -> R.color.gen_unknown
                }
            )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cell, parent, false)
        return CellViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        holder.bind(items[position])
    }
}

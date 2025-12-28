import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import DiaryEntry
import com.oddworks.onelogs.FullscreenImageActivity
import com.oddworks.onelogs.R

sealed class TimelineItem {
    data class DateSeparator(val date: String) : TimelineItem()
    data class EntryItem(val entry: DiaryEntry) : TimelineItem()
    object LoadingItem : TimelineItem()  // add this
}
private val expandedStates = mutableMapOf<Int, Boolean>()
private val noteExpandedStates = mutableMapOf<Int, Boolean>()
class TimelineAdapter(
    private var items: MutableList<TimelineItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_DATE_SEPARATOR = 0
        const val TYPE_ENTRY = 1
        const val TYPE_LOADING = 2
    }

    private var isLoading = false
    var onLoadMore: (() -> Unit)? = null

    inner class DateSeparatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateSeparatorText: TextView = view.findViewById(R.id.dateSeparatorText)
    }

    inner class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val progressBar: ProgressBar = view.findViewById(R.id.loadingProgressBar)
    }
    private fun resolveImageFile(context: android.content.Context, filepath: String?): java.io.File? {
        if (filepath.isNullOrEmpty()) return null

        return if (filepath.startsWith("onelogs_images/")) {
            // relative internal path → resolve against app internal filesDir
            java.io.File(context.filesDir, filepath)
        } else {
            // absolute path (e.g., camera image in public Pictures)
            java.io.File(filepath)
        }
    }


    inner class TimelineViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTask: TextView = view.findViewById(R.id.textTask)
        val imageEntry: ImageView = view.findViewById(R.id.imageEntry)
        val audioContainer: LinearLayout = view.findViewById(R.id.audioContainer)
        val dateTime: TextView = view.findViewById(R.id.dateTime)
        val logbookName: TextView = view.findViewById(R.id.logbookName)
        val note: TextView = view.findViewById(R.id.note)
        val taskContainer: LinearLayout = view.findViewById(R.id.taskContainer)
        val taskCheckbox: ImageView = view.findViewById(R.id.taskCheckbox)
        val taskText: TextView = view.findViewById(R.id.taskText)
        val readMore: TextView = view.findViewById(R.id.readMore)
        val noteReadMore: TextView = view.findViewById(R.id.noteReadMore)  // must be here
    }



    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TimelineItem.DateSeparator -> TYPE_DATE_SEPARATOR
            is TimelineItem.EntryItem -> TYPE_ENTRY
            is TimelineItem.LoadingItem -> TYPE_LOADING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }
            TYPE_DATE_SEPARATOR -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_timeline_date_separator, parent, false)
                DateSeparatorViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_timeline_layout, parent, false)
                TimelineViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LoadingViewHolder -> {
                if (!isLoading) {
                    isLoading = true
                    onLoadMore?.invoke()
                }
            }
            is DateSeparatorViewHolder -> {
                val item = items[position] as TimelineItem.DateSeparator
                holder.dateSeparatorText.text = item.date
            }
            is TimelineViewHolder -> {

                val item = items[position] as TimelineItem.EntryItem
                val entry = item.entry

                // pick which main text view we are collapsing/expanding
                val isTask = entry.entryType == "TASK"
                val mainTextView = if (isTask) holder.taskText else holder.textTask

                // default visibility reset
                holder.taskContainer.visibility = if (isTask) View.VISIBLE else View.GONE
                holder.textTask.visibility = if (!isTask && entry.entryType == "TEXT") View.VISIBLE else View.GONE
                holder.imageEntry.visibility = View.GONE
                holder.audioContainer.visibility = View.GONE

                // set text content for TEXT / TASK
                when (entry.entryType) {
                    "TEXT" -> {
                        holder.textTask.text = entry.textTask
                    }
                    "TASK" -> {
                        holder.taskText.text = entry.textTask
                        val done = entry.taskStat == "DONE" || entry.taskStat == "true"
                        holder.taskCheckbox.setImageResource(
                            if (done) R.drawable.ic_selectedbox else R.drawable.ic_blankbox
                        )
                    }
                    "IMAGE" -> {
                        holder.taskContainer.visibility = View.GONE
                        holder.textTask.visibility = View.GONE
                        holder.imageEntry.visibility = View.VISIBLE
                        holder.audioContainer.visibility = View.GONE

                        if (!entry.filepath.isNullOrEmpty()) {
                            val ctx = holder.itemView.context
                            val file = resolveImageFile(ctx, entry.filepath)
                            val decodedBitmap = if (file != null && file.exists()) {
                                BitmapFactory.decodeFile(file.absolutePath)
                            } else null

                            if (decodedBitmap != null) {
                                holder.imageEntry.setImageBitmap(decodedBitmap)
                            } else {
                                holder.imageEntry.visibility = View.GONE
                            }

                            holder.imageEntry.setOnClickListener {
                                val intent = Intent(ctx, FullscreenImageActivity::class.java)
                                intent.putExtra("image_path", file?.absolutePath ?: entry.filepath)
                                ctx.startActivity(intent)
                            }
                        } else {
                            holder.imageEntry.visibility = View.GONE
                        }
                    }

                    "AUDIO" -> {
                        holder.taskContainer.visibility = View.GONE
                        holder.textTask.visibility = View.GONE
                        holder.imageEntry.visibility = View.GONE
                        holder.audioContainer.visibility = View.VISIBLE
                    }
                    else -> {
                        holder.taskContainer.visibility = View.GONE
                        holder.textTask.visibility = View.VISIBLE
                        holder.imageEntry.visibility = View.GONE
                        holder.audioContainer.visibility = View.GONE
                        holder.textTask.text = entry.textTask
                    }
                }

                // expand/collapse ONLY for TEXT and TASK
                if (entry.entryType == "TEXT" || entry.entryType == "TASK") {
                    val isExpanded = expandedStates[position] ?: false

                    mainTextView.maxLines = if (isExpanded) Int.MAX_VALUE else 8
                    holder.readMore.text = if (isExpanded) "Read less text" else "Read more text"
                    holder.readMore.setTextColor(
                        holder.itemView.context.getColor(R.color.md_theme_dark_primaryContainers)
                    )

                    mainTextView.post {
                        holder.readMore.visibility =
                            if (mainTextView.lineCount > 8 || isExpanded) View.VISIBLE else View.GONE
                    }

                    holder.readMore.setOnClickListener {
                        expandedStates[position] = !isExpanded
                        notifyItemChanged(position)
                    }
                } else {
                    // no read-more for image/audio
                    holder.readMore.visibility = View.GONE
                }

                // footer
                holder.dateTime.text = "${entry.firstEntryDate} ${entry.firstTimeStamp}"
                holder.logbookName.text = entry.logbookName
                holder.note.text = if (!entry.note.isNullOrEmpty()) "Note: ${entry.note}" else ""

                // NOTE: expand/collapse for note, if present
                val noteExpanded = noteExpandedStates[position] ?: false

                if (!entry.note.isNullOrEmpty()) {
                    holder.note.visibility = View.VISIBLE
                    holder.note.text = "Note: ${entry.note}"
                    holder.note.maxLines = if (noteExpanded) Int.MAX_VALUE else 3

                    holder.note.post {
                        holder.noteReadMore.visibility =
                            if (holder.note.lineCount > 3 || noteExpanded) View.VISIBLE else View.GONE
                    }

                    holder.noteReadMore.text = if (noteExpanded) "Read less note" else "Read more note"
                    holder.noteReadMore.setOnClickListener {
                        noteExpandedStates[position] = !noteExpanded
                        notifyItemChanged(position)
                    }
                } else {
                    holder.note.visibility = View.GONE
                    holder.noteReadMore.visibility = View.GONE
                }


            }
        }
    }


    override fun getItemCount(): Int = items.size

    fun addMoreItemsAtTop(newItems: List<TimelineItem>) {
        if (newItems.isEmpty()) return
        items.addAll(0, newItems)
        notifyItemRangeInserted(0, newItems.size)
    }




    fun startLoading() {
        isLoading = true
        items.add(TimelineItem.LoadingItem)
        notifyItemInserted(items.size - 1)
    }

    fun addMoreItems(newItems: List<TimelineItem>) {
        // Only remove loading item if it exists
        if (items.isNotEmpty() && items.last() is TimelineItem.LoadingItem) {
            val insertPosition = items.size - 1
            items.removeAt(insertPosition)
            notifyItemRemoved(insertPosition)
        }

        val insertPosition = items.size
        items.addAll(newItems)
        isLoading = false
        notifyItemRangeInserted(insertPosition, newItems.size)
    }


    fun update(newItems: MutableList<TimelineItem>) {
        items = newItems
        isLoading = false
        notifyDataSetChanged()
    }
}

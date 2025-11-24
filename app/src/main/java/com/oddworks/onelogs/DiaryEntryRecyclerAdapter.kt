import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.oddworks.onelogs.LogBookDetailActivity
import com.oddworks.onelogs.R

class DiaryEntryRecyclerAdapter(
    var items: List<DiaryEntry>
) : RecyclerView.Adapter<DiaryEntryRecyclerAdapter.DiaryViewHolder>() {
    private var onEntryLongClickListener: ((DiaryEntry) -> Unit)? = null

    fun setOnEntryLongClickListener(listener: (DiaryEntry) -> Unit) {
        onEntryLongClickListener = listener
    }
    var bubbleIndex: Int? = null

    fun showBubbleAt(index: Int) {
        bubbleIndex = index
        notifyItemChanged(index)
    }

    private val expandedStates = mutableMapOf<Int, Boolean>()

    inner class DiaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTask: TextView = view.findViewById(R.id.textTask)
        val imageEntry: ImageView = view.findViewById(R.id.imageEntry)
        val audioContainer: LinearLayout = view.findViewById(R.id.audioContainer)
        val dateTime: TextView = view.findViewById(R.id.dateTime)
        val note: TextView = view.findViewById(R.id.note)
        val readMore: TextView = view.findViewById(R.id.readMore)
        val taskContainer: LinearLayout = view.findViewById(R.id.taskContainer)
        val taskCheckbox: ImageView = view.findViewById(R.id.taskCheckbox)
        val taskText: TextView = view.findViewById(R.id.taskText)
    }
    var highlightIndex: Int? = null

    fun highlightEntry(index: Int) {
        highlightIndex = index
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_diary_entry, parent, false)
        return DiaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        val entry = items[position]

        // --- UI SETUP (your display/type logic) ---
        when (entry.entryType) {
            "TEXT" -> {
                holder.taskContainer.visibility = View.GONE
                holder.textTask.visibility = View.VISIBLE
                holder.imageEntry.visibility = View.GONE
                holder.audioContainer.visibility = View.GONE
                holder.textTask.text = entry.textTask
            }
            "TASK" -> {
                holder.taskContainer.visibility = View.VISIBLE
                holder.textTask.visibility = View.GONE
                holder.imageEntry.visibility = View.GONE
                holder.audioContainer.visibility = View.GONE
                holder.taskText.text = entry.textTask
                if (entry.taskStat == "DONE" || entry.taskStat == "true") {
                    holder.taskCheckbox.setImageResource(R.drawable.ic_selectedbox)
                } else {
                    holder.taskCheckbox.setImageResource(R.drawable.ic_blankbox)
                }
            }
            "IMAGE" -> {
                holder.taskContainer.visibility = View.GONE
                holder.textTask.visibility = View.GONE
                holder.imageEntry.visibility = View.VISIBLE
                holder.audioContainer.visibility = View.GONE
                if (!entry.filepath.isNullOrEmpty()) {
                    val decodedBitmap = BitmapFactory.decodeFile(entry.filepath)
                    if (decodedBitmap != null) {
                        holder.imageEntry.setImageBitmap(decodedBitmap)
                        holder.imageEntry.visibility = View.VISIBLE
                        holder.textTask.visibility = View.GONE
                    } else {
                        holder.imageEntry.visibility = View.GONE
                        holder.textTask.text = entry.filepath
                        holder.textTask.visibility = View.VISIBLE
                    }
                    holder.imageEntry.setOnClickListener {
                        val intent = Intent(holder.itemView.context, com.oddworks.onelogs.FullscreenImageActivity::class.java)
                        intent.putExtra("image_path", entry.filepath)
                        holder.itemView.context.startActivity(intent)
                    }
                } else {
                    holder.imageEntry.visibility = View.GONE
                    holder.textTask.visibility = View.GONE
                }
            }
            "AUDIO" -> {
                holder.taskContainer.visibility = View.GONE
                holder.textTask.visibility = View.GONE
                holder.imageEntry.visibility = View.GONE
                holder.audioContainer.visibility = View.VISIBLE
                // TODO: Add audio logic if needed
            }
            else -> {
                holder.taskContainer.visibility = View.GONE
                holder.textTask.visibility = View.VISIBLE
                holder.imageEntry.visibility = View.GONE
                holder.audioContainer.visibility = View.GONE
                holder.textTask.text = entry.textTask
            }
        }
        val linkedBtn = holder.itemView.findViewById<ImageButton>(R.id.linkedIdButton)

        if (entry.linkedId != null) {
            linkedBtn.visibility = View.VISIBLE
            linkedBtn.setOnClickListener {
                val list = items // all items, sorted by EntryUniqueID
                val idx = list.indexOfFirst { it.entryUniqueId == entry.linkedId }
                if (idx != -1) {
                    val activity = holder.itemView.context as? LogBookDetailActivity
                    activity?.entryRecyclerView?.scrollToPosition(idx)
                    // Highlight
                    (activity?.entryRecyclerView?.adapter as? DiaryEntryRecyclerAdapter)?.highlightEntry(idx)
                } else {
                    Toast.makeText(holder.itemView.context, "Linked entry not found!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            linkedBtn.visibility = View.GONE
        }

        // --- Expand/collapse logic for textTask ---
        val isExpanded = expandedStates[position] ?: false
        holder.textTask.maxLines = if (isExpanded) Int.MAX_VALUE else 20
        holder.readMore.text = if (isExpanded) "Read less" else "Read more"
        holder.readMore.setTextColor(holder.itemView.context.getColor(R.color.md_theme_dark_primaryContainers))
        holder.textTask.post {
            holder.readMore.visibility = if (holder.textTask.lineCount >= 20 || isExpanded) View.VISIBLE else View.GONE
        }
        holder.readMore.setOnClickListener {
            expandedStates[position] = !isExpanded
            notifyItemChanged(position)
        }

        // Set date/time and note
        holder.dateTime.text = "${entry.firstEntryDate} ${entry.firstTimeStamp}"
        holder.dateTime.setTextColor(holder.itemView.context.getColor(R.color.md_theme_dark_onSurface2))
        if (!entry.note.isNullOrEmpty()) {
            holder.note.text = "Note: ${entry.note}"
            holder.note.visibility = View.VISIBLE
        } else {
            holder.note.visibility = View.GONE
        }
        holder.note.setTextColor(holder.itemView.context.getColor(R.color.md_theme_dark_tertiary))

        // --- LONG PRESS DIALOG LOGIC ---
        holder.itemView.setOnLongClickListener {
            val context = holder.itemView.context
            val activity = context as? LogBookDetailActivity

            val optionsList = mutableListOf<String>()
            var linkOptionIndex: Int? = null

            // --- DYNAMIC "Link as reply"/"Reassign linked id" OPTION ---
            val replyId = activity?.replyModeLinkedId
            if (replyId != null && replyId != entry.entryUniqueId) {
                val alreadyLinked = entry.linkedId != null
                optionsList.add(if (alreadyLinked) "Reassign linked id" else "Link as reply")
                linkOptionIndex = 0
            }

            // --- NORMAL OPTIONS ---
            if (entry.entryType == "TEXT") {
                optionsList.addAll(listOf("Edit text", "Add/Edit Note", "Convert to task", "Delete"))
            } else if (entry.entryType == "TASK") {
                val isDone = entry.taskStat == "DONE" || entry.taskStat == "true"
                optionsList.addAll(listOf(
                    "Edit task",
                    if (!isDone) "Mark as complete" else "Mark as incomplete",
                    "Convert to text",
                    "Add/Edit Note",
                    "Delete"
                ))
            }

            android.app.AlertDialog.Builder(context)
                .setTitle("Entry Options")
                .setItems(optionsList.toTypedArray()) { _, which ->
                    // Linking logic — if option present AND selected, delegate to Activity for linking logic
                    if (linkOptionIndex != null && which == linkOptionIndex) {
                        activity?.handleReplyLinkAction(entry)
                        return@setItems
                    }
                    // Otherwise, handle as regular options — NOTE: need to adjust index if link present
                    val actual = if (linkOptionIndex != null) which - 1 else which

                    if (entry.entryType == "TEXT") {
                        when (actual) {
                            0 -> {  // Edit text
                                val editText = EditText(context)
                                editText.setText(entry.textTask)
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Edit Entry")
                                    .setView(editText)
                                    .setPositiveButton("Save") { _, _ ->
                                        val newText = editText.text.toString().trim()
                                        if (newText.isNotEmpty()) {
                                            (context as? LogBookDetailActivity)?.dbHelper?.updateDiaryEntryText(
                                                context.tableName, entry.entryUniqueId, newText
                                            )
                                            // --- line immediately above ---
                                            val recyclerView = (context as? LogBookDetailActivity)?.entryRecyclerView
                                            val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                            val currentPosition = holder.adapterPosition

                                            (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                            layoutManager?.scrollToPosition(currentPosition)
// --- line immediately below ---

                                        } else {
                                            Toast.makeText(context, "Empty text not allowed.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            1 -> {  // Add/Edit Note
                                val noteEditText = EditText(context)
                                noteEditText.setText(entry.note ?: "")
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Add/Edit Note")
                                    .setView(noteEditText)
                                    .setPositiveButton("Save") { _, _ ->
                                        val newNote = noteEditText.text.toString().trim()
                                        (context as? LogBookDetailActivity)?.dbHelper?.updateDiaryEntryNote(
                                            context.tableName, entry.entryUniqueId, newNote
                                        )
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            2 -> { // Convert to task
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Convert to Task")
                                    .setMessage("Are you sure you want to convert this entry to a Task?\nThis will enable task features and checkbox!")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val recyclerView = (context as? LogBookDetailActivity)?.entryRecyclerView
                                        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                        val currentPosition = holder.adapterPosition

                                        (context as? LogBookDetailActivity)?.dbHelper?.convertDiaryEntryToTask(
                                            context.tableName, entry.entryUniqueId
                                        )
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                        layoutManager?.scrollToPosition(currentPosition)
                                    }

                                    .setNegativeButton("No", null)
                                    .show()
                            }
                            3 -> { // Delete
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Delete Entry")
                                    .setMessage("Are you sure you want to delete this entry?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val recyclerView = (context as? LogBookDetailActivity)?.entryRecyclerView
                                        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                        val currentPosition = holder.adapterPosition

                                        (context as? LogBookDetailActivity)?.dbHelper?.convertDiaryEntryToTask(
                                            context.tableName, entry.entryUniqueId
                                        )
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                        layoutManager?.scrollToPosition(currentPosition)
                                    }

                                    .setNegativeButton("No", null)
                                    .show()
                            }
                        }
                    } else if (entry.entryType == "TASK") {
                        val isDone = entry.taskStat == "DONE" || entry.taskStat == "true"
                        when (actual) {
                            0 -> { // Edit task
                                val editText = EditText(context)
                                editText.setText(entry.textTask)
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Edit Task")
                                    .setView(editText)
                                    .setPositiveButton("Save") { _, _ ->
                                        val newText = editText.text.toString().trim()
                                        if (newText.isNotEmpty()) {
                                            (context as? LogBookDetailActivity)?.dbHelper?.updateDiaryEntryText(
                                                context.tableName, entry.entryUniqueId, newText
                                            )
                                            (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                        } else {
                                            Toast.makeText(context, "Empty text not allowed.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            1 -> { // Mark as complete/incomplete
                                val newStat = if (isDone) "TODO" else "DONE"
                                val db = (context as? LogBookDetailActivity)?.dbHelper?.writableDatabase
                                val values = android.content.ContentValues().apply { put("TaskStat", newStat) }
                                db?.update(context.tableName, values, "Entry_Unique_ID=?", arrayOf(entry.entryUniqueId.toString()))
                                db?.close()
                                (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                            }
                            2 -> { // Convert to text
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Convert to Text")
                                    .setMessage("Are you sure you want to convert this Task to a normal text entry?\nTask checkbox/features will be removed.")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val db = (context as? LogBookDetailActivity)?.dbHelper?.writableDatabase
                                        val values = android.content.ContentValues().apply {
                                            put("EntryType", "TEXT")
                                            put("TaskStat", null as String?)
                                        }
                                        db?.update(context.tableName, values, "Entry_Unique_ID=?", arrayOf(entry.entryUniqueId.toString()))
                                        db?.close()
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                    }
                                    .setNegativeButton("No", null)
                                    .show()
                            }
                            3 -> { // Add/Edit Note
                                val noteEditText = EditText(context)
                                noteEditText.setText(entry.note ?: "")
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Add/Edit Note")
                                    .setView(noteEditText)
                                    .setPositiveButton("Save") { _, _ ->
                                        val newNote = noteEditText.text.toString().trim()
                                        (context as? LogBookDetailActivity)?.dbHelper?.updateDiaryEntryNote(
                                            context.tableName, entry.entryUniqueId, newNote
                                        )
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            4 -> { // Delete
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Delete Task")
                                    .setMessage("Are you sure you want to delete this task?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val recyclerView = (context as? LogBookDetailActivity)?.entryRecyclerView
                                        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                        val currentPosition = holder.adapterPosition

                                        (context as? LogBookDetailActivity)?.dbHelper?.convertDiaryEntryToTask(
                                            context.tableName, entry.entryUniqueId
                                        )
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                        layoutManager?.scrollToPosition(currentPosition)
                                    }

                                    .setNegativeButton("No", null)
                                    .show()
                            }
                        }
                    }
                }
                .show()
            true
        }
        val overlay = holder.itemView.findViewById<View>(R.id.highlightOverlay)
        if (highlightIndex == position) {
            overlay.visibility = View.VISIBLE
            overlay.alpha = 1f
            overlay.animate()
                .alpha(0f)
                .setDuration(2000)
                .withEndAction {
                    overlay.visibility = View.GONE
                    overlay.alpha = 1f
                    highlightIndex = null
                }
                .start()
        } else {
            overlay.visibility = View.GONE
            overlay.alpha = 1f
        }




    }


    override fun getItemCount(): Int = items.size

    fun update(newItems: List<DiaryEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}

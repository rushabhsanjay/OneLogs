import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
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
    private val noteExpandedStates = mutableMapOf<Int, Boolean>()

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
        val noteReadMore: TextView = view.findViewById(R.id.noteReadMore)   // add this
    }

    var highlightIndex: Int? = null

    fun highlightEntry(index: Int) {
        highlightIndex = index
        notifyItemChanged(index)
    }

    private fun resolveImageFile(context: Context, filepath: String?): java.io.File? {
        if (filepath.isNullOrEmpty()) return null

        return if (filepath.startsWith("onelogs_images/")) {
            // relative internal path → resolve against filesDir
            java.io.File(context.filesDir, filepath)
        } else {
            // absolute path (camera/public image)
            java.io.File(filepath)
        }
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
                    val context = holder.itemView.context
                    val file = resolveImageFile(context, entry.filepath)
                    val decodedBitmap = if (file != null && file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else null

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
                        val intent = Intent(context, com.oddworks.onelogs.FullscreenImageActivity::class.java)
                        intent.putExtra("image_path", file?.absolutePath ?: entry.filepath)
                        context.startActivity(intent)
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
        holder.textTask.maxLines = if (isExpanded) Int.MAX_VALUE else 8
        holder.readMore.text = if (isExpanded) "Read less text" else "Read more text"
        holder.readMore.setTextColor(holder.itemView.context.getColor(R.color.md_theme_dark_primaryContainers))
        holder.textTask.post {
            holder.readMore.visibility = if (holder.textTask.lineCount >= 8 || isExpanded) View.VISIBLE else View.GONE
        }
        holder.readMore.setOnClickListener {
            expandedStates[position] = !isExpanded
            notifyItemChanged(position)
        }

        // Set date/time and note
        holder.dateTime.text = "${entry.firstEntryDate} ${entry.firstTimeStamp}"
        holder.dateTime.setTextColor(holder.itemView.context.getColor(R.color.md_theme_dark_onSurface2))
        if (!entry.note.isNullOrEmpty()) {
            holder.note.visibility = View.VISIBLE

            val noteExpanded = noteExpandedStates[position] ?: false
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
                optionsList.addAll(listOf("Edit text","Copy text", "Add/Edit Note", "Convert to task", "Delete"))
            } else if (entry.entryType == "TASK") {
                val isDone = entry.taskStat == "DONE" || entry.taskStat == "true"
                optionsList.addAll(listOf(
                    "Edit task",
                    "Copy task",
                    if (!isDone) "Mark as complete" else "Mark as incomplete",
                    "Convert to text",
                    "Add/Edit Note",
                    "Delete"
                ))
            } else if (entry.entryType == "IMAGE") {
                val imagePath = entry.filepath ?: ""   // you use entry.filepath for images
                val isInternal = imagePath.startsWith(context.filesDir.path)

                optionsList.add("Open in gallery")  // index 0 (or 1 if link option exists)
                optionsList.add("Share")

                if (isInternal) {
                    // uploaded image saved in app internal dir
                    optionsList.add("Delete entry only")
                    optionsList.add("Delete entry and image")
                } else {
                    // camera image in public storage
                    optionsList.add("Delete entry only")
                    optionsList.add("Delete image and entry")
                }
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
                            1 -> { // Copy text
                                val context = holder.itemView.context
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Diary entry", entry.textTask ?: "")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            2 -> {  // Add/Edit Note
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


                            3 -> { // Convert to task
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
                            4 -> { // Delete
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Delete Entry")
                                    .setMessage("Are you sure you want to delete this entry?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val recyclerView = (context as? LogBookDetailActivity)?.entryRecyclerView
                                        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                        val currentPosition = holder.adapterPosition

                                        (context as? LogBookDetailActivity)?.dbHelper?.markDiaryEntryDeleted(
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
                            1 -> { // Copy task (with status)
                                val context = holder.itemView.context
                                val taskText = entry.textTask ?: ""
                                val statusLabel = if (isDone) "Task is completed." else "Task is pending."
                                val text = "${taskText}\n$statusLabel".trim()

                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Task", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Task copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            2 -> { // Mark as complete/incomplete
                                val newStat = if (isDone) "TODO" else "DONE"
                                val db = (context as? LogBookDetailActivity)?.dbHelper?.writableDatabase
                                val values = android.content.ContentValues().apply { put("TaskStat", newStat) }
                                db?.update(context.tableName, values, "Entry_Unique_ID=?", arrayOf(entry.entryUniqueId.toString()))
                                db?.close()
                                (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                            }
                            3 -> { // Convert to text
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
                            4 -> { // Add/Edit Note
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
                            5 -> { // Delete
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("Delete Task")
                                    .setMessage("Are you sure you want to delete this task?")
                                    .setPositiveButton("Yes") { _, _ ->
                                        val recyclerView = (context as? LogBookDetailActivity)?.entryRecyclerView
                                        val layoutManager = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                                        val currentPosition = holder.adapterPosition

                                        (context as? LogBookDetailActivity)?.dbHelper?.markDiaryEntryDeleted(
                                            context.tableName, entry.entryUniqueId
                                        )
                                        (context as? LogBookDetailActivity)?.refreshEntries(context.tableName)
                                        layoutManager?.scrollToPosition(currentPosition)
                                    }

                                    .setNegativeButton("No", null)
                                    .show()
                            }
                        }
                    } else if (entry.entryType == "IMAGE") {
                        handleImageOptions(context, activity, entry, actual)
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

        private fun handleImageOptions(
            context: Context,
            activity: LogBookDetailActivity?,
            entry: DiaryEntry,
            actual: Int
        ) {
            val imagePath = entry.filepath ?: return
            val file = resolveImageFile(context, imagePath) ?: return
        Log.d("ImageShare", "path=$imagePath exists=${file.exists()} size=${file.length()}")



        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Image file not found or empty", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        Log.d("ImageShare", "uri=$uri")

        when (actual) {
            0 -> { // Open in gallery
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Explicitly grant permission to all possible receivers
                val resInfoList = context.packageManager.queryIntentActivities(intent, 0)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                try {
                    context.startActivity(Intent.createChooser(intent, "Open image"))
                } catch (e: Exception) {
                    Toast.makeText(context, "No app can open this image", Toast.LENGTH_SHORT).show()
                }
            }

            1 -> { // Share
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Explicitly grant to all share targets
                val resInfoList = context.packageManager.queryIntentActivities(shareIntent, 0)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                try {
                    context.startActivity(Intent.createChooser(shareIntent, "Share image"))
                } catch (e: Exception) {
                    Toast.makeText(context, "No app can handle sharing", Toast.LENGTH_SHORT).show()
                }
            }
            // 2 and 3 stay same
        2 -> { // Delete entry only
                android.app.AlertDialog.Builder(context)
                    .setTitle("Delete Entry")
                    .setMessage("Delete entry only? Image will remain on device.")
                    .setPositiveButton("Delete") { _, _ ->
                        activity?.dbHelper?.markDiaryEntryDeleted(activity.tableName, entry.entryUniqueId)
                        activity?.refreshEntries(activity.tableName)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            3 -> { // Delete entry + image
                android.app.AlertDialog.Builder(context)
                    .setTitle("Delete Image and Entry")
                    .setMessage("Permanently delete image file from storage and the entry?")
                    .setPositiveButton("Delete") { _, _ ->
                        try { file.delete() } catch (_: Exception) {}
                        activity?.dbHelper?.markDiaryEntryDeleted(activity.tableName, entry.entryUniqueId)
                        activity?.refreshEntries(activity.tableName)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }


}

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<DiaryEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}

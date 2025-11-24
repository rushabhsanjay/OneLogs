import android.content.Context
import android.net.Uri
import android.util.SparseBooleanArray
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import com.oddworks.onelogs.R

class DiaryEntryAdapter(context: Context, data: List<DiaryEntry>) :
    ArrayAdapter<DiaryEntry>(context, 0, data) {

    // Track expanded states for each row
    private val expandedStates = SparseBooleanArray()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val entry = getItem(position)
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_diary_entry, parent, false)

        val textTask = view.findViewById<TextView>(R.id.textTask)
        val imageEntry = view.findViewById<ImageView>(R.id.imageEntry)
        val audioContainer = view.findViewById<LinearLayout>(R.id.audioContainer)
        val dateTimeView = view.findViewById<TextView>(R.id.dateTime)
        val noteView = view.findViewById<TextView>(R.id.note)
        val readMore = view.findViewById<TextView>(R.id.readMore)
        val taskContainer = view.findViewById<LinearLayout>(R.id.taskContainer)
        val taskCheckbox = view.findViewById<ImageView>(R.id.taskCheckbox)
        val taskText = view.findViewById<TextView>(R.id.taskText)


        // FUTURE-PROOF display logic for entry types
        when (entry?.entryType) {
            "TEXT" -> {
                taskContainer.visibility = View.GONE
                textTask.visibility = View.VISIBLE
                imageEntry.visibility = View.GONE
                audioContainer.visibility = View.GONE
                textTask.text = entry?.textTask
            }

            "TASK" -> {
                taskContainer.visibility = View.VISIBLE
                textTask.visibility = View.GONE
                imageEntry.visibility = View.GONE
                audioContainer.visibility = View.GONE
                taskText.text = entry?.textTask

                // Show correct icon based on taskStat
                if (entry?.taskStat == "DONE" || entry?.taskStat == "true") {
                    taskCheckbox.setImageResource(R.drawable.ic_selectedbox)
                } else {
                    taskCheckbox.setImageResource(R.drawable.ic_blankbox)
                }
            }

            "IMAGE" -> {
                taskContainer.visibility = View.GONE
                textTask.visibility = View.GONE
                imageEntry.visibility = View.VISIBLE
                audioContainer.visibility = View.GONE

                if (!entry?.filepath.isNullOrEmpty()) {
                    val decodedBitmap = android.graphics.BitmapFactory.decodeFile(entry.filepath)
                    if (decodedBitmap != null) {
                        imageEntry.setImageBitmap(decodedBitmap)
                        imageEntry.visibility = View.VISIBLE
                        textTask.visibility = View.GONE
                    } else {
                        imageEntry.visibility = View.GONE
                        textTask.text = entry.filepath // Show the path in existing TextView
                        textTask.visibility = View.VISIBLE
                    }
                    imageEntry.setOnClickListener {
                        val intent = android.content.Intent(context, com.oddworks.onelogs.FullscreenImageActivity::class.java)
                        intent.putExtra("image_path", entry.filepath)
                        context.startActivity(intent)
                    }
                } else {
                    imageEntry.visibility = View.GONE
                    textTask.visibility = View.GONE
                }


            }





            "AUDIO" -> {
                taskContainer.visibility = View.GONE
                textTask.visibility = View.GONE
                imageEntry.visibility = View.GONE
                audioContainer.visibility = View.VISIBLE
                // TODO: Add audio logic later
            }

            else -> {
                taskContainer.visibility = View.GONE
                textTask.visibility = View.VISIBLE
                imageEntry.visibility = View.GONE
                audioContainer.visibility = View.GONE
                textTask.text = entry?.textTask
            }

        }

        // Expand/collapse logic for textTask
        val isExpanded = expandedStates.get(position, false)
        textTask.maxLines = if (isExpanded) Integer.MAX_VALUE else 20

        readMore.text = if (isExpanded) "Read less" else "Read more"
        readMore.setTextColor(context.getColor(R.color.md_theme_dark_primaryContainers))
// Only show readMore if textTask has more than 4 lines
        textTask.post {
            readMore.visibility = if (textTask.lineCount >= 20 || isExpanded) View.VISIBLE else View.GONE
        }

        readMore.setOnClickListener {
            expandedStates.put(position, !isExpanded)
            notifyDataSetChanged()
        }


        readMore.setOnClickListener {
            expandedStates.put(position, !isExpanded)
            notifyDataSetChanged()
        }

        // Set date/time color and value
        dateTimeView.text = "${entry?.firstEntryDate} ${entry?.firstTimeStamp}"
        dateTimeView.setTextColor(context.getColor(R.color.md_theme_dark_onSurface2))

        // Set note color and value
        if (!entry?.note.isNullOrEmpty()) {
            noteView.text = "Note: ${entry.note}"
            noteView.visibility = View.VISIBLE
        } else {
            noteView.visibility = View.GONE
        }
        noteView.setTextColor(context.getColor(R.color.md_theme_dark_tertiary))

        return view
    }


}

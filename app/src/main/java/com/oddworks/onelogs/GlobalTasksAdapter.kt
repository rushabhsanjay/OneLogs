package com.oddworks.onelogs

import DiaryEntry
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GlobalTasksAdapter(
    private var items: MutableList<DiaryEntry>
) : RecyclerView.Adapter<GlobalTasksAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTask: TextView = view.findViewById(R.id.textTask)
        val taskContainer: LinearLayout = view.findViewById(R.id.taskContainer)
        val taskCheckbox: ImageView = view.findViewById(R.id.taskCheckbox)
        val taskText: TextView = view.findViewById(R.id.taskText)
        val dateTime: TextView = view.findViewById(R.id.dateTime)
        val logbookName: TextView = view.findViewById(R.id.logbookName)
        val note: TextView = view.findViewById(R.id.note)
        val readMore: TextView = view.findViewById(R.id.readMore)
        val noteReadMore: TextView = view.findViewById(R.id.noteReadMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_layout, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val entry = items[position]

        holder.taskContainer.visibility = View.VISIBLE
        holder.textTask.visibility = View.GONE

        holder.taskText.text = entry.textTask
        val done = entry.taskStat == "DONE" || entry.taskStat == "true"
        holder.taskCheckbox.setImageResource(
            if (done) R.drawable.ic_selectedbox else R.drawable.ic_blankbox
        )

        holder.dateTime.text = "${entry.firstEntryDate} ${entry.firstTimeStamp}"
        holder.logbookName.text = entry.logbookName

        if (!entry.note.isNullOrEmpty()) {
            holder.note.visibility = View.VISIBLE
            holder.note.text = "Note: ${entry.note}"
        } else {
            holder.note.visibility = View.GONE
            holder.noteReadMore.visibility = View.GONE
        }

        // you can reuse your expand/collapse logic here if you like
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<DiaryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

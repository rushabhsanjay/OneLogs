package com.oddworks.onelogs

import DiaryEntryRecyclerAdapter
import LogBookDatabaseHelper
import TimelineAdapter
import TimelineItem
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TimelineActivity : AppCompatActivity() {

    lateinit var tableName: String
    lateinit var dbHelper: LogBookDatabaseHelper
    private lateinit var adapter: TimelineAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_timeline)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.timeline_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.md_theme_dark_onPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        dbHelper = LogBookDatabaseHelper(this)
        tableName = "timeline97531"   // your timeline table name

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Bottom nav: Logbooks → MainActivity
        val navLogbooks = findViewById<LinearLayout>(R.id.navLogbooks)
        navLogbooks.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Bottom nav: All Tasks → GlobalTasksActivity
        val navAllTasks = findViewById<LinearLayout>(R.id.navAllTasks)
        navAllTasks.setOnClickListener {
            val intent = Intent(this, GlobalTasksActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        recyclerView = findViewById(R.id.timelineRecyclerView)

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true          // fill from bottom
            reverseLayout = false        // data is oldest -> newest
        }
        recyclerView.layoutManager = layoutManager

        adapter = TimelineAdapter(mutableListOf())
        recyclerView.adapter = adapter

        var isLoadingMore = false
        var offset = 0
        val pageSize = 200
        var allLoaded = false

        fun loadNextPage(scrollToBottomAfter: Boolean) {
            if (isLoadingMore || allLoaded) return
            isLoadingMore = true
            adapter.startLoading()

            loadMoreEntries(offset, pageSize) { newItems ->
                if (newItems.isEmpty()) {
                    allLoaded = true
                    isLoadingMore = false
                    return@loadMoreEntries
                }

                offset += pageSize
                adapter.addMoreItems(newItems)
                isLoadingMore = false

                if (scrollToBottomAfter) {
                    recyclerView.post {
                        recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        }

        // Menu button: only Rebuild + 2 export options
        val moreMenuButton = findViewById<ImageButton>(R.id.moreMenuButton)
        moreMenuButton.setOnClickListener {
            val options = arrayOf(
                "Rebuild Timeline",
                "Export Custom Date Range",
                "Export Today"
            )
            AlertDialog.Builder(this)
                .setTitle("Timeline Options")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // Rebuild timeline table (like MainActivity does for its tables)
                            dbHelper.recreateTimelineTable()
                            Toast.makeText(this, "Timeline rebuilt", Toast.LENGTH_SHORT).show()

                            // Easiest safe way: restart TimelineActivity, like going back to Main then coming again
                            finish()
                            startActivity(Intent(this, TimelineActivity::class.java))
                        }
                        1 -> {
                            showDatePicker(this, "Start Date") { startDate ->
                                showDatePicker(this, "End Date") { endDate ->
                                    showTimelineExportFormatDialog(startDate, endDate)
                                }
                            }
                        }
                        2 -> {
                            val todayDate = android.text.format.DateFormat
                                .format("yyyy-MM-dd", System.currentTimeMillis())
                                .toString()
                            showTimelineExportFormatDialog(todayDate, todayDate)
                        }
                    }
                }
                .show()
        }


        // Initial load
        loadNextPage(scrollToBottomAfter = true)
    }

    // ==== EXPORT DIALOG (like MainActivity, but for timeline) ====

    private fun showTimelineExportFormatDialog(startDate: String, endDate: String) {
        val formatOptions = arrayOf("CSV", "TXT")
        AlertDialog.Builder(this)
            .setTitle("Choose Export Format")
            .setItems(formatOptions) { _, formatWhich ->
                try {
                    val entries = dbHelper.getTimelineEntriesForDateRange(startDate, endDate)

                    when (formatWhich) {
                        0 -> exportTimelineEntriesToCSV(entries)
                        1 -> exportTimelineEntriesToTXT(entries)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TimelineExport", "Export failed", e)
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }



    private fun exportTimelineEntriesToTXT(entries: List<LogBookDatabaseHelper.TimelineEntry>) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries to export!", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "Timeline_${System.currentTimeMillis()}.txt"
        val file = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            fileName
        )

        java.io.FileWriter(file).use { writer ->
            for (entry in entries) {
                writer.append("EntryUniqueId: ${entry.entryUniqueId}\n")
                writer.append("Logbook: ${entry.logbookName}\n")
                writer.append("Date: ${entry.firstEntryDate}  Time: ${entry.firstTimeStamp}\n")
                writer.append("Type: ${entry.entryType}\n")
                writer.append("TaskStat: ${entry.taskStat ?: ""}\n")
                writer.append("FilePath: ${entry.filepath ?: ""}\n")
                writer.append("Text: ${entry.textTask ?: ""}\n")
                writer.append("Note: ${entry.note ?: ""}\n")
                writer.append("-----\n")
            }
        }

        Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun exportTimelineEntriesToCSV(entries: List<LogBookDatabaseHelper.TimelineEntry>) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "Timeline_${System.currentTimeMillis()}.csv"
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val file = java.io.File(downloadsDir, fileName)

        java.io.FileWriter(file).use { writer ->
            writer.append("EntryUniqueId,Logbook,Date,Time,Type,TaskStat,FilePath,Text,Note\n")
            for (entry in entries) {
                writer.append(
                    "${entry.entryUniqueId}," +
                            "${entry.logbookName}," +
                            "${entry.firstEntryDate}," +
                            "${entry.firstTimeStamp}," +
                            "${entry.entryType}," +
                            "${entry.taskStat ?: ""}," +
                            "${entry.filepath ?: ""}," +
                            "${entry.textTask ?: ""}," +
                            "${entry.note ?: ""}\n"
                )
            }
        }

        Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }


    // ==== PAGING (unchanged) ====

    private fun loadMoreEntries(
        offset: Int,
        limit: Int,
        onLoaded: (List<TimelineItem>) -> Unit
    ) {
        Thread {
            val allEntries = dbHelper.getTimelineEntriesWithLimit(tableName, limit, offset)

            val timelineItems = mutableListOf<TimelineItem>()
            var lastDate = ""

            for (entry in allEntries) {
                if (entry.firstEntryDate != lastDate) {
                    timelineItems.add(TimelineItem.DateSeparator(entry.firstEntryDate))
                    lastDate = entry.firstEntryDate
                }
                timelineItems.add(TimelineItem.EntryItem(entry))
            }

            runOnUiThread {
                onLoaded(timelineItems)
            }
        }.start()
    }

    // ==== DATE PICKER (same pattern as MainActivity) ====

    private fun showDatePicker(
        activity: AppCompatActivity,
        title: String,
        onDateSelected: (String) -> Unit
    ) {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        val datePickerDialog = android.app.DatePickerDialog(
            activity,
            { _, selectedYear, selectedMonth, selectedDay ->
                val monthStr = String.format("%02d", selectedMonth + 1)
                val dayStr = String.format("%02d", selectedDay)
                val dateStr = "$selectedYear-$monthStr-$dayStr"   // yyyy-MM-dd
                onDateSelected(dateStr)
            },
            year,
            month,
            day
        )

        datePickerDialog.setTitle(title)
        datePickerDialog.show()
    }
}

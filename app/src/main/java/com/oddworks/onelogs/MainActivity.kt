package com.oddworks.onelogs

import DiaryEntry
import LogBookDatabaseHelper
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {

    private lateinit var logBookTitlesList: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var dbHelper: LogBookDatabaseHelper

    // Function to make a safe SQLite table name (letters, numbers, underscores)
    private fun getSanitizedTableName(name: String): String {
        return name.replace("[^A-Za-z0-9_]".toRegex(), "_").lowercase()
    }
    private fun showDatePicker(context: android.content.Context, title: String, onDateSelected: (String) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        val datePicker = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val date = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                onDateSelected(date)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        datePicker.setTitle(title)
        datePicker.show()
    }


    private fun exportEntriesToCSV(entries: List<DiaryEntry>, diaryName: String) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries to export!", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "${diaryName}_${System.currentTimeMillis()}.csv"
        // This makes the file exported to the actual Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        FileWriter(file).use { writer ->
            writer.append("EntryUniqueId,LinkedId,Date,Time,Type,TaskStat,FilePath,Text,Note,DeleteStat\n")
            for (entry in entries) {
                writer.append("${entry.entryUniqueId},${entry.linkedId ?: ""},${entry.firstEntryDate},${entry.firstTimeStamp},${entry.entryType},${entry.taskStat ?: ""},${entry.filepath ?: ""},${entry.textTask},${entry.note ?: ""},${entry.deleteStat}\n")
            }


        }
        Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }
        // Optionally: share/send the CSV (uncomment if you want)
        // val intent = Intent(Intent.ACTION_SEND)
        // intent.type = "text/csv"
        // intent.putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file))
        // startActivity(Intent.createChooser(intent, "Share CSV"))

    private fun exportEntriesToTXT(entries: List<DiaryEntry>, diaryName: String) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries to export!", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "${diaryName}_${System.currentTimeMillis()}.txt"
        val file = java.io.File(android.os.Environment
            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
        java.io.FileWriter(file).use { writer ->
            for (entry in entries) {
                writer.append("EntryUniqueId: ${entry.entryUniqueId}\n")
                writer.append("LinkedId: ${entry.linkedId ?: ""}\n")
                writer.append("Date: ${entry.firstEntryDate}  Time: ${entry.firstTimeStamp}\n")
                writer.append("Type: ${entry.entryType}\n")
                writer.append("TaskStat: ${entry.taskStat ?: ""}\n")
                writer.append("FilePath: ${entry.filepath ?: ""}\n")
                writer.append("Text: ${entry.textTask}\n")
                writer.append("Note: ${entry.note ?: ""}\n")
                writer.append("DeleteStat: ${entry.deleteStat}\n")
                writer.append("-----\n")

            }
        }
        Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = LogBookDatabaseHelper(this)

        // Load diaries from database tables
        logBookTitlesList = dbHelper.getAllDiaryTableNames().toMutableList()
        // If none found, create and show the defaults
        if (logBookTitlesList.isEmpty()) {
            logBookTitlesList = mutableListOf("WorkDiary", "PersonalDiary")
            val db = dbHelper.writableDatabase
            logBookTitlesList.forEach { diaryName ->
                dbHelper.createLogBookTableIfNotExists(db, diaryName)
            }
            db.close()
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logBookTitlesList)
        val listView = findViewById<ListView>(R.id.logBooksListView)
        listView.adapter = adapter

        // Handle short click: open logbook details
        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, LogBookDetailActivity::class.java)
            intent.putExtra("LOG_BOOK_NAME", logBookTitlesList[position])
            startActivity(intent)
        }

        // Long click: open/export/delete dialog
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val options = arrayOf("Open", "Export", "Delete")
            AlertDialog.Builder(this)
                .setTitle("Select action")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val intent = Intent(this, LogBookDetailActivity::class.java)
                            intent.putExtra("LOG_BOOK_NAME", logBookTitlesList[position])
                            startActivity(intent)
                        }
                        1 -> {
                            val exportOptions = arrayOf("All Time", "Custom Date Range")
                            AlertDialog.Builder(this)
                                .setTitle("Export Diary")
                                .setItems(exportOptions) { _, exportWhich ->
                                    if (exportWhich == 0) {
                                        // Export all entries as CSV or TXT
                                        val diaryName = logBookTitlesList[position]
                                        val tableName = getSanitizedTableName(diaryName)
                                        val entries = dbHelper.getLastNEntries(tableName, Int.MAX_VALUE) // Get all

                                        // -- ADD FORMAT CHOICE DIALOG HERE --
                                        val formatOptions = arrayOf("CSV", "TXT")
                                        AlertDialog.Builder(this)
                                            .setTitle("Choose Export Format")
                                            .setItems(formatOptions) { _, formatWhich ->
                                                if (formatWhich == 0) {
                                                    exportEntriesToCSV(entries, diaryName)
                                                } else {
                                                    exportEntriesToTXT(entries, diaryName)
                                                }
                                            }
                                            .show()
                                        // --

                                    } else {
                                        showDatePicker(this, "Start Date") { startDate ->
                                            showDatePicker(this, "End Date") { endDate ->
                                                val diaryName = logBookTitlesList[position]
                                                val tableName = getSanitizedTableName(diaryName)
                                                val entries = dbHelper.getEntriesInDateRange(tableName, startDate, endDate)

                                                // -- ADD FORMAT CHOICE DIALOG HERE --
                                                val formatOptions = arrayOf("CSV", "TXT")
                                                AlertDialog.Builder(this)
                                                    .setTitle("Choose Export Format")
                                                    .setItems(formatOptions) { _, formatWhich ->
                                                        if (formatWhich == 0) {
                                                            exportEntriesToCSV(entries, diaryName)
                                                        } else {
                                                            exportEntriesToTXT(entries, diaryName)
                                                        }
                                                    }
                                                    .show()
                                                // --
                                            }
                                        }
                                    }
                                }
                                .show()
                        }


                        2 -> {
                            AlertDialog.Builder(this)
                                .setTitle("Delete diary")
                                .setMessage("Are you sure you want to delete this book?")
                                .setPositiveButton("Yes") { _, _ ->
                                    val diaryName = logBookTitlesList[position]
                                    val tableName = getSanitizedTableName(diaryName)
                                    logBookTitlesList.removeAt(position)
                                    adapter.notifyDataSetChanged()
                                    // Remove diary table from DB
                                    val db = dbHelper.writableDatabase
                                    db.execSQL("DROP TABLE IF EXISTS $tableName")
                                    db.close()
                                    Toast.makeText(this, "Diary deleted.", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("No", null)
                                .show()
                        }
                    }
                }
                .show()

            true // signals long click handled
        }

        // Button for adding new diaries
        val addButton = findViewById<ImageButton>(R.id.addbutton)
        addButton.setOnClickListener {
            val editText = EditText(this)
            editText.hint = "Enter diary name"
            AlertDialog.Builder(this)
                .setTitle("Add New Diary")
                .setView(editText)
                .setPositiveButton("Add") { _, _ ->
                    val newDiaryName = editText.text.toString().trim()
                    if (newDiaryName.isNotEmpty()) {
                        val db = dbHelper.writableDatabase
                        val tableName = getSanitizedTableName(newDiaryName)
                        dbHelper.createLogBookTableIfNotExists(db, tableName)
                        db.close()
                        logBookTitlesList.add(newDiaryName)
                        adapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this, "Diary name cannot be empty!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

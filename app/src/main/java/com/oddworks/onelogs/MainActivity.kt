package com.oddworks.onelogs


import DiaryEntry
import LogBookDatabaseHelper
import LogBooksAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts   // ⬅️ NEW
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileWriter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// ⬇️ NEW IMPORTS for zip work
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var logBookTitlesList: MutableList<String>
    private lateinit var adapter: LogBooksAdapter
    private lateinit var logBooksRecyclerView: RecyclerView
    private lateinit var dbHelper: LogBookDatabaseHelper

    // ⬇️ NEW: internal media root folder name (inside filesDir/)
    private val mediaRootName = "onelogs_images"


    // ⬇️ NEW: SAF launchers
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            exportToZip(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importFromZip(uri)
        }
    }

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

    private fun exportEntriesToTXT(entries: List<DiaryEntry>, diaryName: String) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries to export!", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "${diaryName}_${System.currentTimeMillis()}.txt"
        val file = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ), fileName
        )
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
    private fun runImagePathMigrationIfNeeded() {
        val prefs = getSharedPreferences("migrations", MODE_PRIVATE)
        if (prefs.getBoolean("image_path_migration_done", false)) return

        // Build the old prefix dynamically for THIS install
        val oldPrefix = filesDir.absolutePath + "/onelogs_images/"

        val db = dbHelper.writableDatabase
        val tables = mutableListOf<String>()

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'android_metadata' " +
                    "AND name NOT LIKE 'sqlite_sequence'",
            null
        )
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()

        db.beginTransaction()
        try {
            for (table in tables) {
                val sql = """
                UPDATE $table
                SET Filepath = substr(Filepath, ${oldPrefix.length + 1})
                WHERE Filepath LIKE ?;
            """.trimIndent()
                db.execSQL(sql, arrayOf("$oldPrefix%"))
            }

            db.setTransactionSuccessful()
            prefs.edit().putBoolean("image_path_migration_done", true).apply()
        } finally {
            db.endTransaction()
        }
    }

    private fun showLogBookOptionsDialog(position: Int) {
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
                                    val diaryName = logBookTitlesList[position]
                                    val tableName = getSanitizedTableName(diaryName)
                                    val entries = dbHelper.getLastNEntries(tableName, Int.MAX_VALUE)
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
                                } else {
                                    showDatePicker(this, "Start Date") { startDate ->
                                        showDatePicker(this, "End Date") { endDate ->
                                            val diaryName = logBookTitlesList[position]
                                            val tableName = getSanitizedTableName(diaryName)
                                            val entries = dbHelper.getEntriesInDateRange(tableName, startDate, endDate)
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
                                adapter.notifyItemRemoved(position)
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
    }

    private fun rebuildTimeline() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Rebuilding Timeline")
            .setMessage("Timeline is being rebuilt. Please do not press back or close.")
            .setCancelable(false)
            .show()

        Thread {
            dbHelper.recreateTimelineTable()
            runOnUiThread {
                progressDialog.dismiss()
                Toast.makeText(this, "Timeline rebuilt successfully", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun rebuildPendingTasksTable() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Rebuilding Pending Tasks")
            .setMessage("All pending tasks table is being rebuilt. Please wait…")
            .setCancelable(false)
            .show()

        Thread {
            dbHelper.recreateAllPendingTasksTable()
            runOnUiThread {
                progressDialog.dismiss()
                Toast.makeText(this, "Pending tasks rebuilt", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun rebuildCompletedTasksTable() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Rebuilding Completed Tasks")
            .setMessage("All completed tasks table is being rebuilt. Please wait…")
            .setCancelable(false)
            .show()

        Thread {
            dbHelper.recreateAllCompletedTasksTable()
            runOnUiThread {
                progressDialog.dismiss()
                Toast.makeText(this, "Completed tasks rebuilt", Toast.LENGTH_SHORT).show()
            }
        }.start()
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


        runImagePathMigrationIfNeeded()

        logBookTitlesList = dbHelper.getAllDiaryTableNames().toMutableList()
        if (logBookTitlesList.isEmpty()) {
            logBookTitlesList = mutableListOf("WorkDiary", "PersonalDiary")
            val db = dbHelper.writableDatabase
            logBookTitlesList.forEach { diaryName ->
                dbHelper.createLogBookTableIfNotExists(db, diaryName)
            }
            db.close()
        }

        logBooksRecyclerView = findViewById(R.id.logBooksRecyclerView)
        logBooksRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LogBooksAdapter(
            logBookTitlesList,
            onClick = { position ->
                val intent = Intent(this, LogBookDetailActivity::class.java)
                intent.putExtra("LOG_BOOK_NAME", logBookTitlesList[position])
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            },
            onLongClick = { position ->
                showLogBookOptionsDialog(position)
            }
        )
        logBooksRecyclerView.adapter = adapter

        val navTimeline = findViewById<LinearLayout>(R.id.navTimeline)
        navTimeline.setOnClickListener {
            val intent = Intent(this, TimelineActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val moreMenuButton = findViewById<ImageButton>(R.id.moreMenuButton)
        moreMenuButton.setOnClickListener {
            // ⬇️ UPDATED OPTIONS ARRAY: added Export backup, Import backup
            val options = arrayOf("About", "Rebuild Summaries", "Export backup", "Import backup", "Privacy Policy")
            AlertDialog.Builder(this)
                .setTitle("Options")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            startActivity(Intent(this, GifTransitionActivity::class.java))
                        }
                        1 -> {
                            rebuildTimeline()
                            rebuildPendingTasksTable()
                            rebuildCompletedTasksTable()
                        }
                        2 -> {
                            // Export backup
                            val fileName = "OneLogs_Backup_${System.currentTimeMillis()}.zip"
                            exportBackupLauncher.launch(fileName)
                        }
                        3 -> {
                            // Import backup
                            importBackupLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream")
                            )
                        }
                        4 -> {
                        startActivity(Intent(this, PrivacyPolicyActivity::class.java))
                    }
                    }
                }
                .show()
        }

        val navAllTasks = findViewById<LinearLayout>(R.id.navAllTasks)
        navAllTasks.setOnClickListener {
            val intent = Intent(this, GlobalTasksActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

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
                        adapter.notifyItemInserted(logBookTitlesList.size - 1)
                    } else {
                        Toast.makeText(this, "Diary name cannot be empty!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ⬇️ EXPORT: DB + images → zip

    private fun exportToZip(backupUri: Uri) {
        val context = this
        val dbFile = context.getDatabasePath("LogBooks.db")   // [file:21][web:22]
        val mediaRoot = File(context.filesDir, mediaRootName) // [web:10]

        try {
            contentResolver.openOutputStream(backupUri)?.use { outStream ->
                ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->

                    if (dbFile.exists()) {
                        addFileToZip(zos, dbFile, "db/LogBooks.db")
                    }

                    if (mediaRoot.exists()) {
                        addFolderToZip(zos, mediaRoot, mediaRoot, "media")
                    }

                    val metaJson = """
                        {
                          "appVersion": 1,
                          "createdAt": ${System.currentTimeMillis()}
                        }
                    """.trimIndent()
                    zos.putNextEntry(ZipEntry("meta.json"))
                    zos.write(metaJson.toByteArray())
                    zos.closeEntry()
                }
            }
            Toast.makeText(this, "Backup exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, zipPath: String) {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val entry = ZipEntry(zipPath)
                zos.putNextEntry(entry)
                val buffer = ByteArray(4096)
                var count: Int
                while (bis.read(buffer).also { count = it } != -1) {
                    zos.write(buffer, 0, count)
                }
                zos.closeEntry()
            }
        }
    }

    private fun addFolderToZip(
        zos: ZipOutputStream,
        root: File,
        current: File,
        baseFolderInZip: String
    ) {
        current.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addFolderToZip(zos, root, file, baseFolderInZip)
            } else {
                val relative = root.toURI().relativize(file.toURI()).path
                val zipPath = "$baseFolderInZip/$relative"
                addFileToZip(zos, file, zipPath)
            }
        }
    }

    // ⬇️ IMPORT: zip → DB + images

    private fun importFromZip(backupUri: Uri) {
        val context = this
        val dbFile = context.getDatabasePath("LogBooks.db")   // [file:21][web:22]
        val mediaRoot = File(context.filesDir, mediaRootName) // [web:10]

        try {
            contentResolver.openInputStream(backupUri)?.use { inStream ->
                ZipInputStream(BufferedInputStream(inStream)).use { zis ->
                    val buffer = ByteArray(4096)
                    val tempDbFile = File(dbFile.parentFile, "LogBooks_temp.db")
                    if (tempDbFile.exists()) tempDbFile.delete()

                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name

                        when {
                            name == "db/LogBooks.db" -> {
                                FileOutputStream(tempDbFile).use { fos ->
                                    var count: Int
                                    while (zis.read(buffer).also { count = it } != -1) {
                                        fos.write(buffer, 0, count)
                                    }
                                }
                            }

                            name.startsWith("media/") && !entry.isDirectory -> {
                                val relativePath = name.removePrefix("media/")
                                val outFile = File(mediaRoot, relativePath)
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    var count: Int
                                    while (zis.read(buffer).also { count = it } != -1) {
                                        fos.write(buffer, 0, count)
                                    }
                                }
                            }
                        }

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }

                    if (tempDbFile.exists() && tempDbFile.length() > 0) {
                        if (dbFile.exists()) dbFile.delete()
                        tempDbFile.renameTo(dbFile)
                    } else {
                        tempDbFile.delete()
                        Toast.makeText(this, "Invalid backup file", Toast.LENGTH_LONG).show()
                        return
                    }
                }
            }

            // validate DB
            val helper = LogBookDatabaseHelper(context)
            helper.readableDatabase.close()

            Toast.makeText(this, "Backup imported. Restart app.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor


class LogBookDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "LogBooks.db"
        private const val DATABASE_VERSION = 1
    }


    override fun onCreate(db: SQLiteDatabase) {
        // We will handle table creation dynamically (see method below)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Not needed yet
    }

    // ⬇️ Move this function here, OUTSIDE onUpgrade & onCreate:
    fun createLogBookTableIfNotExists(db: SQLiteDatabase, tableName: String) {
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS $tableName (
                Entry_Unique_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                FirstEntryDate TEXT,
                FirstTimeStamp TEXT,
                Linked_ID INTEGER,
                EntryType TEXT,
                TaskStat TEXT,
                Filepath TEXT,
                TextTask TEXT,
                Note TEXT,
                DeleteStat INTEGER
            );
        """.trimIndent()

        db.execSQL(createTableSQL)
    }
    fun recreateTimelineTable() {
        val db = writableDatabase
        val timelineTable = "timeline97531"

        db.execSQL("DROP TABLE IF EXISTS $timelineTable")

        db.execSQL("""
        CREATE TABLE $timelineTable (
            Entry_Unique_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            FirstEntryDate TEXT,
            FirstTimeStamp TEXT,
            Linked_ID INTEGER,
            EntryType TEXT,
            TaskStat TEXT,
            Filepath TEXT,
            TextTask TEXT,
            Note TEXT,
            DeleteStat INTEGER,
            LogbookName TEXT
        );
    """.trimIndent())

        val tables = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'android_metadata' " +
                    "AND name NOT LIKE 'sqlite_sequence' " +
                    "AND name NOT LIKE 'timeline97531' " +
                    "AND name NOT LIKE 'allpendingtasks97531' " +
                    "AND name NOT LIKE 'allcompletedtasks97531'",
            null
        )
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            if (name == timelineTable) continue
            tables.add(name)
        }
        cursor.close()

        // Build UNION query to fetch and sort all tables at once
        val unionQueries = tables.mapIndexed { idx, src ->
            "SELECT FirstEntryDate, FirstTimeStamp, Linked_ID, EntryType, TaskStat, Filepath, TextTask, Note, DeleteStat, '$src' as LogbookName " +
                    "FROM $src WHERE DeleteStat = 0" +
                    (if (idx < tables.size - 1) " UNION ALL " else "")
        }.joinToString("")

        db.execSQL("""
        INSERT INTO $timelineTable
        (FirstEntryDate, FirstTimeStamp, Linked_ID, EntryType, TaskStat, Filepath, TextTask, Note, DeleteStat, LogbookName)
        $unionQueries
        ORDER BY FirstEntryDate ASC, FirstTimeStamp ASC
    """.trimIndent())

        db.close()
    }


    fun getTimelineEntriesWithLimit(tableName: String, limit: Int, offset: Int): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
        SELECT * FROM $tableName 
        WHERE DeleteStat = 0
        ORDER BY FirstEntryDate ASC, FirstTimeStamp ASC
        LIMIT $limit OFFSET $offset
        """.trimIndent(),
            null
        )

        while (cursor.moveToNext()) {
            val entry = DiaryEntry(
                entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("Entry_Unique_ID")),
                firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
                firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
                linkedId = if (cursor.isNull(cursor.getColumnIndexOrThrow("Linked_ID"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("Linked_ID")),
                entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
                taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
                filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
                textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("Note")),
                deleteStat = cursor.getInt(cursor.getColumnIndexOrThrow("DeleteStat")) != 0,
                logbookName = cursor.getString(cursor.getColumnIndexOrThrow("LogbookName")) ?: ""
            )
            entries.add(entry)
        }
        cursor.close()
        db.close()
        return entries
    }

    fun recreateAllPendingTasksTable() {
        val db = writableDatabase
        val tableName = "allpendingtasks97531"

        db.execSQL("DROP TABLE IF EXISTS $tableName")

        db.execSQL("""
        CREATE TABLE $tableName (
            Entry_Unique_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            FirstEntryDate TEXT,
            FirstTimeStamp TEXT,
            Linked_ID INTEGER,
            EntryType TEXT,
            TaskStat TEXT,
            Filepath TEXT,
            TextTask TEXT,
            Note TEXT,
            DeleteStat INTEGER,
            LogbookName TEXT
        );
    """.trimIndent())

        // get all user tables except meta / helper ones
        val tables = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'android_metadata' " +
                    "AND name NOT LIKE 'sqlite_sequence' " +
                    "AND name NOT LIKE 'timeline97531' " +
                    "AND name NOT LIKE 'allpendingtasks97531' " +
                    "AND name NOT LIKE 'allcompletedtasks97531'",
            null
        )
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()

        if (tables.isNotEmpty()) {
            val unionQueries = tables.mapIndexed { idx, src ->
                "SELECT FirstEntryDate, FirstTimeStamp, Linked_ID, EntryType, TaskStat, Filepath, TextTask, Note, DeleteStat, '$src' as LogbookName " +
                        "FROM $src " +
                        "WHERE DeleteStat = 0 AND EntryType = 'TASK' AND (TaskStat IS NULL OR TaskStat = 'TODO' OR TaskStat = 'false')" +
                        (if (idx < tables.size - 1) " UNION ALL " else "")
            }.joinToString("")

            db.execSQL("""
            INSERT INTO $tableName
            (FirstEntryDate, FirstTimeStamp, Linked_ID, EntryType, TaskStat, Filepath, TextTask, Note, DeleteStat, LogbookName)
            $unionQueries
            ORDER BY FirstEntryDate ASC, FirstTimeStamp ASC
        """.trimIndent())
        }

        db.close()
    }

    fun recreateAllCompletedTasksTable() {
        val db = writableDatabase
        val tableName = "allcompletedtasks97531"

        db.execSQL("DROP TABLE IF EXISTS $tableName")

        db.execSQL("""
        CREATE TABLE $tableName (
            Entry_Unique_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            FirstEntryDate TEXT,
            FirstTimeStamp TEXT,
            Linked_ID INTEGER,
            EntryType TEXT,
            TaskStat TEXT,
            Filepath TEXT,
            TextTask TEXT,
            Note TEXT,
            DeleteStat INTEGER,
            LogbookName TEXT
        );
    """.trimIndent())

        val tables = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'android_metadata' " +
                    "AND name NOT LIKE 'sqlite_sequence' " +
                    "AND name NOT LIKE 'timeline97531' " +
                    "AND name NOT LIKE 'allpendingtasks97531' " +
                    "AND name NOT LIKE 'allcompletedtasks97531'",
            null
        )
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0))
        }
        cursor.close()

        if (tables.isNotEmpty()) {
            val unionQueries = tables.mapIndexed { idx, src ->
                "SELECT FirstEntryDate, FirstTimeStamp, Linked_ID, EntryType, TaskStat, Filepath, TextTask, Note, DeleteStat, '$src' as LogbookName " +
                        "FROM $src " +
                        "WHERE DeleteStat = 0 AND EntryType = 'TASK' AND (TaskStat = 'DONE' OR TaskStat = 'true')" +
                        (if (idx < tables.size - 1) " UNION ALL " else "")
            }.joinToString("")

            db.execSQL("""
            INSERT INTO $tableName
            (FirstEntryDate, FirstTimeStamp, Linked_ID, EntryType, TaskStat, Filepath, TextTask, Note, DeleteStat, LogbookName)
            $unionQueries
            ORDER BY FirstEntryDate ASC, FirstTimeStamp ASC
        """.trimIndent())
        }

        db.close()
    }




    fun insertDiaryEntry(
        tableName: String,
        textTask: String,
        date: String,
        time: String,
        linkedId: Long? = null // new parameter!
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("FirstEntryDate", date)
            put("FirstTimeStamp", time)
            put("Linked_ID", linkedId)
            put("EntryType", "TEXT")
            put("TaskStat", null as String?)
            put("Filepath", null as String?)
            put("TextTask", textTask)
            put("Note", null as String?)
            put("DeleteStat", 0)
        }
        db.insert(tableName, null, values)
        db.close()
    }

    fun getAllEntries(tableName: String): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        val db = readableDatabase

        val cursor = db.query(
            tableName,
            null, // all columns
            "DeleteStat = 0", // skip deleted entries
            null, null, null,
            "Entry_Unique_ID ASC"
        )

        while (cursor.moveToNext()) {
            val entry = DiaryEntry(
                entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("Entry_Unique_ID")),
                firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
                firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
                linkedId = if (cursor.isNull(cursor.getColumnIndexOrThrow("Linked_ID"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("Linked_ID")),
                entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
                taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
                filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
                textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("Note")),
                deleteStat = cursor.getInt(cursor.getColumnIndexOrThrow("DeleteStat")) != 0,
                logbookName = cursor.getString(cursor.getColumnIndexOrThrow("LogbookName")) ?: ""  // add this
            )
            entries.add(entry)
        }
        cursor.close()
        db.close()
        return entries
    }

    fun getAllDiaryTableNamed(): List<String> {
        val tableNames = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'sqlite_sequence'",
            null
        )
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()
        db.close()
        return tableNames
    }
    fun getAllDiaryTableNames(): List<String> {
        val tableNames = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'sqlite_sequence'",
            null
        )
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            // Skip internal tables
            if (name == "android_metadata") continue
            if (name == "sqlite_sequence") continue
            if (name == "timeline97531") continue
            if (name == "allpendingtasks97531") continue
            if (name == "allcompletedtasks97531") continue

            // later you can add more: if (name.startsWith("_sys_")) continue
            tableNames.add(name)
        }
        cursor.close()
        db.close()
        return tableNames
    }


    fun updateDiaryEntryText(tableName: String, entryUniqueId: Long, newText: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("TextTask", newText) // FIXED COLUMN NAME
        }
        db.update(tableName, values, "Entry_Unique_ID=?", arrayOf(entryUniqueId.toString()))
        db.close()
    }

    fun markDiaryEntryDeleted(tableName: String, entryUniqueId: Long){
        val db = writableDatabase
        val values = ContentValues().apply {
            put("DeleteStat", 1) // true means deleted
        }
        db.update(tableName, values, "Entry_Unique_ID=?", arrayOf(entryUniqueId.toString()))
        db.close()
    }
    fun updateDiaryEntryNote(tableName: String, entryUniqueId: Long, newNote: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("Note", newNote)
        }
        db.update(tableName, values, "Entry_Unique_ID=?", arrayOf(entryUniqueId.toString()))
        db.close()
    }
    fun convertDiaryEntryToTask(tableName: String, entryUniqueId: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("EntryType", "TASK")
            put("TaskStat", "TODO") // Or any initial status you want
        }
        db.update(tableName, values, "Entry_Unique_ID=?", arrayOf(entryUniqueId.toString()))
        db.close()
    }
    // Returns last N entries (newest entries) in ASC order
    fun getLastNEntries(tableName: String, limit: Int): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
        SELECT * FROM (
           SELECT * FROM $tableName WHERE DeleteStat = 0 
           ORDER BY Entry_Unique_ID DESC
           LIMIT $limit
        ) ORDER BY Entry_Unique_ID ASC
        """, null
        )
        while (cursor.moveToNext()) {
            val entry = DiaryEntry(
                entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("Entry_Unique_ID")),
                firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
                firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
                linkedId = if (cursor.isNull(cursor.getColumnIndexOrThrow("Linked_ID"))) null else cursor.getLong(cursor.getColumnIndexOrThrow("Linked_ID")),
                entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
                taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
                filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
                textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("Note")),
                deleteStat = cursor.getInt(cursor.getColumnIndexOrThrow("DeleteStat")) != 0
            )
            entries.add(entry)
        }
        cursor.close()
        db.close()
        return entries
    }
    fun insertImageEntry(
        tableName: String,
        filepath: String,
        date: String,
        time: String
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("FirstEntryDate", date)
            put("FirstTimeStamp", time)
            put("Linked_ID", null as Long?)
            put("EntryType", "IMAGE")
            put("TaskStat", null as String?)
            put("Filepath", filepath)
            put("TextTask", "") // No text for image
            put("Note", null as String?)
            put("DeleteStat", 0)
        }
        db.insert(tableName, null, values)
        db.close()
    }
    fun getEntriesInDateRange(tableName: String, startDate: String, endDate: String): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        val db = readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM $tableName WHERE DeleteStat = 0 AND FirstEntryDate BETWEEN ? AND ? ORDER BY FirstEntryDate ASC, FirstTimeStamp ASC",
            arrayOf(startDate, endDate)
        )

        while (cursor.moveToNext()) {
            val entry = DiaryEntry(
                entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("Entry_Unique_ID")),
                firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
                firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
                linkedId = if (cursor.isNull(cursor.getColumnIndexOrThrow("Linked_ID"))) null
                else cursor.getLong(cursor.getColumnIndexOrThrow("Linked_ID")),
                entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
                taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
                filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
                textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("Note")),
                deleteStat = cursor.getInt(cursor.getColumnIndexOrThrow("DeleteStat")) != 0
            )
            entries.add(entry)
        }

        cursor.close()
        db.close()
        return entries
    }

    fun getTimelineEntriesForDateRange(startDate: String, endDate: String): List<TimelineEntry> {
        val entries = mutableListOf<TimelineEntry>()
        val db = readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM timeline97531 WHERE FirstEntryDate BETWEEN ? AND ? ORDER BY FirstEntryDate ASC, FirstTimeStamp ASC",
            arrayOf(startDate, endDate)
        )

        while (cursor.moveToNext()) {
            entries.add(
                TimelineEntry(
                    entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("Entry_Unique_ID")),
                    logbookName = cursor.getString(cursor.getColumnIndexOrThrow("LogbookName")),
                    firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
                    firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
                    entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
                    taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
                    filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
                    textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("Note"))
                )
            )
        }

        cursor.close()
        db.close()
        return entries
    }


    data class TimelineEntry(
        val entryUniqueId: Long,
        val logbookName: String,
        val firstEntryDate: String,
        val firstTimeStamp: String,
        val entryType: String,
        val filepath: String?,
        val textTask: String?,
        val note: String?,
        val taskStat: String?
    )


    fun getAllEntriesWithLimit(tableName: String, limit: Int = 1000): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
        SELECT * FROM $tableName 
        WHERE DeleteStat = 0
        ORDER BY Entry_Unique_ID ASC
        LIMIT $limit
        """.trimIndent(),
            null
        )

        while (cursor.moveToNext()) {
            entries.add(
                DiaryEntry(
                    entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("Entry_Unique_ID")),
                    firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
                    firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
                    linkedId = if (cursor.isNull(cursor.getColumnIndexOrThrow("Linked_ID"))) null
                    else cursor.getLong(cursor.getColumnIndexOrThrow("Linked_ID")),
                    entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
                    taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
                    filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
                    textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("Note")),
                    deleteStat = cursor.getInt(cursor.getColumnIndexOrThrow("DeleteStat")) != 0
                )
            )
        }

        cursor.close()
        db.close()
        return entries
    }

    private fun getDiaryEntryFromCursor(cursor: Cursor): DiaryEntry {
        return DiaryEntry(
            entryUniqueId = cursor.getLong(cursor.getColumnIndexOrThrow("entryUniqueId")),
            firstEntryDate = cursor.getString(cursor.getColumnIndexOrThrow("FirstEntryDate")),
            firstTimeStamp = cursor.getString(cursor.getColumnIndexOrThrow("FirstTimeStamp")),
            linkedId = if (cursor.isNull(cursor.getColumnIndexOrThrow("LinkedID"))) null
            else cursor.getLong(cursor.getColumnIndexOrThrow("LinkedID")),
            entryType = cursor.getString(cursor.getColumnIndexOrThrow("EntryType")),
            taskStat = cursor.getString(cursor.getColumnIndexOrThrow("TaskStat")),
            filepath = cursor.getString(cursor.getColumnIndexOrThrow("Filepath")),
            textTask = cursor.getString(cursor.getColumnIndexOrThrow("TextTask")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("Note")),
            deleteStat = cursor.getInt(cursor.getColumnIndexOrThrow("DeleteStat")) != 0
        )
    }


}

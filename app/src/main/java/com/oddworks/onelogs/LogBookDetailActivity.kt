package com.oddworks.onelogs

import DiaryEntry
import DiaryEntryRecyclerAdapter
import LogBookDatabaseHelper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LogBookDetailActivity : AppCompatActivity() {

    // ========= MEMBERS =========
    lateinit var dbHelper: LogBookDatabaseHelper
    lateinit var tableName: String

    private var cameraImagePath: String? = null
    private val CAMERA_REQUEST_CODE = 1001
    private val GALLERY_REQUEST_CODE = 1002
    private var searching = false
    var isReplyMode = false
    var replyModeLinkedId: Long? = null

    // Toolbar/UI
    lateinit var toolbarTitle: TextView
    //lateinit var replyModeText: TextView
    lateinit var searchButton: ImageButton
    lateinit var cancelSearchButton: ImageButton
    lateinit var pendingButton: ImageButton
    lateinit var cancelPendingButton: ImageButton
    lateinit var chainViewModeTitle: TextView
    lateinit var chainCancelButton: ImageButton
    lateinit var replyBar: View
    lateinit var replyBarTitle: TextView
    lateinit var replyBarCancel: ImageButton
    lateinit var replyDivider: View
    var isPendingMode = false
    var isChainMode = false


    // Recycler
    private lateinit var recyclerAdapter: DiaryEntryRecyclerAdapter
    lateinit var entryRecyclerView: RecyclerView

    // ========= HELPERS =========
    private fun Int.dpToPx(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()


    // Load latest N entries into adapter
    fun refreshEntries(tableName: String) {
        val updatedEntries = dbHelper.getLastNEntries(tableName, 150)
        recyclerAdapter.update(updatedEntries)
    }


    // ========= CHAIN MODE =========
    fun findChainForEntry(swipedId: Int): Set<Int> {
        val allEntries = dbHelper.getAllEntriesWithLimit(tableName, 1000)
        val idToLinked = allEntries.associate {
            it.entryUniqueId.toInt() to it.linkedId?.toInt()
        }

        val chain = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()

        chain.add(swipedId)
        queue.add(swipedId)

        // Upward
        var cur = swipedId
        while (idToLinked[cur] != null) {
            val parent = idToLinked[cur]!!
            if (!chain.add(parent)) break
            cur = parent
        }

        // Downward
        fun addChildren(id: Int) {
            val kids = idToLinked.filter { it.value == id }.keys
            for (c in kids) if (chain.add(c)) addChildren(c)
        }
        addChildren(swipedId)

        return chain
    }








    // ========= REPLY MODE =========
    fun showReplyToolbar(entry: DiaryEntry) {
        replyBar.visibility = View.VISIBLE
        replyDivider.visibility = View.VISIBLE
        replyBarTitle.text = "Replying to: " + (entry.textTask ?: "").take(40)
        replyBarCancel.setOnClickListener {
            exitReplyMode()
        }
    }



    fun exitReplyMode() {
        isReplyMode = false
        replyModeLinkedId = null

        replyBar.visibility = View.GONE
        replyDivider.visibility = View.GONE
    }


    fun enterPendingMode() {
        isPendingMode = true
        pendingButton.visibility = View.GONE
        cancelPendingButton.visibility = View.VISIBLE

        val filtered = dbHelper.getLastNEntries(tableName, 150).filter { e ->
            e.entryType == "TASK" && (
                    e.taskStat == null ||
                            e.taskStat.lowercase() == "todo" ||
                            e.taskStat.lowercase() == "incomplete" ||
                            e.taskStat == "0" ||
                            e.taskStat.lowercase() == "false"
                    )
        }
        recyclerAdapter.update(filtered)
    }

    fun exitPendingMode() {
        isPendingMode = false
        cancelPendingButton.visibility = View.GONE
        pendingButton.visibility = View.VISIBLE
        refreshEntries(tableName)
    }
    fun setChainViewMode(enabled: Boolean) {
        isChainMode = enabled
        chainViewModeTitle.visibility = if (enabled) View.VISIBLE else View.GONE
        toolbarTitle.visibility = if (enabled) View.GONE else View.VISIBLE
        cancelSearchButton.visibility = if (enabled) View.VISIBLE else View.GONE

        searchButton.visibility = if (enabled) View.GONE else View.VISIBLE
        pendingButton.visibility = if (enabled) View.GONE else View.VISIBLE
    }
    private fun findConnectedChainIds(
        allEntries: List<DiaryEntry>,
        swipedEntryId: Int
    ): Set<Int> {
        // Build adjacency list: id -> neighbors
        val neighbors = mutableMapOf<Int, MutableSet<Int>>()

        fun connect(a: Int, b: Int) {
            neighbors.getOrPut(a) { mutableSetOf() }.add(b)
            neighbors.getOrPut(b) { mutableSetOf() }.add(a)
        }

        // Build edges from entryUniqueId <-> linkedId (both directions)
        for (entry in allEntries) {
            val id = entry.entryUniqueId.toInt()
            val linked = entry.linkedId?.toInt()
            if (linked != null && linked != 0 && linked != id) {
                connect(id, linked)
            }
        }

        // BFS/DFS from swipedEntryId to get whole connected component
        val visited = mutableSetOf<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(swipedEntryId)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!visited.add(current)) continue

            neighbors[current]?.forEach { neigh ->
                if (!visited.contains(neigh)) {
                    stack.add(neigh)
                }
            }
        }

        return visited
    }


    fun activateChainViewMode(swipedEntryId: Int) {
        setChainViewMode(true)

        // Load some window of entries (like you already do)
        val allEntries = dbHelper.getLastNEntries(tableName, 150)

        // Find all connected ids using graph traversal
        val chainIds = findConnectedChainIds(allEntries, swipedEntryId)

        val filtered = allEntries.filter { entry ->
            chainIds.contains(entry.entryUniqueId.toInt())
        }

        recyclerAdapter.update(filtered)

        val idx = filtered.indexOfFirst { it.entryUniqueId.toInt() == swipedEntryId }
        if (idx >= 0) entryRecyclerView.scrollToPosition(idx)
    }


    fun exitChainViewMode() {
        if (!isChainMode) return
        setChainViewMode(false)
        refreshEntries(tableName)
        entryRecyclerView.scrollToPosition(recyclerAdapter.itemCount - 1)
    }

    fun enterSearchMode() {
        searching = true
        toolbarTitle.visibility = View.GONE
        searchButton.visibility = View.GONE
        cancelSearchButton.visibility = View.VISIBLE
        // searchInputBar visibility & focus handled in onCreate where you have access to it
    }

    fun exitSearchMode(searchInputBar: EditText) {
        searching = false
        searchInputBar.visibility = View.GONE
        searchButton.visibility = View.VISIBLE
        cancelSearchButton.visibility = View.GONE
        toolbarTitle.visibility = View.VISIBLE
        searchInputBar.text.clear()
        refreshEntries(tableName)
    }


    // Add this function inside LogBookDetailActivity
    fun handleReplyLinkAction(entry: DiaryEntry) {
        // Example Implementation:
        // Set the linkedId of the entry and update the database/UI as needed

        // For demonstration: Assume you want to set replyModeLinkedId to this entry
        this.replyModeLinkedId = entry.entryUniqueId

        // If you have a method to link/reassign, call it here
        // e.g., dbHelper.linkDiaryEntry(tableName, entry.entryUniqueId, replyModeLinkedId)
        Toast.makeText(this, "Linked/Reassigned entry!", Toast.LENGTH_SHORT).show()
        refreshEntries(tableName) // refresh RecyclerView
        exitReplyMode()
    }

    private fun RecyclerView.isNearBottom(threshold: Int = 3): Boolean {
        val lm = layoutManager as? LinearLayoutManager ?: return false
        val lastVisible = lm.findLastVisibleItemPosition()
        val total = adapter?.itemCount ?: return false
        return lastVisible >= (total - 1 - threshold)
    }

    // ========= ANDROID ONCREATE =========
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_book_detail)
        replyBar = findViewById(R.id.replyBar)
        replyBarTitle = findViewById(R.id.replyBarTitle)
        replyBarCancel = findViewById(R.id.replyBarCancel)
        replyDivider = findViewById(R.id.replyDivider)


        // Insets → avoid overlaps
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        var lastImeInset = 0

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, ins ->
            val ime = ins.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = ins.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, maxOf(ime, nav))

            // POC: keyboard just opened
            if (ime > 0 && lastImeInset == 0) {
                if (entryRecyclerView.isNearBottom()) {
                    entryRecyclerView.post {
                        entryRecyclerView.scrollToPosition(recyclerAdapter.itemCount - 1)
                    }
                }
            }
            lastImeInset = ime

            ins
        }


        window.statusBarColor = ContextCompat.getColor(this, R.color.md_theme_dark_onPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        // ===== Permissions =====
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val need = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) need.add(android.Manifest.permission.CAMERA)

            if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 123)
        }

        // ===== Toolbar =====
        toolbarTitle = findViewById(R.id.toolbarTitle)
        //replyModeText = findViewById(R.id.replyModeText)
        pendingButton = findViewById(R.id.pendingButton)
        cancelPendingButton = findViewById(R.id.cancelPendingButton)
        searchButton = findViewById(R.id.searchButton)
        cancelSearchButton = findViewById(R.id.cancelSearchButton)
        chainViewModeTitle = findViewById(R.id.chainViewModeTitle)
        chainCancelButton = findViewById(R.id.chainCancelButton)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val searchInputBar = toolbar.findViewById<EditText>(R.id.searchInputBar)

        val logBookName = intent.getStringExtra("LOG_BOOK_NAME") ?: "Diary Details"
        toolbarTitle.text = logBookName

        tableName = logBookName.replace("[^A-Za-z0-9_]".toRegex(), "_").lowercase()
        dbHelper = LogBookDatabaseHelper(this)

        // ===== RecyclerView =====
        entryRecyclerView = findViewById(R.id.entryRecyclerView)
        entryRecyclerView.layoutManager = LinearLayoutManager(this)


        val entries = dbHelper.getLastNEntries(tableName, 150)
        recyclerAdapter = DiaryEntryRecyclerAdapter(entries)
        entryRecyclerView.adapter = recyclerAdapter

        entryRecyclerView.post {
            entryRecyclerView.scrollToPosition(recyclerAdapter.itemCount - 1)
        }

        // ========= SWIPE LOGIC =========
        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.adapterPosition
                val entry = recyclerAdapter.items[pos]

                if (dir == ItemTouchHelper.RIGHT) {
                    // Reply mode
                    replyModeLinkedId = entry.entryUniqueId
                    isReplyMode = true
                    showReplyToolbar(entry)

                } else if (dir == ItemTouchHelper.LEFT) {

                    // Block chain when replying
                    if (isReplyMode) {
                        recyclerAdapter.notifyItemChanged(pos)
                        return
                    }

                    val id = entry.entryUniqueId.toInt()

                    // Close pending mode if active
                    if (cancelPendingButton.visibility == View.VISIBLE) {
                        cancelPendingButton.visibility = View.GONE
                        pendingButton.visibility = View.VISIBLE
                        refreshEntries(tableName)
                    }

                    activateChainViewMode(id)
                }

                recyclerAdapter.notifyItemChanged(pos)
            }
        }
        ItemTouchHelper(swipe).attachToRecyclerView(entryRecyclerView)


        // ========= PENDING FILTER =========
        pendingButton.setOnClickListener {
            enterPendingMode()
        }

        cancelPendingButton.setOnClickListener {
            exitPendingMode()
        }


        // ========= SEARCH =========
        searchButton.setOnClickListener {
            enterSearchMode()
            searchInputBar.visibility = View.VISIBLE
            searchInputBar.requestFocus()
        }

        cancelSearchButton.setOnClickListener {

            // 2) Exit CHAIN mode
            if (isChainMode) {
                exitChainViewMode()
                return@setOnClickListener
            }

            // 3) Exit SEARCH mode
            exitSearchMode(searchInputBar)
        }



        searchInputBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()

                if (q.isEmpty()) {
                    refreshEntries(tableName)
                    return
                }

                val words = q.split("\\s+".toRegex()).map { it.lowercase() }
                val filtered = dbHelper.getLastNEntries(tableName, 150).filter { entry ->
                    val text = "${entry.textTask ?: ""} ${entry.note ?: ""}".lowercase()
                    words.all { text.contains(it) }
                }

                recyclerAdapter.update(filtered)
            }

            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })


        // ========= INPUT BAR =========
        val inputBox = findViewById<EditText>(R.id.inputBox)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val mic = findViewById<ImageButton>(R.id.micButton)
        val attach = findViewById<ImageButton>(R.id.attachButton)
        val camera = findViewById<ImageButton>(R.id.cameraButton)

        // Camera
        camera.setOnClickListener {
            val file = createImageFile()
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            startActivityForResult(intent, CAMERA_REQUEST_CODE)
        }


        inputBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val typing = s != null && s.isNotEmpty()
                mic.visibility = if (typing) View.GONE else View.VISIBLE
                attach.visibility = if (typing) View.GONE else View.VISIBLE
                camera.visibility = if (typing) View.GONE else View.VISIBLE

                val pad = if (typing) 0 else 80.dpToPx()
                inputBox.setPadding(
                    inputBox.paddingLeft,
                    inputBox.paddingTop,
                    pad,
                    inputBox.paddingBottom
                )
            }

            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        attach.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }

        // Send
        sendButton.setOnClickListener {
            val text = inputBox.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter some text!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = java.util.Calendar.getInstance()
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(now.time)
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(now.time)

            dbHelper.insertDiaryEntry(tableName, text, date, time, replyModeLinkedId)
            inputBox.setText("")

            exitReplyMode()
            refreshEntries(tableName)

            entryRecyclerView.scrollToPosition(recyclerAdapter.itemCount - 1)
        }
    }


    // ========= CAMERA RESULT =========
    private fun createImageFile(): java.io.File {
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val dir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_PICTURES
        )
        val file = java.io.File(dir, "IMG_$ts.jpg")
        cameraImagePath = file.absolutePath
        return file
    }


    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)

        if (req == CAMERA_REQUEST_CODE && res == RESULT_OK && cameraImagePath != null) {
            // existing camera logic (unchanged)
            val file = java.io.File(cameraImagePath!!)
            val uri = Uri.fromFile(file)

            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
            sendBroadcast(scanIntent)

            val now = java.util.Calendar.getInstance()
            val date = java.text.SimpleDateFormat("yyyy-MM-dd").format(now.time)
            val time = java.text.SimpleDateFormat("HH:mm:ss").format(now.time)

            dbHelper.insertImageEntry(tableName, cameraImagePath!!, date, time)
            refreshEntries(tableName)
            entryRecyclerView.scrollToPosition(recyclerAdapter.itemCount - 1)
        }

        if (req == GALLERY_REQUEST_CODE && res == RESULT_OK && data?.data != null) {
            val pickedUri = data.data!!

            // Persist read permission for this URI (optional but good)
            contentResolver.takePersistableUriPermission(
                pickedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // 1) Open input stream from picked URI
            val inputStream = contentResolver.openInputStream(pickedUri) ?: return

            // 2) Create target file in app internal dir
            val now = java.util.Calendar.getInstance()
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(now.time)

            val imagesDir = java.io.File(filesDir, "onelogs_images")
            val fileName = "IMG_GAL_$ts.jpg"
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val destFile = java.io.File(imagesDir, "IMG_GAL_$ts.jpg")

            // 3) Copy bytes
            inputStream.use { inStream ->
                java.io.FileOutputStream(destFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            // 4) Insert DB entry with internal path
            val date = java.text.SimpleDateFormat("yyyy-MM-dd").format(now.time)
            val time = java.text.SimpleDateFormat("HH:mm:ss").format(now.time)

            val relativePath = "onelogs_images/$fileName"
            dbHelper.insertImageEntry(tableName, relativePath, date, time)

            // 5) Refresh UI
            refreshEntries(tableName)
            entryRecyclerView.scrollToPosition(recyclerAdapter.itemCount - 1)
        }
    }

}

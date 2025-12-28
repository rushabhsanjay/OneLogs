package com.oddworks.onelogs

import LogBookDatabaseHelper
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GlobalTasksActivity : AppCompatActivity() {

    lateinit var tableName: String
    lateinit var dbHelper: LogBookDatabaseHelper
    private lateinit var adapter: GlobalTasksAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_globaltasks)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.timeline_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.md_theme_dark_onPrimary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        dbHelper = LogBookDatabaseHelper(this)
        tableName = "allpendingtasks97531"

        // build table fresh
        dbHelper.recreateAllPendingTasksTable()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        recyclerView = findViewById(R.id.timelineRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GlobalTasksAdapter(mutableListOf())
        recyclerView.adapter = adapter

        loadTasks()


        val navLogbooks = findViewById<LinearLayout>(R.id.navLogbooks)
        navLogbooks.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        val navTimeline = findViewById<LinearLayout>(R.id.navTimeline)
        navTimeline.setOnClickListener {
            val intent = Intent(this, TimelineActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

    }

    private fun loadTasks() {
        Thread {
            val allEntries = dbHelper.getTimelineEntriesWithLimit(tableName, Int.MAX_VALUE, 0)
            runOnUiThread {
                adapter.update(allEntries)
                recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }.start()
    }
}

package com.github.reline.example

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

data class Entry(val id: Long, val title: String, val subtitle: String)

class MainActivity : AppCompatActivity() {
    private lateinit var dao: FeedDao
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val factory = provideCopyFactory(applicationContext, provideFactory())
        val config = provideFeedReaderConfig(applicationContext)
        val database = provideDatabase(factory, config)
        dao = provideDao(database)
    }

    fun onButtonClicked(v: View) {
        dao.insertEntry("title", "subtitle")
    }
}
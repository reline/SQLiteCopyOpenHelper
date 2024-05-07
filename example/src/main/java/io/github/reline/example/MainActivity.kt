package io.github.reline.example

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.github.reline.example.R

data class Entry(val id: Long, val title: String, val subtitle: String)

class MainActivity : AppCompatActivity() {
    private lateinit var dao: FeedDao
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val factory = provideCopyFactory(provideFactory())
        val config = provideFeedReaderConfig(applicationContext)
        val database = provideHelper(factory, config)
        dao = provideDao(database)
    }

    fun onButtonClicked(@Suppress("UNUSED_PARAMETER") v: View) {
        dao.insertEntry("title", "subtitle")
    }
}
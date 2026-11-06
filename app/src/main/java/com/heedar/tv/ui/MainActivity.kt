package com.heedar.tv.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.heedar.tv.HeedarApp
import com.heedar.tv.databinding.ActivityMainBinding
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.ui.catalog.CatalogFragment
import com.heedar.tv.ui.favorites.FavoritesFragment
import com.heedar.tv.ui.login.LoginActivity
import com.heedar.tv.ui.search.SearchFragment
import com.heedar.tv.ui.settings.SettingsFragment

/**
 * Post-login home. Owns the eight top-level sections and keeps one fragment per
 * section alive so tab switching preserves scroll position and loaded data.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var serverConfig: ServerConfig

    private var currentSection: ContentSection = ContentSection.LIVE
    private var suppressTabCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = (application as HeedarApp).container.playlistStore.load()
        if (config == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        serverConfig = config

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()

        val restored = ContentSection.fromName(savedInstanceState?.getString(KEY_SECTION))
            ?: ContentSection.LIVE
        showSection(restored)

        onBackPressedDispatcher.addCallback(this) {
            if (currentSection != ContentSection.LIVE) {
                showSection(ContentSection.LIVE)
            } else {
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SECTION, currentSection.name)
    }

    private fun setupTabs() {
        ContentSection.entries.forEach { section ->
            val tab = binding.tabs.newTab()
            tab.setText(section.titleRes)
            tab.tag = section
            binding.tabs.addTab(tab)
        }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabCallback) return
                (tab.tag as? ContentSection)?.let { showSection(it) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun showSection(section: ContentSection) {
        currentSection = section
        val tag = section.name
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()

        manager.fragments.forEach { fragment ->
            if (fragment.tag != tag) transaction.hide(fragment)
        }

        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragmentContainer, createFragment(section), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()

        syncTabSelection(section)
    }

    private fun syncTabSelection(section: ContentSection) {
        val index = ContentSection.entries.indexOf(section)
        if (binding.tabs.selectedTabPosition == index) return
        suppressTabCallback = true
        binding.tabs.getTabAt(index)?.select()
        suppressTabCallback = false
    }

    private fun createFragment(section: ContentSection): Fragment = when (section) {
        ContentSection.LIVE,
        ContentSection.MOVIES,
        ContentSection.SERIES,
        ContentSection.NEWS,
        ContentSection.SPORTS -> CatalogFragment.newInstance(section)

        ContentSection.SEARCH -> SearchFragment()
        ContentSection.FAVORITES -> FavoritesFragment()
        ContentSection.SETTINGS -> SettingsFragment()
    }

    private companion object {
        const val KEY_SECTION = "com.heedar.tv.state.SECTION"
    }
}

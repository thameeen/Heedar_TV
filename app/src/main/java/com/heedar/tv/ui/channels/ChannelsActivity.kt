package com.heedar.tv.ui.channels

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.databinding.ActivityChannelsBinding
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.ui.login.LoginActivity
import com.heedar.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class ChannelsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChannelsBinding
    private lateinit var serverConfig: ServerConfig

    private val adapter = ChannelListAdapter { channel ->
        startActivity(PlayerActivity.newIntent(this, serverConfig, channel))
    }

    private val viewModel: ChannelsViewModel by viewModels {
        val container = (application as HeedarApp).container
        viewModelFactory {
            initializer {
                ChannelsViewModel(
                    repository = container.createRepository(serverConfig),
                    ioDispatcher = container.ioDispatcher
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = (application as HeedarApp).container.playlistStore.load()
        if (config == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        serverConfig = config

        binding = ActivityChannelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.rvChannels.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.load() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: ChannelsUiState) {
        val loading = state is ChannelsUiState.Loading
        binding.progressChannels.visibility = if (loading) View.VISIBLE else View.GONE
        binding.rvChannels.visibility =
            if (state is ChannelsUiState.Content) View.VISIBLE else View.GONE

        when (state) {
            is ChannelsUiState.Loading -> {
                binding.groupStatus.visibility = View.GONE
            }

            is ChannelsUiState.Content -> {
                binding.groupStatus.visibility = View.GONE
                adapter.submitList(state.channels)
            }

            is ChannelsUiState.Empty -> {
                binding.groupStatus.visibility = View.VISIBLE
                binding.tvStatus.setText(R.string.channels_empty)
                binding.btnRetry.visibility = View.GONE
            }

            is ChannelsUiState.Error -> {
                binding.groupStatus.visibility = View.VISIBLE
                binding.tvStatus.setText(state.reason.toMessageRes())
                binding.btnRetry.visibility = View.VISIBLE
            }
        }
    }

    private fun XtreamFailure.toMessageRes(): Int = when (this) {
        XtreamFailure.AUTH -> R.string.error_auth
        XtreamFailure.NETWORK -> R.string.error_network
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN -> R.string.error_unknown
    }
}

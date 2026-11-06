package com.heedar.tv.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.databinding.ActivityLoginBinding
import com.heedar.tv.ui.channels.ChannelsActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by viewModels {
        val container = (application as HeedarApp).container
        viewModelFactory {
            initializer {
                LoginViewModel(
                    playlistStore = container.playlistStore,
                    repositoryFactory = container::createRepository,
                    ioDispatcher = container.ioDispatcher
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etServerUrl.doAfterTextChanged { viewModel.onServerUrlChanged(it?.toString().orEmpty()) }
        binding.etUsername.doAfterTextChanged { viewModel.onUsernameChanged(it?.toString().orEmpty()) }
        binding.etPassword.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        binding.btnLogin.setOnClickListener { viewModel.submit() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: LoginUiState) {
        binding.progressLogin.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !state.isLoading
        binding.tvCleartextWarning.visibility = if (state.isCleartext) View.VISIBLE else View.GONE

        val errorRes = state.error?.messageRes()
        if (errorRes == null) {
            binding.tvError.visibility = View.GONE
            binding.tvError.text = null
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.setText(errorRes)
        }

        if (state.navigateToChannels) {
            viewModel.onNavigationHandled()
            startActivity(Intent(this, ChannelsActivity::class.java))
            finish()
        }
    }

    private fun LoginError.messageRes(): Int = when (this) {
        LoginError.EMPTY_FIELDS -> R.string.error_required_fields
        LoginError.INVALID_URL -> R.string.error_invalid_url
        LoginError.AUTH -> R.string.error_auth
        LoginError.NETWORK -> R.string.error_network
        LoginError.UNKNOWN -> R.string.error_unknown
    }
}

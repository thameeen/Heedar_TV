package com.heedar.tv.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.heedar.tv.HeedarApp
import com.heedar.tv.R
import com.heedar.tv.data.PlaylistStore
import com.heedar.tv.databinding.FragmentSettingsBinding
import com.heedar.tv.domain.AccountProfile
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.ui.login.LoginActivity
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Language, account snapshot, server and logout. Language switching uses
 * AppCompat's per-app locales, so the whole process restarts into the chosen
 * language and RTL is applied by the platform rather than by hand.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding: FragmentSettingsBinding get() = requireNotNull(_binding)

    private lateinit var playlistStore: PlaylistStore
    private lateinit var serverConfig: ServerConfig

    private val viewModel: SettingsViewModel by viewModels {
        val container = (requireActivity().application as HeedarApp).container
        viewModelFactory {
            initializer { SettingsViewModel(container.createRepository(serverConfig)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (requireActivity().application as HeedarApp).container
        playlistStore = container.playlistStore
        serverConfig = requireNotNull(container.playlistStore.load()) {
            "SettingsFragment requires a saved playlist"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindLanguage()
        binding.tvServerUrl.text = serverConfig.baseUrl
        binding.tvVersion.text = getString(R.string.settings_version, versionName())

        binding.btnLogout.setOnClickListener { logout() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindLanguage() {
        val tag = currentLanguageTag()
        binding.rbArabic.isChecked = tag == LANGUAGE_ARABIC
        binding.rbEnglish.isChecked = tag != LANGUAGE_ARABIC

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val requested = if (checkedId == R.id.rbArabic) LANGUAGE_ARABIC else LANGUAGE_ENGLISH
            if (requested != currentLanguageTag()) {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(requested)
                )
            }
        }
    }

    private fun currentLanguageTag(): String {
        val fromDelegate = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (fromDelegate.isNotBlank()) return fromDelegate.substringBefore('-').lowercase()
        @Suppress("DEPRECATION")
        return resources.configuration.locale.language
    }

    private fun render(state: SettingsViewModel.State) {
        binding.progressProfile.visibility =
            if (state.loading) View.VISIBLE else View.GONE

        val failure = state.failure
        binding.tvProfileError.visibility = if (failure == null) View.GONE else View.VISIBLE
        failure?.let { binding.tvProfileError.setText(it.toMessageRes()) }

        val profile = state.profile
        binding.groupProfile.visibility = if (profile == null) View.GONE else View.VISIBLE
        profile?.let { renderProfile(it) }
    }

    private fun renderProfile(profile: AccountProfile) {
        val unknown = getString(R.string.profile_unknown)
        binding.tvProfileUsername.text = row(R.string.profile_username, profile.username ?: unknown)
        binding.tvProfileStatus.text = row(R.string.profile_status, profile.status ?: unknown)
        binding.tvProfileExpiry.text = row(R.string.profile_expiry, formatExpiry(profile, unknown))
        binding.tvProfileConnections.text = row(
            R.string.profile_connections,
            connections(profile, unknown)
        )

        // Xtream's user_info has no explicit plan name beyond trial/status, so the
        // row is hidden instead of inventing a value.
        binding.tvProfilePlan.visibility = View.GONE
    }

    private fun row(labelRes: Int, value: String): String =
        "${getString(labelRes)}: $value"

    private fun formatExpiry(profile: AccountProfile, unknown: String): String {
        val millis = profile.expiresAtMillis ?: return unknown
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }

    private fun connections(profile: AccountProfile, unknown: String): String {
        val active = profile.activeConnections?.toString() ?: "0"
        val max = profile.maxConnections?.toString() ?: return unknown
        return "$active / $max"
    }

    private fun logout() {
        playlistStore.clear()
        val intent = Intent(requireContext(), LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String = runCatching {
        requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName
    }.getOrNull().orEmpty()

    private fun XtreamFailure.toMessageRes(): Int = when (this) {
        XtreamFailure.AUTH -> R.string.error_auth
        XtreamFailure.NETWORK -> R.string.error_network
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN -> R.string.error_unknown
    }

    private companion object {
        const val LANGUAGE_ARABIC = "ar"
        const val LANGUAGE_ENGLISH = "en"
    }
}

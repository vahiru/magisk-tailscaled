package io.github.vahiru.tailscaled.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.data.Fixtures
import io.github.vahiru.tailscaled.data.ModuleInfo
import io.github.vahiru.tailscaled.data.RootManager
import io.github.vahiru.tailscaled.data.TsPrefs
import io.github.vahiru.tailscaled.data.TsStatus
import io.github.vahiru.tailscaled.ui.screens.HomeScreen
import io.github.vahiru.tailscaled.ui.screens.LoginScreen
import io.github.vahiru.tailscaled.ui.screens.PeersScreen
import io.github.vahiru.tailscaled.ui.screens.SettingsActions
import io.github.vahiru.tailscaled.ui.screens.SettingsScreen
import io.github.vahiru.tailscaled.ui.theme.TailscaledTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens with fake data into build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    private val module = ModuleInfo(
        installed = true, moduleDir = true, version = "v3.0.0.0", versionCode = 3000000,
        rootManager = RootManager.KernelSU, daemonPid = 4242,
    )
    private val status = TsStatus.parse(Fixtures.STATUS_JSON).let { s ->
        // A few more peers so the list looks realistic.
        val extra = listOf(
            s.peers[1].copy(id = "n3", hostName = "macbook", dnsName = "macbook.tail1234.ts.net.", os = "macOS", online = true, ips = listOf("100.64.0.12"), lastSeen = null),
            s.peers[1].copy(id = "n4", hostName = "iphone", dnsName = "iphone.tail1234.ts.net.", os = "iOS", online = true, ips = listOf("100.64.0.13"), lastSeen = null),
            s.peers[1].copy(id = "n5", hostName = "router", dnsName = "router.tail1234.ts.net.", os = "linux", online = true, ips = listOf("100.64.0.14"), primaryRoutes = listOf("10.0.0.0/24"), exitNodeOption = true, lastSeen = null),
        )
        s.copy(peers = (s.peers + extra).sortedWith(compareByDescending<io.github.vahiru.tailscaled.data.Peer> { it.online }.thenBy { it.displayName }))
    }
    private val prefs = TsPrefs(routeAll = true, corpDns = false, runSsh = true, wantRunning = true, hostname = "pixel")
    private val connected = UiState(root = RootState.Granted, module = module, status = status, prefs = prefs, loaded = true)

    private val noopSettings = SettingsActions({}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {})

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Frame(selected: Int, title: String? = null, content: @Composable () -> Unit) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title ?: stringResource(R.string.app_name)) },
                    actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.Refresh, null) } },
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf(
                        R.string.tab_home to Icons.Filled.Home,
                        R.string.tab_devices to Icons.Filled.Devices,
                        R.string.tab_settings to Icons.Filled.Settings,
                    ).forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = i == selected, onClick = {}, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                    }
                }
            },
        ) { p -> Column(Modifier.fillMaxSize().padding(p)) { content() } }
    }

    private fun home(state: UiState) = @Composable {
        TailscaledTheme { Frame(0) { HomeScreen(state, {}, {}, {}, {}, {}) } }
    }

    @Test
    fun homeConnected() = captureRoboImage("home_connected.png", content = home(connected))

    @Test
    @Config(qualifiers = "+night")
    fun homeConnectedDark() = captureRoboImage("home_connected_dark.png", content = home(connected))

    @Test
    fun homeNeedsLogin() = captureRoboImage(
        "home_needs_login.png",
        content = home(connected.copy(status = TsStatus.parse("""{"BackendState":"NeedsLogin"}"""), prefs = null)),
    )

    @Test
    fun homeNoModule() = captureRoboImage(
        "home_no_module.png",
        content = home(UiState(root = RootState.Granted, module = ModuleInfo(rootManager = RootManager.KernelSU), loaded = true)),
    )

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun devicesZh() = captureRoboImage("devices_zh.png") {
        TailscaledTheme { Frame(1, stringResource(R.string.tab_devices)) { PeersScreen(connected, {}, {}) } }
    }

    @Test
    fun settings() = captureRoboImage("settings.png") {
        TailscaledTheme { Frame(2, stringResource(R.string.tab_settings)) { SettingsScreen(connected, noopSettings) } }
    }

    @Test
    @Config(qualifiers = "+zh-rCN")
    fun loginZh() = captureRoboImage("login_zh.png") {
        TailscaledTheme {
            LoginScreen(
                login = LoginState(running = true, authUrl = "https://login.tailscale.com/a/1a2b3c4d5e6f"),
                prefs = null,
                snackbar = SnackbarHostState(),
                onStart = { _, _, _ -> },
                onCancel = {},
                onClose = {},
            )
        }
    }
}

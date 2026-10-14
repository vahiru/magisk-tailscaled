package io.github.vahiru.tailscaled.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

/**
 * Whether Android's Private DNS (DNS over TLS) is in use on the active network. It
 * bypasses tailscaled's DNS redirect on port 53, so MagicDNS names stop resolving.
 */
fun isPrivateDnsActive(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return runCatching { cm.getLinkProperties(cm.activeNetwork)?.isPrivateDnsActive == true }.getOrDefault(false)
}

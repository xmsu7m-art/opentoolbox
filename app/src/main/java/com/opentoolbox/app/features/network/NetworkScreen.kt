package com.opentoolbox.app.features.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentoolbox.app.ui.components.InfoRow
import com.opentoolbox.app.ui.components.SectionTitle
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * 默认网络的一次快照：能力 + 链路属性。
 */
private data class NetworkSnapshot(
    val capabilities: NetworkCapabilities?,
    val linkProperties: LinkProperties?,
)

private fun readNetworkSnapshot(connectivityManager: ConnectivityManager?): NetworkSnapshot {
    val network = connectivityManager?.activeNetwork ?: return NetworkSnapshot(null, null)
    return NetworkSnapshot(
        capabilities = connectivityManager.getNetworkCapabilities(network),
        linkProperties = connectivityManager.getLinkProperties(network),
    )
}

/**
 * 本机 IP 地址：接口名到地址的映射，IPv4 与 IPv6 分开。
 */
private data class LocalAddresses(
    val ipv4: List<Pair<String, String>>,
    val ipv6: List<Pair<String, String>>,
)

private fun readLocalAddresses(): LocalAddresses {
    val ipv4 = mutableListOf<Pair<String, String>>()
    val ipv6 = mutableListOf<Pair<String, String>>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return LocalAddresses(ipv4, ipv6)
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address.isLoopbackAddress) continue
                val host = address.hostAddress ?: continue
                when (address) {
                    is Inet4Address -> ipv4.add(networkInterface.name to host)
                    is Inet6Address -> ipv6.add(networkInterface.name to host)
                }
            }
        }
    } catch (e: Exception) {
        // 枚举网络接口失败时保留已读取到的结果
    }
    return LocalAddresses(ipv4, ipv6)
}

private fun connectionTypeText(capabilities: NetworkCapabilities?): String {
    if (capabilities == null) return "无连接"
    val base = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "蓝牙共享"
        else -> "其他网络"
    }
    return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "$base（VPN）" else base
}

private fun bandwidthText(kbps: Int): String = when {
    kbps <= 0 -> "未知"
    kbps >= 1000 -> String.format(Locale.US, "%.1f Mbps", kbps / 1000.0)
    else -> "$kbps Kbps"
}

private fun gatewayText(linkProperties: LinkProperties?): String {
    if (linkProperties == null) return "—"
    val gateway = linkProperties.routes.firstOrNull { it.gateway != null }?.gateway
    return gateway?.hostAddress ?: "未找到"
}

private fun domainsText(linkProperties: LinkProperties?): String =
    linkProperties?.domains?.takeIf { it.isNotEmpty() } ?: "—"

private fun dnsText(linkProperties: LinkProperties?): String {
    if (linkProperties == null) return "—"
    return linkProperties.dnsServers
        .joinToString("、") { it.hostAddress ?: it.toString() }
        .ifEmpty { "—" }
}

private fun ssidDisplayText(ssid: String?): String =
    if (ssid == null || ssid == WifiManager.UNKNOWN_SSID) {
        WifiManager.UNKNOWN_SSID
    } else {
        ssid.removeSurrounding("\"").ifEmpty { WifiManager.UNKNOWN_SSID }
    }

private fun signalText(rssi: Int): String {
    val level = WifiManager.calculateSignalLevel(rssi, 5) // 0..4
    val label = when (level) {
        0 -> "很差"
        1 -> "较弱"
        2 -> "一般"
        3 -> "良好"
        else -> "优秀"
    }
    return "$rssi dBm（$label）"
}

private fun frequencyText(mhz: Int): String = when {
    mhz <= 0 -> "未知"
    mhz < 3000 -> "$mhz MHz（2.4 GHz 频段）"
    mhz < 5900 -> "$mhz MHz（5 GHz 频段）"
    else -> "$mhz MHz（6 GHz 频段）"
}

private fun wifiIpText(ip: Int): String = String.format(
    Locale.US,
    "%d.%d.%d.%d",
    ip and 0xFF,
    (ip shr 8) and 0xFF,
    (ip shr 16) and 0xFF,
    (ip shr 24) and 0xFF,
)

/**
 * 网络信息页：连接类型、链路信息、本机 IP 与 Wi-Fi 详情。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    val wifiManager = remember {
        context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    var snapshot by remember { mutableStateOf(readNetworkSnapshot(connectivityManager)) }
    val localAddresses = remember { readLocalAddresses() }
    @Suppress("DEPRECATION")
    val wifiInfo = remember { wifiManager?.connectionInfo }

    // 监听默认网络变化，状态变化时重新读取快照；注册失败时仍展示进入页面时读取的数据。
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                snapshot = readNetworkSnapshot(connectivityManager)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                snapshot = readNetworkSnapshot(connectivityManager)
            }

            override fun onLost(network: Network) {
                snapshot = readNetworkSnapshot(connectivityManager)
            }
        }
        var registered = false
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: Exception) {
            // 部分设备可能拒绝注册默认网络回调
        }
        onDispose {
            if (registered) {
                try {
                    connectivityManager?.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // 回调可能已被系统注销
                }
            }
        }
    }

    val capabilities = snapshot.capabilities
    val linkProperties = snapshot.linkProperties
    val wifiConnected = wifiInfo != null && wifiInfo.networkId != -1
    val ssidUnknown = wifiConnected &&
        (wifiInfo.ssid == null || wifiInfo.ssid == WifiManager.UNKNOWN_SSID)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网络信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Column {
                    SectionTitle("当前连接")
                    if (capabilities == null) {
                        Text(
                            text = "当前没有活动网络连接",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InfoRow("连接类型", connectionTypeText(capabilities))
                    InfoRow(
                        "计费状态",
                        capabilities?.let {
                            if (!it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                                "计费网络"
                            } else {
                                "不计费"
                            }
                        } ?: "—",
                    )
                    InfoRow(
                        "互联网访问",
                        capabilities?.let {
                            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                                "可正常访问"
                            } else {
                                "受限"
                            }
                        } ?: "—",
                    )
                    InfoRow("下行带宽（估算）", capabilities?.let { bandwidthText(it.linkDownstreamBandwidthKbps) } ?: "—")
                    InfoRow("上行带宽（估算）", capabilities?.let { bandwidthText(it.linkUpstreamBandwidthKbps) } ?: "—")
                }
            }
            item {
                Column {
                    SectionTitle("链路信息")
                    InfoRow("接口名", linkProperties?.interfaceName ?: "—")
                    InfoRow("网关", gatewayText(linkProperties))
                    InfoRow("域名", domainsText(linkProperties))
                    InfoRow("DNS 服务器", dnsText(linkProperties))
                }
            }
            item {
                Column {
                    SectionTitle("本机 IPv4 地址")
                    if (localAddresses.ipv4.isEmpty()) {
                        Text(
                            text = "未获取到 IPv4 地址",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        localAddresses.ipv4.forEach { (name, host) -> InfoRow(name, host) }
                    }
                }
            }
            item {
                Column {
                    SectionTitle("本机 IPv6 地址")
                    if (localAddresses.ipv6.isEmpty()) {
                        Text(
                            text = "未获取到 IPv6 地址",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        localAddresses.ipv6.forEach { (name, host) -> InfoRow(name, host) }
                    }
                }
            }
            item {
                Column {
                    SectionTitle("Wi-Fi 详情")
                    if (!wifiConnected || wifiInfo == null) {
                        Text(
                            text = "当前未连接 Wi-Fi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        InfoRow("SSID", ssidDisplayText(wifiInfo.ssid))
                        InfoRow("BSSID", wifiInfo.bssid?.takeIf { it.isNotEmpty() } ?: "—")
                        InfoRow("链路速度", "${wifiInfo.linkSpeed} Mbps")
                        InfoRow("信号强度", signalText(wifiInfo.rssi))
                        InfoRow("频率", frequencyText(wifiInfo.frequency))
                        if (wifiInfo.ipAddress != 0) {
                            InfoRow("IP 地址", wifiIpText(wifiInfo.ipAddress))
                        }
                        if (ssidUnknown) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Text(
                                    text = "说明：读取 Wi-Fi 名称（SSID）与 MAC 地址需要定位权限，" +
                                        "本应用未申请定位权限，因此系统返回 <unknown ssid>。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

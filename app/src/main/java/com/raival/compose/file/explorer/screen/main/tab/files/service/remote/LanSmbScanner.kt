package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import jcifs.context.SingletonContext
import jcifs.smb.SmbFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LanSmbScanner(private val context: Context) {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _servers = MutableStateFlow<List<LanSmbServer>>(emptyList())
    val servers: StateFlow<List<LanSmbServer>> = _servers.asStateFlow()

    private var scanJob: Job? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        private var isJcifsInitialized = false

        fun ensureJcifsInitialized() {
            if (isJcifsInitialized) return
            synchronized(this) {
                if (isJcifsInitialized) return
                try {
                    val prop = Properties().apply {
                        setProperty("jcifs.netbios.cachePolicy", "0")
                        setProperty("jcifs.smb.client.maxVersion", "SMB1")
                        setProperty("jcifs.smb.client.responseTimeout", "3000")
                        setProperty("jcifs.smb.client.soTimeout", "3000")
                    }
                    SingletonContext.init(prop)
                } catch (_: Throwable) {
                    // Ignore if already initialized
                }
                isJcifsInitialized = true
            }
        }
    }

    fun startScan(scope: CoroutineScope) {
        stopScan()
        _isScanning.value = true
        _servers.value = emptyList()

        val foundMap = ConcurrentHashMap<String, LanSmbServer>()

        fun emitServer(server: LanSmbServer) {
            val ip = server.address.hostAddress ?: return
            val existing = foundMap[ip]
            if (existing == null || (existing.host == ip && server.host != ip)) {
                foundMap[ip] = server
                val sorted = foundMap.values.sorted()
                _servers.value = sorted
            }
        }

        scanJob = scope.launch(Dispatchers.IO) {
            try {
                ensureJcifsInitialized()

                // 1. Start mDNS discovery (_smb._tcp)
                launch {
                    startMdnsDiscovery { emitServer(it) }
                }

                // 2. Query Computer Browser Service (NetServerEnum)
                launch {
                    scanComputerBrowserService { emitServer(it) }
                }

                // 3. Scan local IPv4 subnets (NetBIOS + Port 445 TCP probe)
                val localAddresses = getLocalAddresses()
                val semaphore = Semaphore(40)
                coroutineScope {
                    for (localAddress in localAddresses) {
                        for (targetAddress in localAddress.getSubnetAddresses()) {
                            if (!isActive) break
                            launch {
                                semaphore.withPermit {
                                    probeAddress(targetAddress) { emitServer(it) }
                                }
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(NonCancellable) {
                    stopMdnsDiscovery()
                    _isScanning.value = false
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        stopMdnsDiscovery()
        _isScanning.value = false
    }

    private suspend fun probeAddress(address: Inet4Address, onFound: (LanSmbServer) -> Unit) {
        // Strategy A: NetBIOS Name Service lookup (UDP 137)
        var netbiosHost: String? = null
        try {
            val nameServiceClient = SingletonContext.getInstance().nameServiceClient
            val nbtAddresses = nameServiceClient.getNbtAllByAddress(address.hostAddress)
            netbiosHost = nbtAddresses?.firstOrNull()?.hostName
        } catch (_: Throwable) {
        }

        if (!netbiosHost.isNullOrBlank()) {
            onFound(LanSmbServer(host = netbiosHost, address = address, discoveryMethod = "NetBIOS"))
            return
        }

        // Strategy B: Port 445 Direct TCP Socket Probe (Windows 10/11 & Linux Samba with NetBIOS disabled)
        val portOpen = withContext(Dispatchers.IO) {
            isPortOpen(address, 445, timeoutMs = 400)
        }

        if (portOpen) {
            var hostName = try {
                address.canonicalHostName
            } catch (_: Throwable) {
                null
            }
            if (hostName.isNullOrBlank() || hostName == address.hostAddress) {
                hostName = try {
                    address.hostName
                } catch (_: Throwable) {
                    null
                }
            }
            if (hostName.isNullOrBlank() || hostName == address.hostAddress) {
                hostName = "SMB Server (${address.hostAddress})"
            }

            onFound(LanSmbServer(host = hostName, address = address, discoveryMethod = "Port 445"))
        }
    }

    private fun isPortOpen(address: InetAddress, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun scanComputerBrowserService(onFound: (LanSmbServer) -> Unit) {
        try {
            @Suppress("DEPRECATION")
            val lan = SmbFile("smb://")
            val domains = try {
                lan.listFiles()
            } catch (_: Throwable) {
                null
            } ?: return

            val nameServiceClient = try {
                SingletonContext.getInstance().nameServiceClient
            } catch (_: Throwable) {
                null
            } ?: return

            for (domain in domains) {
                val servers = try {
                    domain.listFiles()
                } catch (_: Throwable) {
                    null
                } ?: continue

                for (server in servers) {
                    val host = server.name.removeSuffix("/")
                    val address = try {
                        nameServiceClient.getByName(host)?.toInetAddress()
                    } catch (_: Throwable) {
                        null
                    } ?: continue
                    onFound(LanSmbServer(host = host, address = address, discoveryMethod = "Browser"))
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun startMdnsDiscovery(onFound: (LanSmbServer) -> Unit) {
        try {
            val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return

            nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType.contains("_smb._tcp")) {
                        try {
                            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    val resolvedAddress = resolved.host
                                    if (resolvedAddress != null) {
                                        val host = resolved.serviceName.ifBlank { resolvedAddress.hostAddress ?: "" }
                                        onFound(LanSmbServer(host = host, address = resolvedAddress, discoveryMethod = "mDNS"))
                                    }
                                }
                            })
                        } catch (_: Throwable) {
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try {
                        nsdManager.stopServiceDiscovery(this)
                    } catch (_: Throwable) {}
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            nsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (_: Throwable) {
        }
    }

    private fun stopMdnsDiscovery() {
        val listener = nsdDiscoveryListener ?: return
        nsdDiscoveryListener = null
        try {
            val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsdManager?.stopServiceDiscovery(listener)
        } catch (_: Throwable) {
        }
    }

    private fun getLocalAddresses(): List<Inet4Address> {
        val result = mutableListOf<Inet4Address>()
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                val bytes = byteArrayOf(
                    (wifiIp and 0xff).toByte(),
                    ((wifiIp shr 8) and 0xff).toByte(),
                    ((wifiIp shr 16) and 0xff).toByte(),
                    ((wifiIp shr 24) and 0xff).toByte()
                )
                val addr = InetAddress.getByAddress(bytes)
                if (addr is Inet4Address && addr.isSiteLocalAddress) {
                    result.add(addr)
                }
            }
        } catch (_: Throwable) {
        }

        try {
            for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (addr in networkInterface.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress && !result.contains(addr)) {
                        result.add(addr)
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return result
    }

    private fun Inet4Address.getSubnetAddresses(): Sequence<Inet4Address> = sequence {
        val addressBytes = address.clone()
        for (i in 0..99) {
            for (j in 0..2) {
                val lastBit = 100 * j + i
                if (lastBit in 1..254) {
                    addressBytes[3] = lastBit.toByte()
                    try {
                        val genAddr = InetAddress.getByAddress(addressBytes) as? Inet4Address
                        if (genAddr != null) {
                            yield(genAddr)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}

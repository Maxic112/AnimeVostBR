package com.animevostbr.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/** Another device waiting for sync, found on the local network. */
data class SyncPeer(val name: String, val host: String, val port: Int)

/**
  * Finds the waiting device without typing an address (Android NSD / mDNS). Networks that block
  * multicast (some routers, VPNs) need the address typed by hand; [localAddresses] shows it.
  */
object SyncNetwork {

    fun deviceName(): String = "AnimeVostBR · ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    /** IPv4 addresses of this device, e.g. Wi-Fi and a VPN. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress.orEmpty() }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    class Advertiser(context: Context) {
        private val nsd = context.getSystemService(NsdManager::class.java)
        private var listener: NsdManager.RegistrationListener? = null

        fun start(port: Int) {
            val info = NsdServiceInfo().apply {
                serviceName = deviceName()
                serviceType = Sync.SERVICE_TYPE
                setPort(port)
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) = Unit
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
            }
            listener = l
            runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
        }

        fun stop() {
            listener?.let { runCatching { nsd?.unregisterService(it) } }
            listener = null
        }
    }

    /** Reports the waiting devices found so far; resolves one service at a time, as old NSD requires. */
    class Browser(context: Context, private val onChange: (List<SyncPeer>) -> Unit) {
        private val nsd = context.getSystemService(NsdManager::class.java)
        private val peers = linkedMapOf<String, SyncPeer>()
        private val queue = ArrayDeque<NsdServiceInfo>()
        private var resolving = false
        private var listener: NsdManager.DiscoveryListener? = null
        private val ownName = deviceName()

        fun start() {
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceName == ownName) return
                    synchronized(queue) { queue.addLast(info) }
                    resolveNext()
                }

                override fun onServiceLost(info: NsdServiceInfo) {
                    synchronized(peers) { peers.remove(info.serviceName) }
                    publish()
                }
            }
            listener = l
            runCatching { nsd?.discoverServices(Sync.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
        }

        @Suppress("DEPRECATION") // resolveService works on every supported version
        private fun resolveNext() {
            val next = synchronized(queue) {
                if (resolving) return
                queue.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            val done = {
                synchronized(queue) { resolving = false }
                resolveNext()
            }
            runCatching {
                nsd?.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = done()

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress
                        if (host != null) {
                            synchronized(peers) { peers[info.serviceName] = SyncPeer(info.serviceName, host, info.port) }
                            publish()
                        }
                        done()
                    }
                })
            }.onFailure { done() }
        }

        private fun publish() = onChange(synchronized(peers) { peers.values.toList() })

        fun stop() {
            listener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
            listener = null
        }
    }
}

package fr.codetics.ev_server.core

import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress
import org.springframework.stereotype.Component

class ProxyTrust(ranges: String) {
    private val networks = ranges.split(',').map(String::trim).filter(String::isNotBlank).map(::Network)

    val configured: Boolean
        get() = networks.isNotEmpty()

    fun trusted(address: InetAddress?): Boolean = address != null && networks.any { it.contains(address) }

    fun clientAddress(peer: InetAddress?, forwardedFor: List<String>): String? {
        if (peer == null) return null
        if (!trusted(peer)) return peer.hostAddress
        val hops = forwardedFor.flatMap { it.split(',') }.mapNotNull(::hop)
        return hops.lastOrNull { !trusted(it) }?.hostAddress ?: hops.firstOrNull()?.hostAddress ?: peer.hostAddress
    }

    private fun hop(raw: String): InetAddress? {
        val text = raw.trim().removePrefix("for=").trim('"')
        if (text.isEmpty()) return null
        val host =
            when {
                text.startsWith("[") -> text.substringAfter('[').substringBefore(']')
                text.count { it == ':' } == 1 -> text.substringBefore(':')
                else -> text
            }
        return runCatching { InetAddress.ofLiteral(host) }.getOrNull()
    }

    private class Network(text: String) {
        private val network: ByteArray
        private val bits: Int

        init {
            val address = InetAddress.ofLiteral(text.substringBefore('/'))
            network = address.address
            bits = text.substringAfter('/', (network.size * 8).toString()).toInt()
            require(bits in 0..network.size * 8) { "Plage d’adresses invalide : $text" }
        }

        fun contains(address: InetAddress): Boolean {
            val candidate = address.address
            if (candidate.size != network.size) return false
            for (index in 0 until bits) {
                val mask = 0x80 ushr (index % 8)
                if ((candidate[index / 8].toInt() and mask) != (network[index / 8].toInt() and mask)) return false
            }
            return true
        }
    }
}

@Component
class ClientAddresses(properties: AppProperties) {
    private val proxies = ProxyTrust(properties.trustedProxies)

    fun of(request: HttpServletRequest): String {
        val peer = runCatching { InetAddress.ofLiteral(request.remoteAddr) }.getOrNull()
        return proxies.clientAddress(peer, request.getHeaders("X-Forwarded-For").toList()) ?: request.remoteAddr
    }
}

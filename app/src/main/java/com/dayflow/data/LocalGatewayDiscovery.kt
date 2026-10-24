package com.dayflow.data

import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

/**
 * Finds an OpenAI-compatible gateway running on this very phone.
 *
 * OmniRoute in Termux is reachable over loopback, so it needs no Wi-Fi, no LAN address and no
 * router — but only if the app knows the port. Rather than making the user hunt for it, the app can
 * ask each common loopback port for its model list and take the first one that answers.
 */
object LocalGatewayDiscovery {

    /** Ports a self-hosted OpenAI-compatible server is likely to be listening on. */
    val COMMON_PORTS = listOf(20128, 8080, 8000, 4000, 1234, 5000, 3000, 8888)

    /** Loopback spellings first: they work with no network at all. */
    val LOOPBACK_HOSTS = listOf("127.0.0.1", "localhost")

    internal fun candidates(hosts: List<String>, ports: List<Int> = COMMON_PORTS): List<String> =
        hosts.flatMap { host -> ports.map { port -> "http://$host:$port" } }.distinct()

    /** Every address to try, with loopback ahead of the LAN addresses so the fast path is first. */
    internal fun orderedCandidates(lanAddresses: List<String>, ports: List<Int> = COMMON_PORTS): List<String> =
        candidates(LOOPBACK_HOSTS + lanAddresses, ports)

    /** Private IPv4 addresses of this device, so a gateway bound to the LAN interface is still found. */
    fun lanAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network -> network.inetAddresses.toList() }
            .filter { address -> address.address.size == 4 && !address.isLoopbackAddress && address.isSiteLocalAddress }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .filter(String::isNotEmpty)
    }.getOrDefault(emptyList())

    /**
     * True when an OpenAI-compatible model list answers. A refusal or a timeout is a normal answer
     * here: it just means nothing is listening on that port.
     */
    fun probe(baseUrl: String, apiKey: String, timeoutMillis: Int = 400): Boolean = runCatching {
        val connection = (URL("${baseUrl.trimEnd('/')}/v1/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** The first base URL that answers, or null when nothing local is listening. */
    fun discover(
        apiKey: String,
        loopbackTimeoutMillis: Int = 400,
        lanTimeoutMillis: Int = 250
    ): String? {
        val loopback = orderedCandidates(emptyList())
        loopback.firstOrNull { probe(it, apiKey, loopbackTimeoutMillis) }?.let { return it }
        // LAN addresses are tried last and with a shorter timeout: an address on a network that no
        // longer exists would otherwise stall the search for seconds.
        val lan = candidates(lanAddresses())
        return lan.firstOrNull { probe(it, apiKey, lanTimeoutMillis) }
    }
}

package com.alexloi.pdnsswitcher.probe

import android.net.Network
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random

object DnsProbe {

    private const val TAG = "DnsProbe"
    private const val TIMEOUT_MS = 2000

    fun probe(serverIp: String, domain: String, network: Network? = null): Boolean {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            network?.bindSocket(socket)
            socket.soTimeout = TIMEOUT_MS

            val query = buildQuery(domain)
            val address = parseAddress(serverIp)
            val packet = DatagramPacket(query, query.size, InetSocketAddress(address, 53))
            socket.send(packet)

            val buf = ByteArray(512)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)

            isValidDnsResponse(query, buf, response.length)
        } catch (e: Exception) {
            Log.i(TAG, "Probe to $serverIp failed: ${e.message}")
            false
        } finally {
            socket?.close()
        }
    }

    private fun parseAddress(ip: String): InetAddress {
        val cleaned = ip.trim().removeSurrounding("[", "]")
        return InetAddress.getByName(cleaned)
    }

    private fun buildQuery(domain: String): ByteArray {
        val id = Random.nextInt(0, 0xFFFF)
        val header = byteArrayOf(
            (id shr 8).toByte(), id.toByte(),
            0x01, 0x00,
            0x00, 0x01,
            0x00, 0x00,
            0x00, 0x00,
            0x00, 0x00
        )
        val question = ArrayList<Byte>()
        domain.split(".").forEach { label ->
            question.add(label.length.toByte())
            question.addAll(label.toByteArray(Charsets.US_ASCII).toList())
        }
        question.add(0)
        question.add(0x00); question.add(0x01)
        question.add(0x00); question.add(0x01)
        return header + question.toByteArray()
    }

    private fun isValidDnsResponse(query: ByteArray, response: ByteArray, length: Int): Boolean {
        if (length < 12) return false
        if (response[0] != query[0] || response[1] != query[1]) return false
        val flagsByte1 = response[2].toInt()
        val isResponse = (flagsByte1 and 0x80) != 0
        val rcode = response[3].toInt() and 0x0F
        return isResponse && rcode == 0
    }
}

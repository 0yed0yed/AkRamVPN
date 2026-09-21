package com.akram.vpn

data class PacketInfo(
    val number: Int,
    val protocol: String,      // "TCP" / "UDP"
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val size: Int,
    val payloadSize: Int,
    val hex: String,
    val datetime: String
) {
    fun shortDest(): String = "$dstIp:$dstPort"
    fun shortSrc(): String = "$srcIp:$srcPort"
}

package com.akram.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AkRamVpnService : VpnService() {

    companion object {
        const val TAG = "AkRamVPN"
        const val ACTION_START = "com.akram.vpn.START"
        const val ACTION_STOP = "com.akram.vpn.STOP"
        const val CHANNEL_ID = "akramvpn"
        const val NOTIF_ID = 1001

        @Volatile var isRunning = false
            private set

        val packetCount = AtomicLong(0)
        var packetListener: ((PacketInfo) -> Unit)? = null
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val VPN_MTU = 1500

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (running.get()) return

        try {
            // بناء الـ VPN interface
            val builder = Builder()
                .setSession("AkRamVPN")
                .setMtu(VPN_MTU)
                .addAddress("10.8.0.1", 32)
                .addRoute("0.0.0.0", 0)         // كل الترافيك
                .addRoute("::", 0)              // IPv6
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")

            // استثني التطبيق نفسه
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN establish failed")
                return
            }

            running.set(true)
            isRunning = true
            startForegroundNotification()

            // ابدأ pump thread
            Thread { pump() }.start()

            Log.i(TAG, "✓ VPN started")
        } catch (e: Exception) {
            Log.e(TAG, "startVpn: ${e.message}")
        }
    }

    private fun stopVpn() {
        running.set(false)
        isRunning = false
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private fun pump() {
        val input = FileInputStream(vpnInterface?.fileDescriptor ?: return)
        val buffer = ByteArray(VPN_MTU)

        while (running.get()) {
            try {
                val length = input.read(buffer)
                if (length <= 0) continue

                val count = packetCount.incrementAndGet()

                // تحليل الباكيت
                val packet = buffer.copyOf(length)
                processPacket(packet, count)

            } catch (e: Exception) {
                if (running.get()) Log.e(TAG, "pump: ${e.message}")
                break
            }
        }
    }

    private fun processPacket(packet: ByteArray, count: Long) {
        try {
            // IPv4
            if (packet.size < 20) return
            val version = (packet[0].toInt() and 0xF0) shr 4
            if (version != 4) return

            val headerLen = (packet[0].toInt() and 0x0F) * 4
            val protocol = packet[9].toInt() and 0xFF
            val srcIp = "${packet[12].toInt() and 0xFF}.${packet[13].toInt() and 0xFF}.${packet[14].toInt() and 0xFF}.${packet[15].toInt() and 0xFF}"
            val dstIp = "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}.${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"

            when (protocol) {
                6 -> {  // TCP
                    if (packet.size < headerLen + 20) return
                    val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or
                                  (packet[headerLen + 3].toInt() and 0xFF)
                    val srcPort = ((packet[headerLen].toInt() and 0xFF) shl 8) or
                                  (packet[headerLen + 1].toInt() and 0xFF)
                    val payloadOffset = headerLen + ((packet[headerLen + 12].toInt() and 0xF0) shr 2)
                    val payloadSize = packet.size - payloadOffset

                    if (payloadSize > 0) {
                        val payload = packet.copyOfRange(payloadOffset, packet.size)
                        val info = PacketInfo(
                            number = count.toInt(),
                            protocol = "TCP",
                            srcIp = srcIp,
                            srcPort = srcPort,
                            dstIp = dstIp,
                            dstPort = dstPort,
                            size = packet.size,
                            payloadSize = payloadSize,
                            hex = bytesToHex(payload),
                            datetime = java.text.SimpleDateFormat("HH:mm:ss",
                                java.util.Locale.US).format(java.util.Date())
                        )
                        packetListener?.invoke(info)
                    }
                }

                17 -> {  // UDP
                    if (packet.size < headerLen + 8) return
                    val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or
                                  (packet[headerLen + 3].toInt() and 0xFF)
                    val srcPort = ((packet[headerLen].toInt() and 0xFF) shl 8) or
                                  (packet[headerLen + 1].toInt() and 0xFF)
                    val payloadOffset = headerLen + 8
                    val payloadSize = packet.size - payloadOffset

                    if (payloadSize > 0) {
                        val payload = packet.copyOfRange(payloadOffset, packet.size)
                        val info = PacketInfo(
                            number = count.toInt(),
                            protocol = "UDP",
                            srcIp = srcIp,
                            srcPort = srcPort,
                            dstIp = dstIp,
                            dstPort = dstPort,
                            size = packet.size,
                            payloadSize = payloadSize,
                            hex = bytesToHex(payload),
                            datetime = java.text.SimpleDateFormat("HH:mm:ss",
                                java.util.Locale.US).format(java.util.Date())
                        )
                        packetListener?.invoke(info)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processPacket: ${e.message}")
        }
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val chan = NotificationChannel(CHANNEL_ID, "AkRamVPN",
                    NotificationManager.IMPORTANCE_LOW)
                mgr.createNotificationChannel(chan)
            }
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val openPending = PendingIntent.getActivity(this, 0, openIntent, flags)

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AkRamVPN يعمل")
            .setContentText("يلتقط الترافيك · ${packetCount.get()} باكيت")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder()
        val max = minOf(b.size, 256)
        for (i in 0 until max) sb.append(String.format("%02x", b[i]))
        if (b.size > 256) sb.append("...")
        return sb.toString()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}

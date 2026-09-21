package com.akram.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.akram.vpn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PacketAdapter
    private val packets = mutableListOf<PacketInfo>()
    private var tcpCount = 0
    private var udpCount = 0

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission مرفوضة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PacketAdapter(packets) { packet ->
            showPacketDialog(packet)
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        AkRamVpnService.packetListener = { info ->
            runOnUiThread {
                packets.add(0, info)
                if (packets.size > 500) packets.removeAt(packets.size - 1)

                if (info.protocol == "TCP") tcpCount++ else udpCount++

                adapter.notifyDataSetChanged()
                updateUi()
            }
        }

        binding.toggleBtn.setOnClickListener {
            if (AkRamVpnService.isRunning) stopVpn() else requestVpn()
        }

        updateUi()
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermission.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, AkRamVpnService::class.java).apply {
            action = AkRamVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.toggleBtn.postDelayed({ updateUi() }, 500)
    }

    private fun stopVpn() {
        val intent = Intent(this, AkRamVpnService::class.java).apply {
            action = AkRamVpnService.ACTION_STOP
        }
        startService(intent)
        binding.toggleBtn.postDelayed({ updateUi() }, 500)
    }

    private fun updateUi() {
        val running = AkRamVpnService.isRunning
        binding.statusLabel.text = if (running) "RUNNING" else "READY"
        binding.statusLabel.setTextColor(
            if (running) 0xFF4ADE80.toInt() else 0xFF64748B.toInt()
        )
        binding.statusDot.background = getDrawable(
            if (running) R.drawable.dot_green else R.drawable.dot_red
        )
        binding.toggleBtn.text = if (running) "STOP VPN" else "START VPN"
        binding.toggleBtn.background = getDrawable(
            if (running) R.drawable.bg_btn_danger else R.drawable.bg_btn_success
        )

        binding.packetCount.text = packets.size.toString()
        binding.tcpCount.text = tcpCount.toString()
        binding.udpCount.text = udpCount.toString()
    }

    private fun showPacketDialog(p: PacketInfo) {
        val msg = buildString {
            append("Protocol: ${p.protocol}\n")
            append("From: ${p.shortSrc()}\n")
            append("To: ${p.shortDest()}\n")
            append("Size: ${p.size} bytes\n")
            append("Payload: ${p.payloadSize} bytes\n\n")
            append("HEX:\n${p.hex.take(500)}")
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("#${p.number} · ${p.protocol}")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }
}

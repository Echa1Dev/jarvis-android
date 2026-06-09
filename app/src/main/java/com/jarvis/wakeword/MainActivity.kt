package com.jarvis.wakeword

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.wakeword.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            requestPermsAndStart()
        }
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, WakeWordService::class.java))
            binding.statusText.text = "Detenido"
        }

        // Arrancar automáticamente si tiene permisos
        if (hasAllPerms()) startService()
    }

    private fun requestPermsAndStart() {
        val missing = PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startService()
        else ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (results.all { it == PackageManager.PERMISSION_GRANTED }) startService()
    }

    private fun startService() {
        ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
        binding.statusText.text = "Escuchando — di \"Jarvis\""
    }

    private fun hasAllPerms() = PERMS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

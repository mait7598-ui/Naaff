package com.example.databudget

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etTotal = findViewById<EditText>(R.id.etTotal)
        val etDays = findViewById<EditText>(R.id.etDays)
        val etAgo = findViewById<EditText>(R.id.etAgo)
        status = findViewById(R.id.tvStatus)

        if (Budget.hasConfig(this)) {
            etTotal.setText(Budget.totalGb(this).toString())
            etDays.setText(Budget.days(this).toString())
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val gb = etTotal.text.toString().toDoubleOrNull()
            val days = etDays.text.toString().toIntOrNull()
            val ago = etAgo.text.toString().toIntOrNull() ?: 0
            if (gb == null || gb <= 0 || days == null || days <= 0) {
                Toast.makeText(this, "دخل أرقام صحيحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Budget.save(this, gb, days, System.currentTimeMillis() - ago * Budget.DAY)
            Budget.schedule(this)
            status.text = Budget.check(this, alert = false)
        }

        findViewById<Button>(R.id.btnCheck).setOnClickListener {
            status.text = Budget.check(this, alert = true)
        }
    }

    override fun onResume() {
        super.onResume()
        status.text = Budget.check(this, alert = false)
    }
}

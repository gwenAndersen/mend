package com.mend

import android.app.Activity
import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class MendSettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var layoutJsonTextView: TextView
    private lateinit var wallpaperScheduleList: RecyclerView
    private lateinit var adapter: WallpaperScheduleAdapter
    private lateinit var activeWldFileTextView: TextView
    
    private val schedules = mutableListOf<WallpaperSchedule>()
    private val gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .create()

    private var mHour: Int = 0
    private var mMinute: Int = 0
    private var editingPosition: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("mend_prefs", Context.MODE_PRIVATE)

        activeWldFileTextView = findViewById(R.id.active_wld_file_textview)
        val savedWld = sharedPreferences.getString("active_wld_file", "world1.wld")
        activeWldFileTextView.text = savedWld

        // Click on the text or a button to change .wld file
        activeWldFileTextView.setOnClickListener {
            showWldFilePicker()
        }

        loadSchedules()

        wallpaperScheduleList = findViewById(R.id.wallpaper_schedule_list)
        wallpaperScheduleList.layoutManager = LinearLayoutManager(this)
        adapter = WallpaperScheduleAdapter(schedules, { position ->
            schedules.removeAt(position)
            adapter.notifyItemRemoved(position)
            saveSchedules()
        }) { position ->
            editWallpaperSchedule(position)
        }
        wallpaperScheduleList.adapter = adapter

        val callback = ItemMoveCallback(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(wallpaperScheduleList)

        findViewById<MaterialButton>(R.id.add_wallpaper_schedule_button).setOnClickListener { addWallpaperSchedule() }
        
        findViewById<MaterialButton>(R.id.set_3d_wallpaper_button).setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, Live3dWallpaperService::class.java))
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.set_plains_wallpaper_button).setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, PlainsWallpaperService::class.java))
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.set_particles_wallpaper_button).setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, NightSkyWallpaperService::class.java))
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.set_scheduled_wallpaper_button).setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, ScheduledImageWallpaperService::class.java))
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.preview_scheduled_wallpaper_button).setOnClickListener {
            val intent = Intent(this, WallpaperPreviewActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.open_data_map_button).setOnClickListener {
            val intent = Intent(this, DataMapActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.capture_layout_button).setOnClickListener {
            val intent = Intent(MendAccessibilityService.ACTION_CAPTURE_LAYOUT)
            sendBroadcast(intent)
            Toast.makeText(this, "Requesting layout capture...", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.copy_json_button).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Captured Layout", layoutJsonTextView.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        layoutJsonTextView = findViewById(R.id.layout_json_textview)
        SharedViewModel.capturedLayoutJson.observe(this) {
            layoutJsonTextView.text = "Captured Layout:\n" + (it ?: "No layout captured")
            Toast.makeText(this, "Layout captured!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addWallpaperSchedule() {
        val calendar = Calendar.getInstance()
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                mHour = hourOfDay
                mMinute = minute
                openFilePicker()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val selectedImageUri: Uri? = data?.data
            selectedImageUri?.let {
                val mimeType = contentResolver.getType(it)
                if (mimeType?.startsWith("image/") == true) {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    schedules.add(WallpaperSchedule(it, mHour, mMinute))
                    adapter.notifyDataSetChanged()
                    saveSchedules()
                } else {
                    Toast.makeText(this, "Please select a valid image file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        pickImageLauncher.launch(intent)
    }

    private fun editWallpaperSchedule(position: Int) {
        val schedule = schedules[position]
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
        calendar.set(Calendar.MINUTE, schedule.minute)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                mHour = hourOfDay
                mMinute = minute
                openFilePickerForEdit(position)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun openFilePickerForEdit(position: Int) {
        editingPosition = position
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        pickImageLauncherForEdit.launch(intent)
    }

    private val pickImageLauncherForEdit = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val selectedImageUri: Uri? = data?.data
            selectedImageUri?.let {
                val mimeType = contentResolver.getType(it)
                if (mimeType?.startsWith("image/") == true) {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (editingPosition != -1) {
                        schedules[editingPosition] = WallpaperSchedule(it, mHour, mMinute)
                        adapter.notifyItemChanged(editingPosition)
                        saveSchedules()
                        editingPosition = -1
                    }
                } else {
                    Toast.makeText(this, "Please select a valid image file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveSchedules() {
        WallpaperUtils.saveSchedules(this, schedules)
    }

    private fun loadSchedules() {
        schedules.clear()
        schedules.addAll(WallpaperUtils.loadSchedules(this))
    }

    private fun showWldFilePicker() {
        val assets = assets.list("")?.filter { it.endsWith(".wld") }?.toTypedArray() ?: emptyArray()
        if (assets.isEmpty()) {
            Toast.makeText(this, "No .wld files found in assets", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select .wld file")
            .setItems(assets) { _, which ->
                val selected = assets[which]
                sharedPreferences.edit().putString("active_wld_file", selected).apply()
                activeWldFileTextView.text = selected
                Toast.makeText(this, "Selected: $selected. Apply wallpaper to update.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}

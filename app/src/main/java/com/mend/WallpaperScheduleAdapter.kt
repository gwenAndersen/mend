package com.mend

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException

class WallpaperScheduleAdapter(
    private val schedules: MutableList<WallpaperSchedule>,
    private val onDeleteClickListener: (Int) -> Unit,
    private val onEditClickListener: (Int) -> Unit
) : RecyclerView.Adapter<WallpaperScheduleAdapter.ViewHolder>(), ItemMoveCallback.ItemMoveAdapter {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wallpaper_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = schedules[position]
        
        holder.scheduleTimeTextView.text = String.format("%02d:%02d", schedule.hour, schedule.minute)
        holder.scheduleImageNameTextView.text = getFileName(holder.itemView.context.contentResolver, schedule.uri) ?: "Unknown File"
        
        // Load Thumbnail
        try {
            val thumbnail: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                holder.itemView.context.contentResolver.loadThumbnail(schedule.uri, Size(200, 200), null)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(holder.itemView.context.contentResolver, schedule.uri)
            }
            holder.scheduleImageView.setImageBitmap(thumbnail)
        } catch (e: Exception) {
            holder.scheduleImageView.setImageResource(android.R.drawable.ic_menu_gallery)
            e.printStackTrace()
        }

        holder.deleteButton.setOnClickListener { onDeleteClickListener(position) }
        holder.editButton.setOnClickListener { onEditClickListener(position) }
    }

    private fun getFileName(contentResolver: android.content.ContentResolver, uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    override fun getItemCount(): Int {
        return schedules.size
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        val fromSchedule = schedules[fromPosition]
        schedules.removeAt(fromPosition)
        schedules.add(toPosition, fromSchedule)
        notifyItemMoved(fromPosition, toPosition)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val scheduleImageView: ImageView = itemView.findViewById(R.id.schedule_image_view)
        val scheduleTimeTextView: TextView = itemView.findViewById(R.id.schedule_time_textview)
        val scheduleImageNameTextView: TextView = itemView.findViewById(R.id.schedule_image_name_textview)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_schedule_button)
        val editButton: ImageButton = itemView.findViewById(R.id.edit_schedule_button)
    }
}

package com.example.intuch

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider

class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val contact: ConstraintLayout = itemView.findViewById(R.id.contact)
    val name: TextView = itemView.findViewById(R.id.name)
    val lastContacted: TextView = itemView.findViewById(R.id.last_contacted)
    val notify: SwitchCompat = itemView.findViewById(R.id.notify)
    val extras: ConstraintLayout = itemView.findViewById(R.id.extras)
    val notifySlider: Slider = itemView.findViewById(R.id.notify_slider)
    val actionButton: Button = itemView.findViewById(R.id.action_button)
}
package com.example.lecturerapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttendeesAdapter(
    private val context: Context,
    private val attendeesList: List<String>,
    private val openChat: (String) -> Unit // Lambda function for opening chat
) : RecyclerView.Adapter<AttendeesAdapter.AttendeeViewHolder>() {

    inner class AttendeeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val attendeeName: TextView = itemView.findViewById(R.id.attendee_name)
        private val chatButton: Button = itemView.findViewById(R.id.chat_button)

        fun bind(attendee: String) {
            attendeeName.text = attendee
            chatButton.setOnClickListener {
                openChat(attendee) // Call the openChat function passed as a parameter
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendeeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendee, parent, false) // Use your custom layout
        return AttendeeViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendeeViewHolder, position: Int) {
        holder.bind(attendeesList[position])
    }

    override fun getItemCount(): Int = attendeesList.size
}

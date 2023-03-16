package com.example.intuch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import org.ocpsoft.prettytime.PrettyTime
import java.time.Duration


// In charge of structuring the contact list
class ContactAdapter(private val context: Context, private val layoutManager: LinearLayoutManager, private val contactManager: ContactManager) :
    RecyclerView.Adapter<ContactViewHolder>() {

    companion object {
        private const val TRANSITION_TIME = 250
    }

    // Used to display nice-looking time info
    private val prettyTime = PrettyTime()

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ContactViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.contact_view, viewGroup, false)

        val viewHolder = ContactViewHolder(view)

        viewHolder.notifySlider.setLabelFormatter { value: Float ->
            "Reach out every ${value.toInt()} days"
        }

        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: ContactViewHolder, position: Int) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        val contact = contactManager.contacts[position]

        // Set name
        viewHolder.name.text = contact.name
        // Set slider initial position
        viewHolder.notifySlider.value = contact.notifyDuration.toDays().toInt().toFloat()

        // Set enabled-ness
        updateEnabledCheckbox(contact.notify, viewHolder)
        // Set expanded-ness
        updateViewExpanded(contact.expanded, viewHolder)
        // Set notify-ness
        updateNotifyView(contact, viewHolder)

        // Attach listener to toggle
        viewHolder.notify.setOnClickListener {
            // Toggle value in the contact, and re-sort
            contact.notify = viewHolder.notify.isChecked
            updateEnabledCheckbox(contact.notify, viewHolder)
            updateNotifyDurationItems(contact, viewHolder)
        }

        // Attach listener to the slider
        viewHolder.notifySlider.addOnSliderTouchListener(
            @SuppressLint("RestrictedApi")
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                // Runs when slider is released
                override fun onStopTrackingTouch(slider: Slider) {
                    // Set new notify duration, and re-sort
                    contact.notifyDuration = Duration.ofDays(slider.value.toLong())
                    updateNotifyDurationItems(contact, viewHolder)
                }
            }
        )

        // Add click listener to the reach out button
        viewHolder.actionButton.setOnClickListener {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", contact.numbers[0], null)))
        }

        // Add a click listener to the whole view, that brings up texting window
        viewHolder.contact.setOnClickListener {
            contact.expanded = !contact.expanded
            updateViewExpanded(contact.expanded, viewHolder, contact)
        }
    }

    override fun onViewRecycled(viewHolder: ContactViewHolder) {
        super.onViewRecycled(viewHolder)

        // Remove all slider listeners when view is recycled
        viewHolder.notifySlider.clearOnSliderTouchListeners()
    }

    // Should be called when contact has variables changed that could change it's sort position
    private fun updateNotifyDurationItems(contact: Contact, viewHolder: ContactViewHolder) {
        // Update sort
        val oldIndex = contactManager.contacts.indexOf(contact)
        contactManager.sort()

        // Save scroll state
        val recyclerViewState = layoutManager.onSaveInstanceState()
        // Move item
        notifyItemMoved(oldIndex, contactManager.contacts.indexOf(contact))
        // Restore state
        layoutManager.onRestoreInstanceState(recyclerViewState);

        // Update notify
        updateNotifyView(contact, viewHolder)
    }

    // Update reach out visualization
    private fun updateNotifyView(contact: Contact, viewHolder: ContactViewHolder) {
        // Get whether or not to notify
        val reachOut = contact.notify && contactManager.notifyPercent(contact) >= 1
        // Update text
        viewHolder.lastContacted.text = when {
            (reachOut) -> context.getString(R.string.reach_out_text)
            (contact.instant != null) -> prettyTime.format(contact.instant).replaceFirstChar(Char::titlecase)
            else -> context.getString(R.string.unknown_time_text)
        }
        // Update background
        (viewHolder.contact.background as TransitionDrawable).let {
            when {
                (reachOut && !contact.reachOut) -> it.startTransition(TRANSITION_TIME)
                (!reachOut && contact.reachOut) -> it.reverseTransition(TRANSITION_TIME)
                (reachOut) -> it.startTransition(0)
                else -> it.resetTransition()
            }
        }
        // Save value in contact
        contact.reachOut = contact.notify && contactManager.notifyPercent(contact) >= 1
    }

    // Expands or minimizes the view
    private fun updateViewExpanded(expanded: Boolean, viewHolder: ContactViewHolder, contact: Contact? = null) {
        viewHolder.extras.visibility = if (expanded) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (contact != null) {
            notifyItemChanged(contactManager.contacts.indexOf(contact))
        }
    }

    // Style view as enabled or not
    private fun updateEnabledCheckbox(enabled: Boolean, viewHolder: ContactViewHolder) {
        // Change style
        viewHolder.notify.isChecked = enabled
        viewHolder.name.isEnabled = enabled
        viewHolder.lastContacted.isEnabled = enabled
    }

    override fun getItemCount() = contactManager.contacts.size
}
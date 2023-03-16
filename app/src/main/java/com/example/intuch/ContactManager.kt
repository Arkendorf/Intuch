package com.example.intuch

import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import kotlin.String
import kotlin.arrayOf
import kotlin.let
import kotlin.to

// Manages the list of contacts
class ContactManager(registryOwner: SavedStateRegistryOwner? = null) :
    SavedStateRegistry.SavedStateProvider {
    companion object {
        private const val PROVIDER = "search_manager"
        private const val CONTACTS = "contacts"
        private const val SAVE_FILE_NAME = "contacts.json"

        private const val DEFAULT_DURATION: Long = 14

        private const val KEY_NOTIFY = "notify"
        private const val KEY_NOTIFY_DURATION = "notifyDuration"
        private const val KEY_LAST_NOTIFIED = "lastNotified"
    }

    var contacts = mutableListOf<Contact>() // List of contacts to display
    private var numberMap = mutableMapOf<String, Int>() // Maps phone numbers to contact indices

    init {
        // Register a LifecycleObserver for when the Lifecycle hits ON_CREATE
        registryOwner?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_CREATE) {
                val registry = registryOwner.savedStateRegistry

                // Register this object for future calls to saveState()
                registry.registerSavedStateProvider(PROVIDER, this)

                // Get the previously saved state and restore it
                val state = registry.consumeRestoredStateForKey(PROVIDER)

                // Apply the previously saved state
                state?.getParcelableArray(CONTACTS)?.map {it as Contact} ?.toMutableList()?.let {
                    contacts = it
                }
            }
        })
    }

    // Saves state between application pauses
    override fun saveState(): Bundle {
        return bundleOf(
            CONTACTS to contacts,
        )
    }

    fun saveContactData(context: Context) {
        val gson = Gson()
        val saveObject = JsonObject()
        for (contact in contacts) {
            // Construct save object for this specific contact
            val contactSaveObject = JsonObject()
            contactSaveObject.add(KEY_NOTIFY, gson.toJsonTree(contact.notify))
            contactSaveObject.add(KEY_NOTIFY_DURATION, gson.toJsonTree(contact.notifyDuration.toDays().toInt()))
            contactSaveObject.add(KEY_LAST_NOTIFIED, gson.toJsonTree(contact.lastNotified.toString()))

            // Add it to overall save object
            saveObject.add(contact.name, contactSaveObject)
        }

        // Write save object to file
        context.openFileOutput(SAVE_FILE_NAME, Context.MODE_PRIVATE).use {
            it.write(gson.toJson(saveObject).toByteArray())
        }
    }

    // Loads list of contacts
    fun loadContacts(context: Context) {
        contacts.clear()

        // Get list of contacts
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        )?.let { cursor ->
            while (cursor.moveToNext()) {
                // Get the contact's name
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val name = cursor.getString(nameIndex)

                if (name != null) {

                    // Get the contact's id
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val id = cursor.getString(idIndex)

                    val numbers = mutableListOf<String>()

                    // Create a new cursor to query for the contact's phone number
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf<String>(id),
                        null
                    )?.let { numberCursor ->
                        while (numberCursor.moveToNext()) {
                            // Get the contact's phone number
                            val numberIndex =
                                numberCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val number = sanitizeNumber(numberCursor.getString(numberIndex))
                            // Set number to map to the proper contact index
                            if (!numbers.contains(number)) {
                                numberMap[number] = contacts.size
                                numbers.add(number)
                            }
                        }
                        // Close the cursor
                        numberCursor.close()
                    }

                    // Create the contact with the given name and the list of numbers,
                    // If the contact has at least 1 number
                    if (numbers.size > 0) {
                        contacts.add(Contact(name, numbers.toTypedArray(), Duration.ofDays(DEFAULT_DURATION)))
                    }
                }
            }
            cursor.close()
        }

        // Fill out remaining info
        loadSavedContactData(context)
        getContactLastSMSDates(context)
        getStartExpanded()
    }

    // Populates the "notify" field of all the contacts in contacts
    // Reads locally saved info, so this data persists across sessions
    private fun loadSavedContactData(context: Context) {
        try {
            context.openFileInput(SAVE_FILE_NAME).bufferedReader().use {
                val saveObject = Gson().fromJson(it, JsonObject::class.java)
                // Read save data for the given contact
                for (contact in contacts) {
                    saveObject.get(contact.name)?.asJsonObject?.let { contactSaveObject ->
                        if (contactSaveObject.has(KEY_NOTIFY)) {
                            contact.notify = contactSaveObject.get(KEY_NOTIFY)?.asBoolean ?: false
                        }
                        if (contactSaveObject.has(KEY_NOTIFY_DURATION)) {
                            contact.notifyDuration = Duration.ofDays(
                                contactSaveObject.get(KEY_NOTIFY_DURATION)?.asInt?.toLong() ?: 0)
                        }
                        if (contactSaveObject.has(KEY_LAST_NOTIFIED)) {
                            contact.lastNotified = Instant.parse(
                                contactSaveObject.get(KEY_LAST_NOTIFIED).asString)
                        }
                    }
                }
            }
        }
        catch (e: FileNotFoundException) {
        }
    }

    // Populates the "instant" field of all the contacts in contacts
    // Reads SMSs, and gets the date of last contact
    private fun getContactLastSMSDates(context: Context) {
        // Query texts
        context.contentResolver.query(
            Telephony.Sms.Sent.CONTENT_URI,
            arrayOf(
                Telephony.Sms.Sent.DATE,
                Telephony.Sms.Sent.ADDRESS,
            ),
            null,
            null,
            "${Telephony.Sms.Sent.DATE} ASC"
        )?.let { cursor ->
            // Iterate through texts
            while (cursor.moveToNext()) {
                // Get the number this text belongs to
                val number = sanitizeNumber(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Sent.ADDRESS)))
                // Get the instant this text was sent
                val instant = Instant.ofEpochMilli(
                    cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.Sent.DATE)).toLong())

                // Since we are sorting so that more recent texts are last, every instant will be
                // closer to the present than the previous one. Therefore we can keep setting
                // the contact's instant field, and the last instant will be the most recent
                numberMap[number]?.let {
                    contacts[it].instant = instant
                }
            }

            cursor.close()
        }
    }

    // Gets and sets whether contacts should start expanded
    private fun getStartExpanded() {
        for (contact in contacts) {
            contact.expanded = contact.notify && notifyPercent(contact) >= 1
        }
    }

    // Sanitizes phone numbers so they all have the same format
    private fun sanitizeNumber(number: String): String {
        return number.replace(Regex("[+() -]"), "")
    }

    // Sorts the contacts so that contacts with notify = true are at the top
    // Within contacts with notify = true, the most recently messaged are at the bottom
    // Within contacts with notify = false, the most recently messaged are at the top
    fun sort() {
        contacts.sortWith { contact1, contact2 ->
            when {
                (!contact1.notify && !contact2.notify) ->
                    notifyPercent(contact1).compareTo(notifyPercent(contact2))
                (contact1.notify && contact2.notify) ->
                    notifyPercent(contact2).compareTo(notifyPercent(contact1))
                (contact1.notify) -> -1
                (contact2.notify) -> 1
                else -> 0
            }
        }
    }

    // Greater than or equal to 1, contact should be notified
    // Returns percentage of notify duration that has elapsed
    fun notifyPercent(contact: Contact): Float {
        return Duration.between(contact.instant ?: Instant.MIN, Instant.now()).toHours().toFloat() / contact.notifyDuration.toHours().toFloat()
    }
}
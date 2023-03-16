package com.example.intuch

import android.os.Parcel
import android.os.Parcelable
import java.time.Duration
import java.time.Instant

// Class containing contact info
// Parcelable so it can be serialized
class Contact(
    val name: String,
    val numbers: Array<String>,
    var notifyDuration: Duration,
    var instant: Instant? = null,
    var notify: Boolean = false,
    var lastNotified: Instant = Instant.MIN,
    var expanded: Boolean = false,
    var reachOut: Boolean = false,

): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.createStringArray() ?: arrayOf(),
        Duration.ofDays(parcel.readInt().toLong()),
        Instant.parse(parcel.readString()),
        parcel.readByte() != 0.toByte(),
        Instant.parse(parcel.readString()),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeStringArray(numbers)
        parcel.writeInt(notifyDuration.toDays().toInt())
        parcel.writeString(instant.toString())
        parcel.writeByte(if (notify) 1 else 0)
        parcel.writeString(instant.toString())
        parcel.writeByte(if (expanded) 1 else 0)
        parcel.writeByte(if (reachOut) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Contact> {
        override fun createFromParcel(parcel: Parcel): Contact {
            return Contact(parcel)
        }

        override fun newArray(size: Int): Array<Contact?> {
            return arrayOfNulls(size)
        }
    }
}
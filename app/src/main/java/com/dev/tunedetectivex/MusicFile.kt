package com.dev.tunedetectivex


import android.os.Parcel
import android.os.Parcelable

data class MusicFile(
    val fileName: String,
    val status: String,
    val coverUrl: String?
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(fileName)
        parcel.writeString(status)
        parcel.writeString(coverUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MusicFile> {
        override fun createFromParcel(parcel: Parcel): MusicFile {
            return MusicFile(parcel)
        }

        override fun newArray(size: Int): Array<MusicFile?> {
            return arrayOfNulls(size)
        }
    }
}
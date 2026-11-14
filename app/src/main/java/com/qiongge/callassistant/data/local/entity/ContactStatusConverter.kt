package com.qiongge.callassistant.data.local.entity

import androidx.room.TypeConverter

class ContactStatusConverter {
    @TypeConverter
    fun fromContactStatus(value: ContactStatus): String = value.name

    @TypeConverter
    fun toContactStatus(value: String): ContactStatus =
        runCatching { ContactStatus.valueOf(value) }
            .getOrDefault(ContactStatus.NOT_CONTACTED)
}

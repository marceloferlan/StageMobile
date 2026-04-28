package com.marceloferlan.stagemobile.domain.model

import com.google.firebase.firestore.DocumentId

data class SoundFontMetadata(
    @DocumentId
    val id: String = "",
    val fileName: String = "",
    val tags: List<String> = emptyList(),
    val isSystem: Boolean = false,
    val addedDate: Long = System.currentTimeMillis()
)

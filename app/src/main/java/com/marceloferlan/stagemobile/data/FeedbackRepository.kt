package com.marceloferlan.stagemobile.data

import com.google.firebase.firestore.FirebaseFirestore
import com.marceloferlan.stagemobile.domain.model.FeedbackReport
import kotlinx.coroutines.tasks.await

class FeedbackRepository {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun submitFeedback(report: FeedbackReport): Result<Unit> {
        return try {
            firestore.collection("feedbacks")
                .add(report)
                .await() // Envia para o cache local imediatamente e faz sync quando houver internet
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Falha ao enviar feedback: ${e.message}"))
        }
    }
}

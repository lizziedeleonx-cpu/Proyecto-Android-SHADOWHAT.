package com.shadowhat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whatsapp_groups")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val inviteLink: String? = null,
    val notes: String? = null,
    val contactCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val message: String,
    val targetGroupIdsJson: String, // JSON list of IDs
    val status: String, // draft, queued, running, paused, completed, cancelled
    val dispatchIntervalMs: Long = 1200L,
    val assistedMode: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "task_queue")
data class TaskQueueEntity(
    @PrimaryKey
    val id: String,
    val campaignId: String,
    val groupId: String,
    val groupName: String,
    val message: String,
    val status: String, // pending, dispatching, sent_verified, failed, skipped
    val attempts: Int = 0,
    val maxAttempts: Int = 3,
    val lastAttemptAt: Long? = null,
    val verifiedAt: Long? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "campaign_history")
data class HistoryEntity(
    @PrimaryKey
    val id: String,
    val campaignId: String,
    val campaignTitle: String,
    val groupId: String,
    val groupName: String,
    val messageSnippet: String,
    val status: String, // sent_verified, failed, cancelled
    val timestamp: Long = System.currentTimeMillis(),
    val attempts: Int = 1,
    val method: String = "whatsapp_business_intent"
)

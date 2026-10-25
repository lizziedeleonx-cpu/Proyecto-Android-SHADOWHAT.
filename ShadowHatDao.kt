package com.shadowhat.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShadowHatDao {
    // Groups
    @Query("SELECT * FROM whatsapp_groups ORDER BY updatedAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM whatsapp_groups ORDER BY updatedAt DESC")
    suspend fun getAllGroupsList(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("DELETE FROM whatsapp_groups WHERE id IN (:ids)")
    suspend fun deleteGroupsByIds(ids: List<String>)

    // Campaigns
    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun getAllCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    suspend fun getAllCampaignsList(): List<CampaignEntity>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: String): CampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: CampaignEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaigns(campaigns: List<CampaignEntity>)

    @Update
    suspend fun updateCampaign(campaign: CampaignEntity)

    // Task Queue
    @Query("SELECT * FROM task_queue ORDER BY rowid ASC")
    fun getAllQueueItems(): Flow<List<TaskQueueEntity>>

    @Query("SELECT * FROM task_queue WHERE campaignId = :campaignId")
    fun getQueueForCampaign(campaignId: String): Flow<List<TaskQueueEntity>>

    @Query("SELECT * FROM task_queue WHERE status = 'pending' ORDER BY rowid ASC")
    suspend fun getPendingTasks(): List<TaskQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<TaskQueueEntity>)

    @Update
    suspend fun updateQueueItem(item: TaskQueueEntity)

    @Query("DELETE FROM task_queue WHERE status = 'sent_verified'")
    suspend fun clearCompletedQueueItems()

    @Query("DELETE FROM task_queue")
    suspend fun clearAllQueueItems()

    // History
    @Query("SELECT * FROM campaign_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM campaign_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryList(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(history: List<HistoryEntity>)

    @Query("DELETE FROM campaign_history")
    suspend fun clearHistory()
}

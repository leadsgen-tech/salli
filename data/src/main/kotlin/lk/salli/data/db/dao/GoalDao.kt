package lk.salli.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import lk.salli.data.db.entities.GoalContributionEntity
import lk.salli.data.db.entities.GoalEntity

@Dao
interface GoalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun goalById(id: Long): GoalEntity?

    @Query("SELECT * FROM goals ORDER BY is_archived ASC, CASE WHEN target_date IS NULL THEN 1 ELSE 0 END, target_date ASC, id ASC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY at DESC, id DESC")
    fun observeContributions(): Flow<List<GoalContributionEntity>>

    @Query("UPDATE goals SET is_archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContribution(contribution: GoalContributionEntity): Long

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalRow(id: Long)

    @Query("DELETE FROM goal_contributions WHERE goal_id = :goalId")
    suspend fun deleteContributionsFor(goalId: Long)

    /** Removes a goal and everything saved toward it. */
    @Transaction
    suspend fun deleteGoal(id: Long) {
        deleteContributionsFor(id)
        deleteGoalRow(id)
    }
}

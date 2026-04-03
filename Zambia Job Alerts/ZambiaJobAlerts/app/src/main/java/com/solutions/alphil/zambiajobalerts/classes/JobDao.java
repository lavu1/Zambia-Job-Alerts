package com.solutions.alphil.zambiajobalerts.classes;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY date DESC")
    LiveData<List<JobEntity>> getAllJobs();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertJobs(List<JobEntity> jobs);

    @Query("DELETE FROM jobs")
    void deleteAllJobs();

    @Query("SELECT * FROM jobs WHERE id = :jobId")
    JobEntity getJobById(int jobId);
    
    @Query("SELECT * FROM jobs WHERE title LIKE '%' || :query || '%' OR company LIKE '%' || :query || '%'")
    LiveData<List<JobEntity>> searchJobs(String query);
}

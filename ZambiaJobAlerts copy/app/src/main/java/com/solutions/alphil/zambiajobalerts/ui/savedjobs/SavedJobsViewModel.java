package com.solutions.alphil.zambiajobalerts.ui.savedjobs;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.solutions.alphil.zambiajobalerts.classes.Job;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SavedJobsViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "saved_jobs_prefs";
    private static final String KEY_SAVED_JOBS = "saved_jobs_list";
    private final SharedPreferences prefs;
    private final Gson gson;

    private final MutableLiveData<List<Job>> _savedJobs = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<Job>> getSavedJobs() {
        return _savedJobs;
    }

    public SavedJobsViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadSavedJobs();
    }

    public void loadSavedJobs() {
        String json = prefs.getString(KEY_SAVED_JOBS, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<Job>>() {}.getType();
            List<Job> jobs = gson.fromJson(json, type);
            _savedJobs.setValue(jobs);
        } else {
            _savedJobs.setValue(new ArrayList<>());
        }
    }

    public void saveJob(Job job) {
        List<Job> currentJobs = _savedJobs.getValue();
        if (currentJobs == null) currentJobs = new ArrayList<>();

        // Check if already saved
        boolean alreadySaved = false;
        for (Job savedJob : currentJobs) {
            if (savedJob.getId() == job.getId()) {
                alreadySaved = true;
                break;
            }
        }

        if (!alreadySaved) {
            currentJobs.add(job);
            saveToPrefs(currentJobs);
            _savedJobs.setValue(currentJobs);
        }
    }

    public void removeJob(int jobId) {
        List<Job> currentJobs = _savedJobs.getValue();
        if (currentJobs != null) {
            currentJobs.removeIf(job -> job.getId() == jobId);
            saveToPrefs(currentJobs);
            _savedJobs.setValue(currentJobs);
        }
    }

    private void saveToPrefs(List<Job> jobs) {
        String json = gson.toJson(jobs);
        prefs.edit().putString(KEY_SAVED_JOBS, json).apply();
    }

    public boolean isJobSaved(int jobId) {
        List<Job> currentJobs = _savedJobs.getValue();
        if (currentJobs != null) {
            for (Job job : currentJobs) {
                if (job.getId() == jobId) return true;
            }
        }
        return false;
    }
}
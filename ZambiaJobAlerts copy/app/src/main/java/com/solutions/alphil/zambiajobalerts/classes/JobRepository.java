package com.solutions.alphil.zambiajobalerts.classes;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class JobRepository {
    private final JobDao jobDao;
    private final LiveData<List<JobEntity>> allJobs;
    private final RequestQueue queue;
    private final Gson gson = new Gson();
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);

    public JobRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        jobDao = db.jobDao();
        allJobs = jobDao.getAllJobs();
        queue = Volley.newRequestQueue(application);
    }

    public LiveData<List<JobEntity>> getAllJobs() {
        return allJobs;
    }

    public LiveData<List<JobEntity>> searchJobs(String query) {
        return jobDao.searchJobs(query);
    }

    public LiveData<Boolean> getIsRefreshing() {
        return isRefreshing;
    }

    public void refreshJobs() {
        if (Boolean.TRUE.equals(isRefreshing.getValue())) return;
        isRefreshing.setValue(true);

        String url = "https://zambiajobalerts.com/wp-json/wp/v2/job-listings?per_page=30&_embed";
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        Type listType = new TypeToken<List<Job>>(){}.getType();
                        List<Job> jobs = gson.fromJson(response, listType);
                        List<JobEntity> entities = new ArrayList<>();
                        for (Job job : jobs) {
                            entities.add(new JobEntity(
                                    job.getId(),
                                    job.getTitle(),
                                    job.getExcerpt(),
                                    job.getContent(),
                                    job.getDate(),
                                    job.getLink(),
                                    job.getFeaturedImage(),
                                    job.getCompany(),
                                    job.getLocation(),
                                    job.getJobType(),
                                    job.getApplication()
                            ));
                        }
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            jobDao.insertJobs(entities);
                            isRefreshing.postValue(false);
                        });
                    } catch (Exception e) {
                        Log.e("JobRepository", "Error parsing jobs: " + e.getMessage());
                        isRefreshing.setValue(false);
                    }
                },
                error -> {
                    Log.e("JobRepository", "Error fetching jobs: " + error.getMessage());
                    isRefreshing.setValue(false);
                });

        queue.add(request);
    }

    public void fetchJobDetails(int jobId, ResponseListener<JobEntity> listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            JobEntity localJob = jobDao.getJobById(jobId);
            if (localJob != null) {
                listener.onResponse(localJob);
            } else {
                String url = "https://zambiajobalerts.com/wp-json/wp/v2/job-listings/" + jobId + "?_embed";
                fetchJobFromUrl(url, listener);
            }
        });
    }

    public void fetchJobDetailsBySlug(String slug, ResponseListener<JobEntity> listener) {
        String url = "https://zambiajobalerts.com/wp-json/wp/v2/job-listings?slug=" + slug + "&_embed";
        fetchJobFromUrl(url, new ResponseListener<List<JobEntity>>() {
            @Override
            public void onResponse(List<JobEntity> result) {
                if (result != null && !result.isEmpty()) {
                    listener.onResponse(result.get(0));
                } else {
                    listener.onError("Job not found");
                }
            }

            @Override
            public void onError(String error) {
                listener.onError(error);
            }
        }, new TypeToken<List<Job>>(){}.getType());
    }

    private void fetchJobFromUrl(String url, ResponseListener<JobEntity> listener) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        Job job = gson.fromJson(response, Job.class);
                        JobEntity entity = mapToEntity(job);
                        AppDatabase.databaseWriteExecutor.execute(() -> jobDao.insertJobs(List.of(entity)));
                        listener.onResponse(entity);
                    } catch (Exception e) {
                        listener.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> listener.onError(error.getMessage()));
        queue.add(request);
    }

    private <T> void fetchJobFromUrl(String url, ResponseListener<List<JobEntity>> listener, Type type) {
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        List<Job> jobs = gson.fromJson(response, type);
                        List<JobEntity> entities = new ArrayList<>();
                        for (Job job : jobs) {
                            entities.add(mapToEntity(job));
                        }
                        AppDatabase.databaseWriteExecutor.execute(() -> jobDao.insertJobs(entities));
                        listener.onResponse(entities);
                    } catch (Exception e) {
                        listener.onError("Parse error: " + e.getMessage());
                    }
                },
                error -> listener.onError(error.getMessage()));
        queue.add(request);
    }

    private JobEntity mapToEntity(Job job) {
        return new JobEntity(
                job.getId(), job.getTitle(), job.getExcerpt(), job.getContent(),
                job.getDate(), job.getLink(), job.getFeaturedImage(),
                job.getCompany(), job.getLocation(), job.getJobType(), job.getApplication()
        );
    }

    public interface ResponseListener<T> {
        void onResponse(T result);
        void onError(String error);
    }
}

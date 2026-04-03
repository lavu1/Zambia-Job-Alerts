package com.solutions.alphil.zambiajobalerts.classes;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "jobs")
public class JobEntity {
    @PrimaryKey
    private int id;
    private String title;
    private String excerpt;
    private String content;
    private String date;
    private String link;
    private String featuredImage;
    private String company;
    private String location;
    private String jobType;
    private String applicationLink;

    public JobEntity(int id, String title, String excerpt, String content, String date, String link, 
                     String featuredImage, String company, String location, String jobType, String applicationLink) {
        this.id = id;
        this.title = title;
        this.excerpt = excerpt;
        this.content = content;
        this.date = date;
        this.link = link;
        this.featuredImage = featuredImage;
        this.company = company;
        this.location = location;
        this.jobType = jobType;
        this.applicationLink = applicationLink;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getFeaturedImage() { return featuredImage; }
    public void setFeaturedImage(String featuredImage) { this.featuredImage = featuredImage; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getApplicationLink() { return applicationLink; }
    public void setApplicationLink(String applicationLink) { this.applicationLink = applicationLink; }
}

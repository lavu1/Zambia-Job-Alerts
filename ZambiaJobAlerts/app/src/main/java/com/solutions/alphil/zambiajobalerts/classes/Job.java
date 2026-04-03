package com.solutions.alphil.zambiajobalerts.classes;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Job {
    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private RenderedTitle title;

    @SerializedName("excerpt")
    private RenderedExcerpt excerpt;

    @SerializedName("content")
    private RenderedContent content;

    @SerializedName("date")
    private String date;

    @SerializedName("link")
    private String link;

    @SerializedName("_embedded")
    private Embedded embedded;

    @SerializedName("meta")
    private Map<String, Object> meta;

    @SerializedName("job-categories")
    private List<Integer> jobCategories;

    @SerializedName("job-types")
    private List<Integer> jobTypes;

    @SerializedName("uagb_excerpt")
    private String uagbExcerpt;

    // Fields for caching support
    private String cachedCompany;
    private String cachedLocation;
    private String cachedJobType;
    private String cachedApplication;

    private static final Map<String, Integer> JOB_TYPE_MAPPING = Map.of(
            "Freelance", 9,
            "Full Time", 6,
            "Internship", 10,
            "Part Time", 7,
            "Temporary", 8,
            "Consultancy", 30,
            "Contract", 31,
            "Consultant", 30,
            "Tender", 32
    );

    public Job() {}

    public static Job fromEntity(JobEntity entity) {
        Job job = new Job();
        job.id = entity.getId();
        job.title = new RenderedTitle(entity.getTitle());
        job.excerpt = new RenderedExcerpt(entity.getExcerpt());
        job.content = new RenderedContent(entity.getContent());
        job.date = entity.getDate();
        job.link = entity.getLink();
        
        Embedded embedded = new Embedded();
        Media media = new Media(entity.getFeaturedImage());
        embedded.featuredMedia = new ArrayList<>();
        embedded.featuredMedia.add(media);
        job.embedded = embedded;

        job.cachedCompany = entity.getCompany();
        job.cachedLocation = entity.getLocation();
        job.cachedJobType = entity.getJobType();
        job.cachedApplication = entity.getApplicationLink();
        
        return job;
    }

    public static class RenderedTitle {
        @SerializedName("rendered")
        private String rendered;
        public RenderedTitle(String rendered) { this.rendered = rendered; }
        public String getRendered() { return rendered; }
    }

    public static class RenderedExcerpt {
        @SerializedName("rendered")
        private String rendered;
        public RenderedExcerpt(String rendered) { this.rendered = rendered; }
        public String getRendered() { return rendered; }
    }

    public static class RenderedContent {
        @SerializedName("rendered")
        private String rendered;
        public RenderedContent(String rendered) { this.rendered = rendered; }
        public String getRendered() { return rendered; }
    }

    public static class Embedded {
        @SerializedName("wp:featuredmedia")
        private List<Media> featuredMedia;

        @SerializedName("author")
        private List<Author> authors;

        @SerializedName("wp:term")
        private List<List<Term>> terms;
    }

    public static class Media {
        @SerializedName("source_url")
        private String sourceUrl;
        public Media(String sourceUrl) { this.sourceUrl = sourceUrl; }
        public String getSourceUrl() { return sourceUrl; }
    }

    public static class Author {
        @SerializedName("name")
        private String name;
        public String getName() { return name; }
    }

    public static class Term {
        @SerializedName("id")
        private int id;
        @SerializedName("name")
        private String name;
        @SerializedName("taxonomy")
        private String taxonomy;
        public int getId() { return id; }
        public String getName() { return name; }
        public String getTaxonomy() { return taxonomy; }
    }

    public int getId() { return id; }
    public String getTitle() { return title != null ? title.getRendered() : ""; }
    public String getContent() { return content != null ? content.getRendered() : ""; }
    public String getExcerpt() {
        if (uagbExcerpt != null && !uagbExcerpt.isEmpty()) return uagbExcerpt;
        return excerpt != null ? excerpt.getRendered() : "";
    }
    public String getDate() { return date; }
    public String getLink() { return link; }
    public String getFeaturedImage() {
        return embedded != null && embedded.featuredMedia != null && !embedded.featuredMedia.isEmpty() ?
                embedded.featuredMedia.get(0).getSourceUrl() : "";
    }

    public String getCompany() {
        if (cachedCompany != null) return cachedCompany;
        if (meta != null && meta.containsKey("_company_name")) return (String) meta.get("_company_name");
        return "";
    }

    public String getLocation() {
        if (cachedLocation != null) return cachedLocation;
        if (meta != null && meta.containsKey("_job_location")) return (String) meta.get("_job_location");
        return "";
    }

    public String getApplication() {
        if (cachedApplication != null) return cachedApplication;
        if (meta != null && meta.containsKey("_application")) return (String) meta.get("_application");
        return "";
    }

    public String getJobType() {
        if (cachedJobType != null) return cachedJobType;
        if (jobTypes != null && !jobTypes.isEmpty()) {
            int jobTypeId = jobTypes.get(0);
            for (Map.Entry<String, Integer> entry : JOB_TYPE_MAPPING.entrySet()) {
                if (entry.getValue() == jobTypeId) return entry.getKey();
            }
            if (embedded != null && embedded.terms != null) {
                for (List<Term> termList : embedded.terms) {
                    for (Term term : termList) {
                        if ("job_listing_type".equals(term.getTaxonomy()) && term.getId() == jobTypeId) return term.getName();
                    }
                }
            }
        }
        return "";
    }

    public String getFormattedDate() {
        if (date != null && date.length() >= 10) return date.substring(0, 10);
        return date;
    }
}

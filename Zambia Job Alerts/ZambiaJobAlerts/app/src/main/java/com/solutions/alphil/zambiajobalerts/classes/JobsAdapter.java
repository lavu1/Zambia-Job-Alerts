package com.solutions.alphil.zambiajobalerts.classes;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.nativead.AdChoicesView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.solutions.alphil.zambiajobalerts.R;

import java.util.List;

public class JobsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<Object> displayItems;
    private OnJobClickListener listener;

    private static final int VIEW_TYPE_JOB = 0;
    private static final int VIEW_TYPE_AD = 1;


    public interface OnJobClickListener {
        void onJobClick(Job job);
    }

    public JobsAdapter(List<Object> displayItems, OnJobClickListener listener) {
        this.displayItems = displayItems;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayItems.get(position);
        return item instanceof NativeAd ? VIEW_TYPE_AD : VIEW_TYPE_JOB;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_AD) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_native_ad, parent, false);
            return new NativeAdViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_job, parent, false);
            return new JobViewHolder(view);
        }
    }
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayItems.get(position);
        if (holder instanceof JobViewHolder) {
            Job job = (Job) item;
            ((JobViewHolder) holder).bind(job, listener); // Pass listener to bind method
        } else if (holder instanceof NativeAdViewHolder) {
            NativeAd nativeAd = (NativeAd) item;
            ((NativeAdViewHolder) holder).bind(nativeAd);
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    public void updateDisplayList(List<Object> newItems) {
        this.displayItems = newItems;
        notifyDataSetChanged();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof NativeAdViewHolder) {
            NativeAdViewHolder adHolder = (NativeAdViewHolder) holder;
            // Manually clear the views to unbind the ad and prevent crash
            adHolder.adHeadline.setText("");
            adHolder.adBody.setText("");
            adHolder.adAdvertiser.setText("");
            adHolder.adCallToAction.setText("");
            adHolder.adAppIcon.setImageDrawable(null);
            // Do not call setNativeAd(null) - this causes the NPE in SDK v24.6.0
            // Optionally, if you have a reference to the NativeAd in the holder, call nativeAd.destroy() here
        }
        /*if (holder instanceof NativeAdViewHolder) {
            ((NativeAdViewHolder) holder).nativeAdView.setNativeAd(null);
        }*/
    }

    static class JobViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvExcerpt, tvDate, tvCompany, tvLocation, tvJobType;
        ImageView ivImage;
        Button btnApplyNow, btnShare, btnDetails;

        JobViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvExcerpt = itemView.findViewById(R.id.tvExcerpt);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvCompany = itemView.findViewById(R.id.tvCompany);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvJobType = itemView.findViewById(R.id.tvJobType);
            ivImage = itemView.findViewById(R.id.ivImage);
            // Initialize buttons
            btnApplyNow = itemView.findViewById(R.id.btnApplyNow);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnDetails = itemView.findViewById(R.id.btnDetails);
        }

        void bind(Job job,OnJobClickListener listener) {
            tvTitle.setText(job.getTitle());
            tvExcerpt.setText(job.getExcerpt());
            tvDate.setText(job.getFormattedDate());

            // Set company if available
            String company = job.getCompany();
            if (!company.isEmpty()) {
                tvCompany.setText("Company: " + company);
                tvCompany.setVisibility(View.VISIBLE);
            } else {
                tvCompany.setVisibility(View.GONE);
            }

            // Set location if available
            String location = job.getLocation();
            if (!location.isEmpty()) {
                tvLocation.setText("Location: " + location);
                tvLocation.setVisibility(View.VISIBLE);
            } else {
                tvLocation.setVisibility(View.GONE);
            }

            // Set job type if available
            String jobType = job.getJobType();
            if (!jobType.isEmpty()) {
                tvJobType.setText("Type: " + jobType);
                tvJobType.setVisibility(View.VISIBLE);
            } else {
                tvJobType.setVisibility(View.GONE);
            }

            if (!job.getFeaturedImage().isEmpty()) {
                Glide.with(itemView.getContext()).load(job.getFeaturedImage()).into(ivImage);
            }

            // Setup Apply Now button
            String application = job.getApplication();
            if (application != null && !application.isEmpty()) {
                btnApplyNow.setVisibility(View.VISIBLE);
                btnApplyNow.setOnClickListener(v -> applyForJob(job));
            } else {
                btnApplyNow.setVisibility(View.GONE);
            }

            // Setup Share button
            btnShare.setOnClickListener(v -> shareJob(job));

            // Setup Details button
            btnDetails.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onJobClick(job);
                }
            });
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onJobClick(job);
                }
            });

        }
        /*
        private void applyForJob(Job job) {

            SharedPreferences prefs;
            prefs = itemView.getContext().getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
            int viewCount = prefs.getInt("job_views", 0) + 1;
            prefs.edit().putInt("job_views", viewCount).apply();

            String application = job.getApplication();
            if (application != null && !application.isEmpty()) {
                String cleanEmail = application.trim();

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{cleanEmail});
                intent.putExtra(Intent.EXTRA_SUBJECT, "Job Application: " + job.getTitle());
                intent.putExtra(Intent.EXTRA_TEXT, "I am applying for the " + job.getTitle() + " position.");

                try {
                    itemView.getContext().startActivity(Intent.createChooser(intent, "Apply via:"));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(itemView.getContext(), "No email app found. Email: " + cleanEmail, Toast.LENGTH_LONG).show();

                    // Copy to clipboard as fallback
                    ClipboardManager clipboard = (ClipboardManager) itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Application Email", cleanEmail);
                    clipboard.setPrimaryClip(clip);
                }
            } else {
                Toast.makeText(itemView.getContext(), "No application email provided", Toast.LENGTH_SHORT).show();
            }
        }
*/
        private void applyForJob(Job job) {
            SharedPreferences prefs;
            prefs = itemView.getContext().getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
            int viewCount = prefs.getInt("job_views", 0) + 1;
            prefs.edit().putInt("job_views", viewCount).apply();
            String application = job.getApplication();
                if (application != null && !application.isEmpty()) {
                    String cleanApp = application.trim();

                    // Check if it's an email (rough check for @ and domain)
                    if (cleanApp.contains("@") && cleanApp.contains(".")) {
                        // Treat as email: Use ACTION_SENDTO with mailto: to target only email apps
                        Intent intent = new Intent(Intent.ACTION_SENDTO);
                        intent.setData(Uri.parse("mailto:" + cleanApp));
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Job Application: " + job.getTitle());
                        intent.putExtra(Intent.EXTRA_TEXT, createEmailBody(job));

                        // Launch directly (no chooser; only email apps will handle mailto)
                        try {
                            itemView.getContext().startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // If no email app is found, offer to copy the email
                            offerEmailCopy(cleanApp);
                        }
                    } else if (cleanApp.startsWith("http://") || cleanApp.startsWith("https://")) {
                        // Treat as URL: Use ACTION_VIEW to target only browsers
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(cleanApp));
                        try {
                            itemView.getContext().startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // Fallback: Offer to copy the URL (adapt offerEmailCopy if needed)
                            offerEmailCopy(cleanApp); // Or create a separate offerUrlCopy method
                        }
                    } else {
                        // Fallback for unrecognized format: Offer to copy
                        offerEmailCopy(cleanApp);
                    }
                }
            }
        private void offerEmailCopy(String email) {
            new AlertDialog.Builder(itemView.getContext())
                    .setTitle("No Email App Found")
                    .setMessage("Would you like to copy the application email address to clipboard?")
                    .setPositiveButton("Copy Email", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager) itemView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Job Application Email", email);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(itemView.getContext(), "Email copied: " + email, Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        private String createEmailBody(Job job) {
            return "Dear Hiring Manager,\n\n" +
                    "I am writing to apply for the position of " + job.getTitle() +
                    " that I found through Zambia Job Alerts.\n\n" +
                    "Please find my application materials attached.\n\n" +
                    "Thank you for your consideration.\n\n" +
                    "Sincerely,\n" +
                    "[Your Name]\n" +
                    "[Your Phone Number]\n" +
                    "[Your Email Address]";
        }
        private void shareJob(Job job) {
            SharedPreferences prefs;
            prefs = itemView.getContext().getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
            int viewCount = prefs.getInt("job_views", 0) + 1;
            prefs.edit().putInt("job_views", viewCount).apply();

            String playStoreUrl = "https://play.google.com/store/apps/details?id=com.solutions.alphil.zambiajobalerts";
            String download = "Check out Zambia Job Alerts app for the latest job opportunities in Zambia! Download now: " + playStoreUrl;

            String jobLink = generateJobLink(job);
            String shareText = "Check out this job opportunity: " + job.getTitle() +
                    "\n\n" + jobLink +
                    "\n\nVia Zambia Job Alerts App"+ "\n\n" +download;;

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Job Opportunity: " + job.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

            itemView.getContext().startActivity(Intent.createChooser(shareIntent, "Share Job via"));
        }

        private String generateJobLink(Job job) {
            // Use the same deep link structure as before
            return job.getLink();//"https://zambiajobalerts.com/job/" + job.getId();
        }

    }

    static class NativeAdViewHolder extends RecyclerView.ViewHolder {
        NativeAdView nativeAdView;
        ImageView adAppIcon;
        TextView adHeadline, adBody, adAdvertiser, adAttribution;  // Add adAttribution
        Button adCallToAction;
        AdChoicesView adChoicesView;  // Fix to AdChoicesView

        NativeAdViewHolder(@NonNull View itemView) {
            super(itemView);
            nativeAdView = itemView.findViewById(R.id.native_ad_view);
            adAppIcon = itemView.findViewById(R.id.ad_app_icon);
            adHeadline = itemView.findViewById(R.id.ad_headline);
            adBody = itemView.findViewById(R.id.ad_body);
            adAdvertiser = itemView.findViewById(R.id.ad_advertiser);
            adCallToAction = itemView.findViewById(R.id.ad_call_to_action);
            adAttribution = itemView.findViewById(R.id.ad_attribution);  // Add this
            adChoicesView = itemView.findViewById(R.id.ad_choices_view);  // Fix ID and type
        }
        void bind(NativeAd nativeAd) {
            // Bind headline (returns String directly)
            String headline = nativeAd.getHeadline();
            if (headline != null) {
                adHeadline.setText(headline);
                adHeadline.setVisibility(View.VISIBLE);
            } else {
                adHeadline.setVisibility(View.INVISIBLE);
            }

            // Bind body (returns String directly)
            String body = nativeAd.getBody();
            if (body != null) {
                adBody.setText(body);
                adBody.setVisibility(View.VISIBLE);
            } else {
                adBody.setVisibility(View.INVISIBLE);
            }

            // Bind call to action (returns String directly)
            String callToAction = nativeAd.getCallToAction();
            if (callToAction != null) {
                adCallToAction.setText(callToAction);
                adCallToAction.setVisibility(View.VISIBLE);
            } else {
                adCallToAction.setVisibility(View.INVISIBLE);
            }

            // Bind icon
            if (nativeAd.getIcon() != null) {
                adAppIcon.setImageDrawable(nativeAd.getIcon().getDrawable());
                adAppIcon.setVisibility(View.VISIBLE);
            } else {
                adAppIcon.setVisibility(View.INVISIBLE);
            }

            // Bind advertiser (returns String directly)
            String advertiser = nativeAd.getAdvertiser();
            if (advertiser != null) {
                adAdvertiser.setText(advertiser);
                adAdvertiser.setVisibility(View.VISIBLE);
            } else {
                adAdvertiser.setVisibility(View.INVISIBLE);
            }


            // Show Ad attribution badge (required for programmatic)
            if (adAttribution != null) {
                adAttribution.setVisibility(View.VISIBLE);
            }

            // Register AdChoicesView (required)
            if (adChoicesView != null) {
                nativeAdView.setAdChoicesView(adChoicesView);
            }

            // Register other views for click tracking
            nativeAdView.setHeadlineView(adHeadline);
            nativeAdView.setBodyView(adBody);
            nativeAdView.setCallToActionView(adCallToAction);
            nativeAdView.setIconView(adAppIcon);
            nativeAdView.setAdvertiserView(adAdvertiser);

            // Set the native ad
            //nativeAdView.setNativeAd(nativeAd);
            // Register the views with the native ad (handles clicks, impressions, etc.)
            nativeAdView.setNativeAd(nativeAd);


        }
    }
}

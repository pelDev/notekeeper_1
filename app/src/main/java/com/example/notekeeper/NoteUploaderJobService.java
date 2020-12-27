package com.example.notekeeper;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.net.Uri;
import android.os.AsyncTask;

public class NoteUploaderJobService extends JobService {

    public static final String EXTRA_DATA_URI = "com.example.notekeeper.extras.DATA_URI";
    private NoteUploader mNoteUploader;

    public NoteUploaderJobService() {
    }

    @Override
    public boolean onStartJob(JobParameters params) {

        mNoteUploader = new NoteUploader(this);

        AsyncTask<JobParameters, Void, Void> task = new AsyncTask<JobParameters, Void, Void>() {
            @Override
            protected Void doInBackground(JobParameters... jobParameters) {
                // get the extras from the job parameters
                JobParameters jobParams = jobParameters[0];
                String stringUri = jobParams.getExtras().getString(EXTRA_DATA_URI);
                Uri dataUri = Uri.parse(stringUri);
                mNoteUploader.doUpload(dataUri);
                if (!mNoteUploader.isCancelled())
                jobFinished(jobParams, false);
                return null;
            }
        };

        task.execute(params);

        return true;

    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // handle stop job
        mNoteUploader.cancel();

        // return true to reschedule
        return true;
    }

}

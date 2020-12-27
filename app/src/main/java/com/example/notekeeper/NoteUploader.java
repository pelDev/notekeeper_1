package com.example.notekeeper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.notekeeper.NoteKeeperProviderContract.Notes;

public class NoteUploader {

    private final String TAG = getClass().getSimpleName();

    private final Context mContext;
    private boolean mIsCancelled;

    public NoteUploader(Context context) { mContext = context; }

    public boolean isCancelled() { return mIsCancelled; }

    public void cancel() { mIsCancelled = true; }

    public void doUpload(Uri uri) {

        String[] columns = {
                Notes.COLUMN_COURSE_ID,
                Notes.COLUMN_NOTE_TITLE,
                Notes.COLUMN_NOTE_TEXT
        };

        Cursor cursor = mContext.getContentResolver().query(uri, columns, null, null, null);
        int courseIdPos = cursor.getColumnIndex(Notes.COLUMN_COURSE_ID);
        int noteTitlePos = cursor.getColumnIndex(Notes.COLUMN_NOTE_TITLE);
        int noteTextPos = cursor.getColumnIndex(Notes.COLUMN_NOTE_TEXT);

        Log.d(TAG, ">>>*** UPLOAD START - " + uri + " ***<<<");
        mIsCancelled = false;

        while (!mIsCancelled && cursor.moveToNext()) {

            String courseId = cursor.getString(courseIdPos);
            String noteTitle = cursor.getString(noteTitlePos);
            String noteText = cursor.getString(noteTextPos);

            if (!noteTitle.equals("")) {
                Log.d(TAG, ">>>Uploading Note<<< " + courseId + " | " + noteTitle + " | " + noteText);
                simulateLongRunningWork();
            }

        }

    }

    private void simulateLongRunningWork() {
        try {
            Thread.sleep(1500);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

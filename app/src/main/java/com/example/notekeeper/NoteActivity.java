package com.example.notekeeper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;

import com.example.notekeeper.NoteKeeperDatabaseContract.CourseInfoEntry;
import com.example.notekeeper.NoteKeeperDatabaseContract.NoteInfoEntry;
import com.example.notekeeper.NoteKeeperProviderContract.Courses;
import com.example.notekeeper.NoteKeeperProviderContract.Notes;

public class NoteActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int LOADER_NOTES = 0;
    public static final int LOADER_COURSES = 1;
    private static final int BUDGET_NOTIFICATION_ID = 0;
    private final String TAG = getClass().getSimpleName();
    public static final String NOTE_ID = "com.example.notekeeper.NOTE_POSITION";
    public static final String PRIMARY_CHANNEL_ID = "note-reminder-channel";
    public static final int ID_NOT_SET = -1;
    private NoteInfo mNote;
    private boolean mIsNewNote;
    private Spinner mSpinnerCourses;
    private EditText mTextNoteTitle;
    private EditText mTextNoteText;
    private int mNoteId;
    private boolean mIsCancelling;
    private NoteActivityViewModel mViewModel;
    private NoteKeeperOpenHelper mDbOpenHelper;
    private Cursor mNoteCursor;
    private int mCourseIdPosition;
    private int mNoteTitlePosition;
    private int mNoteTextPosition;
    private SimpleCursorAdapter mAdapterCourses;
    private boolean mCourseQueryFinished;
    private boolean mNoteQueryFinished;
    private Uri mNoteUri;
    private NotificationManager mNotifyManager;

    private static final int NOTIFICATION_ID = 0;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.optionsmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_send) {
            sendEmail();
            return  true;
        } else if (id == R.id.menu_cancel) {
            mIsCancelling = true;
            finish();
        } else if (id == R.id.menu_next) {
            moveNext();
        } else if (id == R.id.menu_set_reminder) {
            showReminderNotification();
        }
        return super.onOptionsItemSelected(item);
    }

    private void showReminderNotification() {
        sendNotification();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.menu_next);
        int lastNoteIndex = DataManager.getInstance().getNotes().size() - 1;
        item.setEnabled(mNoteId < lastNoteIndex);
        return super.onPrepareOptionsMenu(menu);
    }

    private void moveNext() {
        saveNote();

        ++mNoteId;
        mNote = DataManager.getInstance().getNotes().get(mNoteId);

        saveOriginalNoteValues();
        displayNote();
        invalidateOptionsMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsCancelling = false;
    }

    private void sendEmail() {
        saveNote();
        mIsCancelling = true;
        Cursor course = (Cursor) mSpinnerCourses.getSelectedItem();
        int courseTitlePos = course.getColumnIndex(Courses.COLUMN_COURSE_TITLE);
        String courseTitle = course.getString(courseTitlePos);
        String subject = mTextNoteTitle.getText().toString();
        String text = "Check out what Pelumi learnt in the Pluralsight course \"" + courseTitle
        + "\"\n" + mTextNoteText.getText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc2822");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_note);

        mDbOpenHelper = new NoteKeeperOpenHelper(this);

        ViewModelProvider viewModelProvider = new ViewModelProvider(getViewModelStore(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()));
        mViewModel = viewModelProvider.get(NoteActivityViewModel.class);

        if(mViewModel.mIsNewlyCreated && savedInstanceState != null) {
            mViewModel.restoreState(savedInstanceState);
        }

        mViewModel.mIsNewlyCreated = false;
        mSpinnerCourses = findViewById(R.id.spinner_courses);


        mAdapterCourses = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item, null,
                new String[] {CourseInfoEntry.COLUMN_COURSE_TITLE},
                new int[] {android.R.id.text1}, 0);
        mAdapterCourses.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerCourses.setAdapter(mAdapterCourses);


        getLoaderManager().initLoader(LOADER_COURSES, null, this);

        readDisplayStateValues();
//        saveOriginalNoteValues();

        mTextNoteTitle = findViewById(R.id.text_note_title);
        mTextNoteText = findViewById(R.id.text_note_text);
        if(!mIsNewNote)
            getLoaderManager().initLoader(LOADER_NOTES, null, this);

        createNotificationChannel();
        Log.d(TAG, "onCreate");
    }

    private void saveOriginalNoteValues() {
        if(mIsNewNote) {
            return;
        } else {
            mViewModel.mOriginalNoteCourseId = mNote.getCourse().getCourseId();
            mViewModel.mOriginalNoteTitle = mNote.getTitle();
            mViewModel.mOriginalNoteText = mNote.getText();
        }

    }

    private void displayNote() {
        String courseId = mNoteCursor.getString(mCourseIdPosition);
        String noteTitle = mNoteCursor.getString(mNoteTitlePosition);
        String noteText = mNoteCursor.getString(mNoteTextPosition);
        int courseIndex = getIndexOfCourseId(courseId);
        mSpinnerCourses.setSelection(courseIndex);
        mTextNoteTitle.setText(noteTitle);
        mTextNoteText.setText(noteText);
    }

    private int getIndexOfCourseId(String courseId) {
        Cursor cursor = mAdapterCourses.getCursor();
        int courseIdPosition = cursor.getColumnIndex(CourseInfoEntry.COLUMN_COURSE_ID);
        int courseRowIndex = 0;
        boolean more = cursor.moveToFirst();
        while (more) {
            // get course id value for current row
            String cursorCourseId = cursor.getString(courseIdPosition);
            // check if course id that was passed in equals course id in the cursor
            if(courseId.equals(cursorCourseId)) {
                // breaks out of the loop
                break;
            } else {
                // increase cursor row position
                courseRowIndex++;
                more = cursor.moveToNext();
            }
        }
        return courseRowIndex;
    }

    private void readDisplayStateValues() {
        Intent intent = getIntent();

        // get the note id passes from the list of notes
        mNoteId = intent.getIntExtra(NOTE_ID, ID_NOT_SET);
        mIsNewNote = this.mNoteId == ID_NOT_SET;
        if (mIsNewNote) {
            createNewNote();
        }

        Log.i(TAG, "mNotePosition: " + mNoteId);
            //mNote = DataManager.getInstance().getNotes().get(this.mNoteId);

    }

    private void createNewNote() {
        AsyncTask<ContentValues, Integer, Uri> task = new AsyncTask<ContentValues, Integer, Uri>() {

            private ProgressBar progressBar;

            @Override
            protected void onPreExecute() {
                progressBar = findViewById(R.id.progress_bar);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(1);
            }

            @Override
            protected Uri doInBackground(ContentValues... contentValues) {
                Uri uri = getContentResolver().insert(Notes.CONTENT_URI, contentValues[0]);
                simulateLongRunningWork();
                publishProgress(2);
                simulateLongRunningWork();
                publishProgress(3);
                return uri;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                progressBar.setProgress(values[0]);
            }

            @Override
            protected void onPostExecute(Uri uri) {
                progressBar.setVisibility(View.GONE);
                mNoteUri = uri;
            }
        };
        final ContentValues values = new ContentValues();
        values.put(Notes.COLUMN_COURSE_ID, "");
        values.put(Notes.COLUMN_NOTE_TITLE, "");
        values.put(Notes.COLUMN_NOTE_TEXT, "");


                //mNoteUri = getContentResolver().insert(Notes.CONTENT_URI, values);
        task.execute(values);

        Log.i(TAG, "Created new note at row position " + mNoteId);
    }

    private void simulateLongRunningWork() {
        try {
            Thread.currentThread().sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mIsCancelling) {
            Log.i(TAG, "Cancelling note at position " + mNoteId);
            if(mIsNewNote) {
                deleteNewNoteFromDatabase();
            }

        } else {
            saveNote();
        }
        Log.d(TAG, "onPause");
    }

    private void deleteNewNoteFromDatabase() {
        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                getContentResolver().delete(mNoteUri, null, null);
                return null;
            }
        };
        task.execute();
    }

    private void saveNote() {
        String courseId = selectedCourseId();
        String noteTitle = mTextNoteTitle.getText().toString();
        String noteText = mTextNoteText.getText().toString();

        saveNoteToDatabase(courseId, noteTitle, noteText);
    }

    private String selectedCourseId() {
        int selectedPosition = mSpinnerCourses.getSelectedItemPosition();
        Cursor cursor = mAdapterCourses.getCursor();
        cursor.moveToPosition(selectedPosition);
       // int courseIdPos = cursor.getColumnIndex(CourseInfoEntry.COLUMN_COURSE_ID);
        String  courseId = cursor.getString(cursor.getColumnIndex(Courses.COLUMN_COURSE_ID));
        return courseId;
    }

    private void saveNoteToDatabase(String courseId, String noteTitle, String noteText) {

        final ContentValues values = new ContentValues();
        values.put(NoteInfoEntry.COLUMN_COURSE_ID, courseId);
        values.put(NoteInfoEntry.COLUMN_NOTE_TITLE, noteTitle);
        values.put(NoteInfoEntry.COLUMN_NOTE_TEXT, noteText);

        AsyncTask task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                getContentResolver().update(mNoteUri, values, null, null);
                return null;
            }
        };
        task.execute();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(outState != null)
            mViewModel.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        mDbOpenHelper.close();
        if(mNoteCursor != null)
            mNoteCursor.close();
        super.onDestroy();
    }


    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        CursorLoader loader = null;
        if (id == LOADER_NOTES)
            loader = createLoaderNotes();
        else if (id == LOADER_COURSES) {
            loader = createLoaderCourses();
        }
        return loader;
    }

    private CursorLoader createLoaderCourses() {
        mCourseQueryFinished = false;
        Uri uri = Courses.CONTENT_URI;
        String[] courseColumns = {
                CourseInfoEntry.COLUMN_COURSE_TITLE,
                CourseInfoEntry.COLUMN_COURSE_ID,
                CourseInfoEntry._ID
        };
        return new CursorLoader(this, uri, courseColumns, null,null, CourseInfoEntry.COLUMN_COURSE_TITLE);
    }

    private CursorLoader createLoaderNotes() {
        mNoteQueryFinished = false;
        String[] noteColumns = {
            Notes.COLUMN_NOTE_TITLE,
            Notes.COLUMN_NOTE_TEXT,
            Notes.COLUMN_COURSE_ID
        };

        mNoteUri = ContentUris.withAppendedId(Notes.CONTENT_URI, mNoteId);
        return  new CursorLoader(this, mNoteUri, noteColumns, null, null, null);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == LOADER_NOTES)
            loadFinishedNotes(data);
        else if (loader.getId() == LOADER_COURSES) {
            mAdapterCourses.changeCursor(data);
            mCourseQueryFinished = true;
            displayNoteWhenQueriesFinished();
        }
    }

    private void loadFinishedNotes(Cursor data) {
        mNoteCursor = data;
        mCourseIdPosition = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_COURSE_ID);
        mNoteTitlePosition = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TITLE);
        mNoteTextPosition = mNoteCursor.getColumnIndex(NoteInfoEntry.COLUMN_NOTE_TEXT);
        mNoteCursor.moveToNext();
        mNoteQueryFinished = true;
        displayNoteWhenQueriesFinished();
    }

    public void createNotificationChannel() {
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Create a NotificationChannel
            NotificationChannel notificationChannel = new NotificationChannel(PRIMARY_CHANNEL_ID,
                    "Note Reminder", NotificationManager
                    .IMPORTANCE_DEFAULT);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationChannel.setDescription("Notification to check your note");
            mNotifyManager.createNotificationChannel(notificationChannel);
        }
    }

    private NotificationCompat.Builder getNotificationBuilder() {
        Intent noteActivityIntent = new Intent(this, NoteActivity.class);
        noteActivityIntent.putExtra(NoteActivity.NOTE_ID, mNoteId);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(this,
                BUDGET_NOTIFICATION_ID, noteActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent noteListIntent = new Intent(this, MainActivity.class);
        PendingIntent noteListPendingIntent = PendingIntent.getActivity(this,
                BUDGET_NOTIFICATION_ID, noteListIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        String message = mTextNoteText.getText().toString();

        // create pending intent to do back up
        Intent backupServiceIntent = new Intent(this, NoteBackupService.class);
        backupServiceIntent.putExtra(NoteBackupService.EXTRA_COURSE_ID, NoteBackup.ALL_COURSES);
        PendingIntent backupServicePendingIntent = PendingIntent.getService(this,
                BUDGET_NOTIFICATION_ID, backupServiceIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, PRIMARY_CHANNEL_ID)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.drawable.ic_notes)
                .setContentTitle("Note Reminder")
                .setColor(getResources().getColor(R.color.colorAccent))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setTicker("Review Note")
                .setContentIntent(notificationPendingIntent)
                .addAction(0, "View all notes", noteListPendingIntent)
                .addAction(0, "Backup notes", backupServicePendingIntent)
                .setAutoCancel(true)
                .setStyle( new NotificationCompat.BigTextStyle()
                        .bigText(mTextNoteText.getText().toString())
                        .setBigContentTitle(mTextNoteTitle.getText().toString())
                        .setSummaryText("Review Note"));
    }

    public void sendNotification() {
        NotificationCompat.Builder notifyBuilder = getNotificationBuilder();
        mNotifyManager.notify(BUDGET_NOTIFICATION_ID, notifyBuilder.build());
    }

    public void cancelNotification() {
        mNotifyManager.cancel(BUDGET_NOTIFICATION_ID);
    }

    private void displayNoteWhenQueriesFinished() {
        if (mNoteQueryFinished && mCourseQueryFinished) {
            displayNote();
            mNoteQueryFinished = false;
            mCourseQueryFinished = false;
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (loader.getId() == LOADER_NOTES) {
            if (mNoteCursor != null)
                mNoteCursor.close();
        } else if (loader.getId() == LOADER_COURSES) {
            mAdapterCourses.changeCursor(null);
        }
    }

}
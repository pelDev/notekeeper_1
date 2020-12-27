package com.example.notekeeper;

import android.content.Context;
import android.content.Intent;

public class CourseEventBroadcastHelper {

    // app defined action
    public static final String ACTION_COURSE_EVENT = "com.example.notekeeper.action.COURSE_EVENT";

    public static final String EXTRA_COURSE_ID = "com.example.notekeeper.extra.COURSE_ID";

    public static final String EXTRA_COURSE_MESSAGE = "com.example.notekeeper.extra.COURSE_MESSAGE";

    public static void sendEventBroadcast(Context context, String courseId, String message) {

        Intent intent = new Intent(ACTION_COURSE_EVENT);
        intent.putExtra(EXTRA_COURSE_ID, courseId);
        intent.putExtra(EXTRA_COURSE_MESSAGE, message);

        context.sendBroadcast(intent);
    }

}

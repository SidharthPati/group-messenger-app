package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {
    private static final String TAG = "GroupMessengerProvider";

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         * 
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */
        /*
         * References:
         * Line 76: https://stackoverflow.com/questions/2390244/how-to-get-the-keys-from-contentvalues
         * Lines 81-83: https://developer.android.com/training/data-storage/files#WriteInternalStorage
         * Line 81: https://stackoverflow.com/questions/28242386/cannot-resolve-method-openfileoutputjava-lang-string-int
         */
        String filename = (String)values.get("key");
        String fileContents = (String)values.get("value");
        FileOutputStream outputStream;

        try {
            outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(fileContents.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */
        /*
         *  Sid notes:
         *  The ContentResolver object communicates with the provider object,
         *  an instance of a class that implements ContentProvider.
         *  Lines 128-133: https://developer.android.com/training/data-storage/files#WriteInternalStorage
         *  Line 138-141: https://stackoverflow.com/questions/18290864/create-a-cursor-from-hardcoded-array-instead-of-db
         */

        try {
            File directory = getContext().getFilesDir();
            File file = new File(directory, selection);

            FileInputStream f = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(f));
            String ln = br.readLine();
            Log.v(TAG, "Line Read: " + ln);
            br.close();

            // Matrix Cursor
            String[] columns = new String[]{"key", "value"};
            String[] input = new String[]{selection, ln};
            MatrixCursor cursor = new MatrixCursor(columns);
            cursor.addRow(input);
            return cursor;

        }
        catch (Exception e){
            Log.e(TAG, "File not Found or IOException while reading File");
        }
        Log.v("query", selection);
        return null;
    }
}

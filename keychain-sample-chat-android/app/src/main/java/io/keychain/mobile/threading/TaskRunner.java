package io.keychain.mobile.threading;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * https://stackoverflow.com/questions/58767733/android-asynctask-api-deprecating-in-android-11-what-are-the-alternatives
 *
 * Use as follows:
 *   1. Create an instance of TaskRunner 'taskRunner', optionally passing in a particular Executor (default is single thread)
 *   2. Invoke #executeAsync on the instance, passing in a Callable and a Callback
 *
 *   The Callable will be run on the Executor thread, and the Callback will be run on the UI thread (Main Looper)
 */
public class TaskRunner {
    private static final String TAG = "TaskRunner";
    private final Executor executor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public TaskRunner() {
        this(Executors.newSingleThreadExecutor());
    }
    public TaskRunner(Executor executor) {
        this.executor = executor;
    }

    public interface Callback<R> {
        void onComplete(R result);
    }

    public <R> void executeAsync(Callable<R> callable, Callback<R> callback) {
        // Callable is executed on an Executor (Worker) Thread
        executor.execute(() -> {
            R result = null;
            try {
                result = callable.call();
            } catch (Exception e) {
                Log.e(TAG, "Exception thrown from callable: " + e.getMessage());
            }
            final R r = result;

            // Callback is executed on the Main Looper (Main Thread)
            if (callback != null) {
                handler.post(() -> callback.onComplete(r));
            }
        });
    }
}

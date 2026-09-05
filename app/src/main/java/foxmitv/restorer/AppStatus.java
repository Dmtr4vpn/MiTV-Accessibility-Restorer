package foxmitv.restorer;

import android.content.Context;
import android.content.SharedPreferences;

final class AppStatus {
    private static final String PREFERENCES = "status";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_LAST_SUCCESS = "last_success";
    private static final String KEY_LAST_MESSAGE = "last_message";

    private AppStatus() {
    }

    static void saveResult(Context context, boolean success, String message) {
        preferences(context).edit()
                .putLong(KEY_LAST_TIME, System.currentTimeMillis())
                .putBoolean(KEY_LAST_SUCCESS, success)
                .putString(KEY_LAST_MESSAGE, message)
                .commit();
    }

    static long getLastTime(Context context) {
        return preferences(context).getLong(KEY_LAST_TIME, 0L);
    }

    static boolean getLastSuccess(Context context) {
        return preferences(context).getBoolean(KEY_LAST_SUCCESS, false);
    }

    static String getLastMessage(Context context) {
        return preferences(context).getString(KEY_LAST_MESSAGE, "Восстановление ещё не выполнялось");
    }

    private static SharedPreferences preferences(Context context) {
        return DirectBootContext.get(context)
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}

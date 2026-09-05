package foxmitv.restorer;

import android.content.Context;
import android.os.Build;

final class DirectBootContext {
    private DirectBootContext() {
    }

    static Context get(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Context deviceContext = appContext.createDeviceProtectedStorageContext();
            if (deviceContext != null) {
                return deviceContext;
            }
        }
        return appContext;
    }
}

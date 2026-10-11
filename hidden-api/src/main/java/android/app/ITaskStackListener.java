package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface ITaskStackListener extends IInterface {
    abstract class Stub extends Binder implements ITaskStackListener {
        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}

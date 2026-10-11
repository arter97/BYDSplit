package android.app;

import android.os.RemoteException;

public abstract class TaskStackListener extends ITaskStackListener.Stub {
    public void onTaskStackChanged() throws RemoteException {
        throw new RuntimeException("Stub!");
    }

    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) throws RemoteException {
        throw new RuntimeException("Stub!");
    }
}

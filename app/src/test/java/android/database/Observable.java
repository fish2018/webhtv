package android.database;

import java.util.ArrayList;

public abstract class Observable<T> {

    protected final ArrayList<T> mObservers = new ArrayList<>();

    public void registerObserver(T observer) {
        if (observer == null) throw new IllegalArgumentException("The observer is null.");
        synchronized (mObservers) {
            if (mObservers.contains(observer)) throw new IllegalStateException("Observer is already registered.");
            mObservers.add(observer);
        }
    }

    public void unregisterObserver(T observer) {
        if (observer == null) throw new IllegalArgumentException("The observer is null.");
        synchronized (mObservers) {
            if (!mObservers.remove(observer)) throw new IllegalStateException("Observer was not registered.");
        }
    }

    public void unregisterAll() {
        synchronized (mObservers) {
            mObservers.clear();
        }
    }
}

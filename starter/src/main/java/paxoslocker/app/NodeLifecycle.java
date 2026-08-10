package paxoslocker.app;

public interface NodeLifecycle extends AutoCloseable {
    void start();

    void stop();

    boolean isRunning();

    default void restart() {
        stop();
        start();
    }

    @Override
    default void close() {
        stop();
    }
}

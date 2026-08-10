package paxoslocker.persistence;

import java.io.Serializable;
import java.util.Optional;

public interface PersistentStore {
    <T extends Serializable> void save(String key, T value);

    <T> Optional<T> load(String key, Class<T> type);

    void delete(String key);
}

package paxoslocker.persistence;

import java.io.*;
import java.nio.file.*;
import java.util.Optional;

/**
 * Atomic filesystem utility. Students decide when protocol state must be saved.
 */
public final class FileStore implements PersistentStore {
    private final Path root;

    public FileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized <T extends Serializable> void save(String key, T value) {
        Path target = resolve(key), temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temp))) {
                out.writeObject(value);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized <T> Optional<T> load(String key, Class<T> type) {
        Path path = resolve(key);
        if (!Files.exists(path)) return Optional.empty();
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path))) {
            return Optional.of(type.cast(in.readObject()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        if (!key.matches("[A-Za-z0-9._/-]+")) throw new IllegalArgumentException("unsafe key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("key escapes root");
        return path;
    }
}

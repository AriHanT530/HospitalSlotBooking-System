package com.hospital.dao;

import com.hospital.exception.DataAccessException;
import com.hospital.util.AppLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract file-backed repository (Unit 2: abstract class + template method,
 * Unit 4: byte/character streams).
 *
 * Concrete subclasses only supply three things: how to turn an entity into a
 * line, how to read a line back, and how to read an entity's id.
 * Reads and writes are synchronized because the reminder daemon reads the
 * same files while the console thread writes them.
 */
public abstract class CsvRepository<T> implements Repository<T> {

    protected final Path file;
    protected final AppLogger log = AppLogger.getInstance();
    private final List<T> cache = new ArrayList<>();
    private boolean loaded = false;

    protected CsvRepository(String fileName) {
        this.file = Paths.get("data", fileName);
        ensureFile();
    }

    /* ---- template methods implemented by subclasses ---- */
    protected abstract String toCsv(T entity);
    protected abstract T fromCsv(String line);
    protected abstract String idOf(T entity);
    protected abstract String header();

    private void ensureFile() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(file)) {
                Files.writeString(file, "# " + header() + System.lineSeparator(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Could not create data file " + file, e);
        }
    }

    /** Lazy load; subsequent calls are served from the in-memory cache. */
    protected synchronized List<T> load() throws DataAccessException {
        if (loaded) return cache;
        cache.clear();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.startsWith("#")) continue;
                try {
                    T entity = fromCsv(line);
                    if (entity != null) cache.add(entity);
                } catch (RuntimeException parseError) {
                    // A corrupt row must not bring the whole application down.
                    log.warn("Skipping malformed row " + lineNo + " in " + file.getFileName()
                            + ": " + parseError.getMessage());
                }
            }
            loaded = true;
            return cache;
        } catch (IOException e) {
            throw new DataAccessException("Unable to read " + file, e);
        }
    }

    /** Full rewrite. Small dataset, so simplicity beats an append-only log here. */
    protected synchronized void flush() throws DataAccessException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# " + header());
            writer.newLine();
            for (T entity : cache) {
                writer.write(toCsv(entity));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new DataAccessException("Unable to write " + file, e);
        }
    }

    @Override
    public synchronized T save(T entity) throws DataAccessException {
        load();
        cache.add(entity);
        flush();
        return entity;
    }

    @Override
    public synchronized void update(T entity) throws DataAccessException {
        load();
        String id = idOf(entity);
        for (int i = 0; i < cache.size(); i++) {
            if (idOf(cache.get(i)).equals(id)) {
                cache.set(i, entity);
                flush();
                return;
            }
        }
        throw new DataAccessException("Update failed, id not present: " + id, null);
    }

    @Override
    public synchronized boolean deleteById(String id) throws DataAccessException {
        load();
        boolean removed = cache.removeIf(e -> idOf(e).equals(id));
        if (removed) flush();
        return removed;
    }

    @Override
    public synchronized Optional<T> findById(String id) throws DataAccessException {
        for (T entity : load()) {
            if (idOf(entity).equals(id)) return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<T> findAll() throws DataAccessException {
        return new ArrayList<>(load());
    }

    /** Test/utility hook: drop the cache so the next read hits disk. */
    public synchronized void invalidate() {
        loaded = false;
        cache.clear();
    }
}

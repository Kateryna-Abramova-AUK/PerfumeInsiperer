package com.scentmood;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
class LocalShortlistRepository implements ShortlistRepository {
    private final Path shortlistDirectory;

    LocalShortlistRepository(@Value("${scent-mood.shortlist-directory}") Path shortlistDirectory)
        throws IOException {
        this.shortlistDirectory = Files.createDirectories(shortlistDirectory);
    }

    @Override
    public synchronized List<String> saved(UUID visitor) {
        return savedIds(visitor);
    }

    @Override
    public synchronized void save(UUID visitor, String id) {
        var ids = new ArrayList<>(savedIds(visitor));
        if (!ids.contains(id)) {
            ids.addFirst(id);
            writeIds(visitor, ids);
        }
    }

    @Override
    public synchronized void remove(UUID visitor, String id) {
        var ids = new ArrayList<>(savedIds(visitor));
        if (ids.remove(id)) writeIds(visitor, ids);
    }

    @Override
    public boolean healthy() {
        return Files.isWritable(shortlistDirectory);
    }

    private Path shortlistFile(UUID visitor) {
        return shortlistDirectory.resolve(visitor + ".txt");
    }

    private List<String> savedIds(UUID visitor) {
        try {
            return Files.readAllLines(shortlistFile(visitor));
        } catch (NoSuchFileException missing) {
            return List.of();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read the shortlist", failure);
        }
    }

    private void writeIds(UUID visitor, List<String> ids) {
        try {
            Path temporary = Files.createTempFile(shortlistDirectory, "shortlist-", ".tmp");
            try {
                Files.write(temporary, ids);
                Files.move(temporary, shortlistFile(visitor), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not update the shortlist", failure);
        }
    }
}

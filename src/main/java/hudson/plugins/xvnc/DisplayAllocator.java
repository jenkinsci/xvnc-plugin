package hudson.plugins.xvnc;

import hudson.model.Saveable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the display numbers in use.
 *
 * @author Kohsuke Kawaguchi
 */
final class DisplayAllocator {

    transient Saveable owner;

    /**
     * Display numbers in use.
     */
    private final Set<Integer> allocatedNumbers = new HashSet<Integer>();
    private final Set<Integer> blacklistedNumbers = new HashSet<Integer>();

    public DisplayAllocator() {
    }

    private void save() {
        if (owner != null) {
            try {
                owner.save();
            } catch (IOException x) {
                Logger.getLogger(DisplayAllocator.class.getName()).log(Level.WARNING, null, x);
            }
        }
    }

    private int getRandomValue(final int min, final int max) {
        return min + (new Random().nextInt(getRange(min, max)));
    }

    private int getRange(final int min, final int max) {
        return (max + 1) - min;
    }

    public int allocate(final int minDisplayNumber, final int maxDisplayNumber) {
        try {
            return doAllocate(minDisplayNumber, maxDisplayNumber);
        } finally {
            save();
        }
    }
    private synchronized int doAllocate(final int minDisplayNumber, final int maxDisplayNumber) {
        if (noDisplayNumbersLeft(minDisplayNumber, maxDisplayNumber)) {
            if (!blacklistedNumbers.isEmpty()) {
                blacklistedNumbers.clear();
            } else {
                throw new RuntimeException("All available display numbers are allocated or " +
                        "blacklisted.\nallocated: " + allocatedNumbers.toString() +
                        "\nblacklisted: " + blacklistedNumbers.toString());
            }
        }
        int displayNumber;
        do {
            displayNumber = getRandomValue(minDisplayNumber, maxDisplayNumber);
        } while(isNotAvailable(displayNumber));
        allocatedNumbers.add(displayNumber);
        return displayNumber;
    }

    private boolean isNotAvailable(int number) {
        return allocatedNumbers.contains(number) || blacklistedNumbers.contains(number);
    }

    private boolean noDisplayNumbersLeft(final int min, final int max) {
        return allocatedNumbers.size() + blacklistedNumbers.size() >= getRange(min, max);
    }

    public void free(int n) {
        synchronized (this) {
            allocatedNumbers.remove(n);
        }
        save();
    }

    public void blacklist(int badDisplay) {
        synchronized (this) {
            allocatedNumbers.remove(badDisplay);
            blacklistedNumbers.add(badDisplay);
        }
        save();
    }

}

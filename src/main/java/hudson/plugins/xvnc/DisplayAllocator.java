package hudson.plugins.xvnc;

import java.util.Random;
import java.util.Set;
import java.util.HashSet;

/**
 * Manages the display numbers in use.
 *
 * @author Kohsuke Kawaguchi
 */
final class DisplayAllocator {
    /**
     * Display numbers in use.
     */
    private final Set<Integer> allocatedNumbers = new HashSet<Integer>();
    private final Set<Integer> blacklistedNumbers = new HashSet<Integer>();
    private final int minDisplayNumber;
    private final int maxDisplayNumber;

    public DisplayAllocator(final int minDisplayNumber, final int maxDisplayNumber) {
        this.minDisplayNumber = minDisplayNumber;
        this.maxDisplayNumber = maxDisplayNumber;
    }

    private final int getRandomValue() {
        return minDisplayNumber + (new Random().nextInt(getRange()));
    }

    private int getRange() {
        return (maxDisplayNumber + 1) - minDisplayNumber;
    }

    public synchronized int allocate() {
        if (noDisplayNumbersLeft()) {
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
            displayNumber = getRandomValue();
        } while(isNotAvailable(displayNumber));
        allocatedNumbers.add(displayNumber);
        return displayNumber;
    }

    private boolean isNotAvailable(int number) {
        return allocatedNumbers.contains(number) || blacklistedNumbers.contains(number);
    }

    private boolean noDisplayNumbersLeft() {
        return allocatedNumbers.size() + blacklistedNumbers.size() >= getRange();
    }

    public synchronized void free(int n) {
        allocatedNumbers.remove(n);
    }

    public void blacklist(int badDisplay) {
        free(badDisplay);
        blacklistedNumbers.add(badDisplay);
    }
}

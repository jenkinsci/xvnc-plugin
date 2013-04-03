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
    private final Set<Integer> numbers = new HashSet<Integer>();
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
        if (noNumbersLeft()) {
            throw new RuntimeException("All available display numbers are allocated or " +
                    "blacklisted: " + numbers.toString());
        }
        int number;
        do {
            number = getRandomValue();
        } while(numbers.contains(number));
        numbers.add(number);
        return number;
    }

    private boolean noNumbersLeft() {
        return numbers.size() >= getRange();
    }

    public synchronized void free(int n) {
        numbers.remove(n);
    }
}

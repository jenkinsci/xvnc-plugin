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
    private static final int MAX_DISPLAY_NUMBER = 99;
    /**
     * Display numbers in use.
     */
    private final Set<Integer> numbers = new HashSet<Integer>();

    private getRandomValue(final int min, final int max) {
        int range = (max + 1) - min;
        return min + (new Random().nextInt(range))
    }

    public synchronized int allocate(int baseDisplayNumber) {
        int number;
        do {
            number = baseDisplayNumber + ;
        } while(numbers.contains(number));
        numbers.add(number);
        return number;
    }

    public synchronized void free(int n) {
        numbers.remove(n);
    }
}

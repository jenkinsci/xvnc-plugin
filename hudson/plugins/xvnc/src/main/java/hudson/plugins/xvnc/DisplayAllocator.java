package hudson.plugins.xvnc;

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

    public synchronized int allocate(int baseDisplayNumber) {
        int i = baseDisplayNumber; 
        while(numbers.contains(i))
            i++;
        numbers.add(i);
        return i;
    }

    public synchronized void free(int n) {
        numbers.remove(n);
    }
}

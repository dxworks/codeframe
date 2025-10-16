package samples.java;

import java.util.List;
import java.util.Optional;

public class GenericsSample {
    public <T extends Comparable<T>> T findMax(List<T> items) {
        return items.stream()
                .max(T::compareTo)
                .orElse(null);
    }

    public <T> Optional<T> findFirst(List<T> items) {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    public void processWildcard(List<? extends Number> numbers) {
        for (Number n : numbers) {
            System.out.println(n.doubleValue());
        }
    }

    public <K, V> void addToMap(GenericContainer<K, V> container, K key, V value) {
        container.put(key, value);
    }
}

class GenericContainer<K, V> {
    private K key;
    private V value;

    public void put(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public V get(K key) {
        return this.key.equals(key) ? value : null;
    }
}

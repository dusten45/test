package android.util;

import java.util.ArrayList;
import java.util.TreeMap;

public class SparseArray<E> implements Cloneable {
    private final TreeMap<Integer, E> values = new TreeMap<>();
    public SparseArray() { }
    public SparseArray(int initialCapacity) { }
    public E get(int key) { return values.get(key); }
    public E get(int key, E defaultValue) { return values.getOrDefault(key, defaultValue); }
    public void put(int key, E value) { values.put(key, value); }
    public void append(int key, E value) { put(key, value); }
    public void delete(int key) { values.remove(key); }
    public void remove(int key) { values.remove(key); }
    public void removeAt(int index) { values.remove(keyAt(index)); }
    public void clear() { values.clear(); }
    public int size() { return values.size(); }
    public int keyAt(int index) { return new ArrayList<>(values.keySet()).get(index); }
    public E valueAt(int index) { return values.get(keyAt(index)); }
    public void setValueAt(int index, E value) { values.put(keyAt(index), value); }
    public int indexOfKey(int key) { return java.util.Collections.binarySearch(new ArrayList<>(values.keySet()), key); }
    public int indexOfValue(E value) {
        int index = 0;
        for (E item : values.values()) { if (item == value) { return index; } index++; }
        return -1;
    }
    @Override public SparseArray<E> clone() { SparseArray<E> clone = new SparseArray<>(); clone.values.putAll(values); return clone; }
}

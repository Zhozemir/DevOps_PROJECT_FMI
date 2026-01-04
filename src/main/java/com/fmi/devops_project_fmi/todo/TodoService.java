package com.fmi.devops_project_fmi.todo;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TodoService {

    private final AtomicLong idGenerator = new AtomicLong(0);
    private final Map<Long, Todo> storage = new ConcurrentHashMap<>();

    public Todo create(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }

        long id = idGenerator.incrementAndGet();
        Todo todo = new Todo(id, title.trim(), false);
        storage.put(id, todo);
        return todo;
    }

    public List<Todo> list() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(Todo::id))
                .toList();
    }

    public boolean delete(long id) {
        return storage.remove(id) != null;
    }

    public void clear() {
        storage.clear();
        idGenerator.set(0);
    }

}

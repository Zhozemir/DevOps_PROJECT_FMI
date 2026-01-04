package com.fmi.devops_project_fmi.controller;

import com.fmi.devops_project_fmi.todo.CreateTodoRequest;
import com.fmi.devops_project_fmi.todo.Todo;
import com.fmi.devops_project_fmi.todo.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Todo create(@RequestBody CreateTodoRequest request) {
        return todoService.create(request.title());
    }

    @GetMapping
    public List<Todo> list() {
        return todoService.list();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        boolean deleted = todoService.delete(id);
        if (!deleted) {
            throw new TodoNotFoundException();
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class TodoNotFoundException extends RuntimeException { }
}

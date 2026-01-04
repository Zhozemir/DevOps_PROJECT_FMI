package com.fmi.devops_project_fmi.controller;

import com.fmi.devops_project_fmi.todo.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TodoService todoService;

    @BeforeEach
    void setUp() {}

    @Test
    void create_returns201AndTodoJson() throws Exception {
        when(todoService.create("Buy milk"))
                .thenReturn(new com.fmi.devops_project_fmi.todo.Todo(1L, "Buy milk", false));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Buy milk\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.done").value(false));

        verify(todoService, times(1)).create("Buy milk");
    }

    @Test
    void list_returns200AndArray() throws Exception {
        when(todoService.list()).thenReturn(List.of(
                new com.fmi.devops_project_fmi.todo.Todo(1L, "A", false),
                new com.fmi.devops_project_fmi.todo.Todo(2L, "B", true)
        ));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("A"))
                .andExpect(jsonPath("$[0].done").value(false))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].title").value("B"))
                .andExpect(jsonPath("$[1].done").value(true));

        verify(todoService, times(1)).list();
    }

    @Test
    void delete_existing_returns204() throws Exception {
        when(todoService.delete(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/todos/1"))
                .andExpect(status().isNoContent());

        verify(todoService, times(1)).delete(1L);
    }

    @Test
    void delete_missing_returns404() throws Exception {
        when(todoService.delete(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/todos/999"))
                .andExpect(status().isNotFound());

        verify(todoService, times(1)).delete(999L);
    }

}

package com.ExpenseTracker.infrastructure.ai.tools;

import com.ExpenseTracker.util.enums.ChatActionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Acumula propuestas de acciones que las tools generan durante una llamada al
 * chat. ChatService llama start() antes de invocar al modelo y drain() después
 * para persistir las propuestas asociadas al mensaje del asistente.
 *
 * Usa ThreadLocal porque Spring AI ejecuta las tools en el mismo hilo del
 * request del chat.
 */
@Component
public class PendingActionCollector {

    private final ThreadLocal<List<Pending>> current = ThreadLocal.withInitial(ArrayList::new);

    public void start() {
        current.get().clear();
    }

    public void add(ChatActionType type, String summary, Map<String, Object> payload) {
        current.get().add(new Pending(type, summary, payload));
    }

    public List<Pending> drain() {
        List<Pending> snapshot = new ArrayList<>(current.get());
        current.remove();
        return snapshot;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Pending {
        private final ChatActionType type;
        private final String summary;
        private final Map<String, Object> payload;
    }
}

package dev.rafael.dailyknowledge;

/** Fonte de descobertas (implementação real: {@link AgyClient}). */
public interface Researcher {

    Discovery research(String prompt, int attempt) throws ResearchException;

    final class ResearchException extends Exception {
        public ResearchException(String message) {
            super(message);
        }

        public ResearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

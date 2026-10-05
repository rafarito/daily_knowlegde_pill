package dev.rafael.dailyknowledge;

import java.util.List;

/** Fábrica de objetos para testes. */
final class Fixtures {

    private Fixtures() {
    }

    static Discovery discovery(String name, String url) {
        return new Discovery(name, url, "https://example.org", "cli", true, "MIT", "Rust",
                "Uma ferramenta <útil> & rápida", "Faz coisas.", "Resolve dores.", "Criado por alguém em 2020.",
                "Porque sim.", "cargo install " + name.toLowerCase(), List.of("Alt1", "Alt2"),
                List.of("https://github.com/x/y", "https://example.org/docs"));
    }

    static Discovery minimal(String name, String url) {
        return new Discovery(name, url, null, "cli", false, "MIT", "Go",
                "tagline", "Faz coisas.", "Resolve dores.", null,
                "Porque sim.", "go install x", List.of(), List.of());
    }
}

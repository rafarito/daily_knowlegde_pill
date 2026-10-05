package dev.rafael.dailyknowledge;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Verifica se a URL existe. Para repositórios do GitHub usa a API pública (seguindo
 * redirecionamentos de renomeação/transferência); para o resto, HEAD com fallback para GET.
 */
public class Verifier {

    private static final String UA = "Mozilla/5.0 (X11; Linux x86_64) daily-knowledge/1.0";

    private final HttpClient http;
    private final String githubApiBase;
    private final String githubToken;

    public Verifier(String githubToken) {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build(), "https://api.github.com", githubToken);
    }

    Verifier(HttpClient http, String githubApiBase, String githubToken) {
        this.http = http;
        this.githubApiBase = githubApiBase;
        this.githubToken = githubToken;
    }

    public Verification verify(String url) {
        Optional<String> repo = UrlNormalizer.githubRepo(url);
        if (repo.isPresent()) {
            Optional<Verification> gh = fromGithubApi(repo.get());
            if (gh.isPresent()) {
                return gh.get();
            }
            Log.warn("API do GitHub indisponível para " + repo.get() + "; caindo para HEAD/GET");
        }
        return isReachable(url) ? Verification.reachable(url) : Verification.notFound("URL inacessível: " + url);
    }

    /** Vazio quando a API não deu uma resposta conclusiva (rate limit, erro de rede, 5xx). */
    Optional<Verification> fromGithubApi(String ownerRepo) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(githubApiBase + "/repos/" + ownerRepo))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", UA)
                    .timeout(Duration.ofSeconds(20))
                    .GET();
            
            if (githubToken != null && !githubToken.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + githubToken);
            }
            
            HttpRequest req = reqBuilder.build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                return Optional.of(Verification.notFound("repositório não existe no GitHub: " + ownerRepo));
            }
            if (res.statusCode() != 200) {
                Log.warn("API do GitHub respondeu " + res.statusCode() + " para " + ownerRepo);
                return Optional.empty();
            }
            JsonNode n = Json.MAPPER.readTree(res.body());
            String spdx = n.path("license").path("spdx_id").asText(null);
            if (spdx != null && (spdx.isBlank() || spdx.equals("NOASSERTION"))) {
                spdx = null;
            }
            return Optional.of(new Verification(true,
                    n.path("html_url").asText("https://github.com/" + ownerRepo),
                    n.has("stargazers_count") ? n.get("stargazers_count").asInt() : null,
                    spdx,
                    n.path("pushed_at").asText(null),
                    null));
        } catch (IOException e) {
            Log.warn("Erro ao consultar a API do GitHub: " + e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    boolean isReachable(String url) {
        try {
            URI uri = URI.create(url.strip());
            if (status(uri, "HEAD") < 400) {
                return true;
            }
            return status(uri, "GET") < 400;
        } catch (IllegalArgumentException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private int status(URI uri, String method) throws InterruptedException {
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .header("User-Agent", UA)
                    .timeout(Duration.ofSeconds(20))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return 599;
        }
    }
}

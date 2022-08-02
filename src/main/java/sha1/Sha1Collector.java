package sha1;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

public final class Sha1Collector {
    private final HttpClient client;
    private final static Pattern DIR_PATTERN = Pattern.compile("href=\"([\\w\\.-]+)/\"");
    private final static Pattern SHA_PATTERN = Pattern.compile("href=\"([\\w\\.-]+)\\.sha1\"");
    private final static Pattern DDOT_PATTERN = Pattern.compile("\\.\\.");
    private final static Pattern JAVADOC_PATTERN = Pattern.compile("javadoc");
    private final static Pattern SOURCES_PATTERN = Pattern.compile("sources");
    private final static Pattern POM_PATTERN = Pattern.compile("pom");

    public Sha1Collector(HttpClient client) {
        this.client = client;
    }

    public static void main(String[] args) {
        String location;
        if (args.length < 1) {
            location = "https://maven.repository.redhat.com/ga/org/jboss/modules/jboss-modules/";
        } else {
            location = args[0];
        }

        var client = HttpClient.newBuilder()
//                .sslContext(sslContext)
                .build();

        var self = new Sha1Collector(client);
        self.run(location);
    }

    void run(String base) {
        var location = base;
        try {
            findJarShas(location).forEach(System.out::println);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<String> findJarShas(String location) {
        try {
            var uri = new URI(location);
            var req = HttpRequest.newBuilder(uri).build();

            var response = client.send(req,
                HttpResponse.BodyHandlers.ofString(Charset.defaultCharset()));

            System.out.println(location + ": " + response.version());
            var body = response.body();
            var shaUrls = parseBodyForShas(body, location);
            shaUrls.addAll(parseBodyForUrls(body).stream().map(s -> location+ s).flatMap(s -> findJarShas(s).stream()).collect(toList()));
            return shaUrls;
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    List<String> parseBodyForShas(String body, String location) {
        var out = new ArrayList<String>();
        var m = SHA_PATTERN.matcher(body);

        while (m.find()) {
            var relativeUrl = m.group(1);
            if (!JAVADOC_PATTERN.matcher(relativeUrl).find()
                && !SOURCES_PATTERN.matcher(relativeUrl).find()
                && !POM_PATTERN.matcher(relativeUrl).find()) {
                out.add(location + relativeUrl + ".sha1");
            }
        }

        return out;
    }

    List<String> parseBodyForUrls(String body) {
        var out = new ArrayList<String>();
        var m = DIR_PATTERN.matcher(body);
        while (m.find()) {
            var relativeUrl = m.group(1);
            // Prevent backtracking
            if (!DDOT_PATTERN.matcher(relativeUrl).find()) {
                out.add(relativeUrl +"/");
            }
        }

        return out;
    }

}

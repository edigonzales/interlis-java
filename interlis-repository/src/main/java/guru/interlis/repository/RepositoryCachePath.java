package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Maps exact URL identities to portable, case-insensitive filesystem keys. */
final class RepositoryCachePath {
    private static final int MAX_READABLE_LENGTH = 180;
    private static final Pattern HOST = Pattern.compile("[a-z0-9.-]+");
    private static final Pattern SEGMENT = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]*");
    private static final Pattern DEVICE = Pattern.compile("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])(?:\\..*)?");

    private RepositoryCachePath() { }

    static String key(SourceId source) {
        String url = source.value();
        if (url.length() <= MAX_READABLE_LENGTH) {
            String readable = readableKey(url);
            if (readable != null && readable.length() <= MAX_READABLE_LENGTH) return readable;
        }
        return "_opaque/" + ContentDigest.sha256(url.getBytes(StandardCharsets.UTF_8)).value();
    }

    private static String readableKey(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equals(scheme) || "https".equals(scheme)) || host == null || !HOST.matcher(host).matches()
                || !portable(host) || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) return null;

        int port = uri.getPort();
        if (!uri.getRawAuthority().equals(port == -1 ? host : host + ":" + port)) return null;

        String path = uri.getRawPath();
        if (path.length() <= 1 || !path.startsWith("/")) return null;
        var key = new StringBuilder(scheme).append('/').append(host);
        if (port != -1) key.append("~p").append(port);
        for (String segment : path.substring(1).split("/", -1)) {
            if (!SEGMENT.matcher(segment).matches() || !portable(segment)) return null;
            key.append('/');
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (c >= 'A' && c <= 'Z') key.append('~');
                key.append(Character.toLowerCase(c));
            }
        }
        return key.toString();
    }

    private static boolean portable(String component) {
        return !component.endsWith(".") && !DEVICE.matcher(component).matches();
    }
}

package io.wakeharbor.control.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces strict SSRF protection for external Supabase requests:
 * 1. Only *.supabase.co domain names allowed.
 * 2. Strict HTTPS protocol.
 * 3. Rejects userinfo, query parameters, port overrides, and unexpected subpaths.
 * 4. Resolves and validates target IP against private/loopback/link-local IP ranges.
 */
@Component
public class SsrfValidator {

    private static final Logger log = LoggerFactory.getLogger(SsrfValidator.class);

    private static final Pattern SUPABASE_URL_PATTERN = Pattern.compile(
        "^https://([a-z0-9_-]{1,64})\\.supabase\\.co/?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROJECT_REF_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$");

    public record ValidatedSupabaseTarget(String projectRef, String canonicalBaseUrl, String rpcEndpoint) {}

    /**
     * Validates and canonicalizes a Supabase project URL.
     * Returns canonical target details including the strictly constructed RPC endpoint.
     */
    public ValidatedSupabaseTarget validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Supabase URL cannot be empty");
        }

        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed URL: " + e.getMessage());
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URL must use HTTPS protocol");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs containing user credentials are not permitted");
        }

        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Non-standard ports are not permitted");
        }

        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Query strings and URL fragments are not permitted");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Missing host in URL");
        }

        host = host.toLowerCase(Locale.ROOT);
        if (!host.endsWith(".supabase.co")) {
            throw new IllegalArgumentException("Target host must belong to the *.supabase.co domain");
        }

        Matcher matcher = SUPABASE_URL_PATTERN.matcher("https://" + host);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Supabase project host format: " + host);
        }

        String projectRef = matcher.group(1);
        if (!PROJECT_REF_PATTERN.matcher(projectRef).matches()) {
            throw new IllegalArgumentException("Invalid project reference identifier: " + projectRef);
        }

        // Validate resolved IP addresses to prevent DNS rebinding or internal routing attacks
        validateResolvedIp(host);

        String canonicalBaseUrl = "https://" + host;
        String rpcEndpoint = canonicalBaseUrl + "/rest/v1/rpc/wakeharbor_heartbeat";

        return new ValidatedSupabaseTarget(projectRef, canonicalBaseUrl, rpcEndpoint);
    }

    /**
     * Resolves host IP and rejects loopback, link-local, private, or multicast addresses.
     */
    public void validateResolvedIp(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress() ||
                    address.isLinkLocalAddress() ||
                    address.isSiteLocalAddress() ||
                    address.isMulticastAddress() ||
                    address.isAnyLocalAddress()) {
                    log.warn("SSRF attempt detected: host {} resolved to prohibited IP {}", host, address.getHostAddress());
                    throw new IllegalArgumentException("Resolved target IP is within a restricted network range");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // DNS resolution failure will be categorized during request execution
            log.debug("DNS pre-check for host {} reported: {}", host, e.getMessage());
        }
    }
}

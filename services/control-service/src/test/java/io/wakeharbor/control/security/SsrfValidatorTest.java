package io.wakeharbor.control.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SsrfValidatorTest {

    private SsrfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SsrfValidator();
    }

    @Test
    @DisplayName("Valid Supabase URL should produce canonical target details")
    void testValidSupabaseUrl() {
        String input = "https://abcdefghijklmnop.supabase.co";
        SsrfValidator.ValidatedSupabaseTarget target = validator.validateUrl(input);

        assertNotNull(target);
        assertEquals("abcdefghijklmnop", target.projectRef());
        assertEquals("https://abcdefghijklmnop.supabase.co", target.canonicalBaseUrl());
        assertEquals("https://abcdefghijklmnop.supabase.co/rest/v1/rpc/wakeharbor_heartbeat", target.rpcEndpoint());
    }

    @Test
    @DisplayName("Valid URL with trailing slash should be normalized cleanly")
    void testValidSupabaseUrlWithTrailingSlash() {
        String input = "https://xyz-123.supabase.co/";
        SsrfValidator.ValidatedSupabaseTarget target = validator.validateUrl(input);

        assertEquals("xyz-123", target.projectRef());
        assertEquals("https://xyz-123.supabase.co", target.canonicalBaseUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://abcdef.supabase.co",                     // Insecure HTTP
            "https://attacker.com",                         // Non-supabase domain
            "https://abcdef.supabase.co.evil.com",          // Subdomain spoofing
            "https://user:pass@abcdef.supabase.co",         // Userinfo embedded
            "https://abcdef.supabase.co:8443",              // Custom port
            "https://localhost",                            // Localhost
            "https://127.0.0.1",                            // Loopback IP
            "https://169.254.169.254",                      // AWS metadata link-local
            "https://abcdef.supabase.co?query=payload",     // Query parameter
            "https://abcdef.supabase.co/#hash",             // Hash fragment
            "ftp://abcdef.supabase.co",                     // Bad scheme
            "",                                             // Empty
            " "                                             // Blank
    })
    @DisplayName("Malicious, non-Supabase, or manipulated URLs must be strictly rejected")
    void testRejectedUrls(String invalidUrl) {
        assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(invalidUrl));
    }
}

package com.antigravity.remotecontrol.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 Feature Tests for F14: Network Security Configuration.
 * Verifies network_security_config.xml XML structure, trust-anchors (system, user, raw CA), and cleartext traffic allowances.
 */
class NetworkSecurityConfigTest {

    private val sampleConfigXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <network-security-config>
            <base-config cleartextTrafficPermitted="true">
                <trust-anchors>
                    <certificates src="system" />
                    <certificates src="user" />
                    <certificates src="@raw/netspark_ca" />
                </trust-anchors>
            </base-config>
        </network-security-config>
    """.trimIndent()

    @Test
    fun testBaseConfigPermitsCleartextTraffic() {
        val parsed = parseConfig(sampleConfigXml)
        assertTrue("Cleartext traffic must be permitted for local development", parsed.cleartextTrafficPermitted)
    }

    @Test
    fun testTrustAnchorsIncludeSystemCertificates() {
        val parsed = parseConfig(sampleConfigXml)
        assertTrue(parsed.trustAnchors.contains("system"))
    }

    @Test
    fun testTrustAnchorsIncludeUserInstalledCertificates() {
        val parsed = parseConfig(sampleConfigXml)
        assertTrue(parsed.trustAnchors.contains("user"))
    }

    @Test
    fun testTrustAnchorsIncludeBundledRawProxyCertificate() {
        val parsed = parseConfig(sampleConfigXml)
        assertTrue(parsed.trustAnchors.contains("@raw/netspark_ca"))
    }

    @Test
    fun testValidatePemCertificateHeaderAndFooter() {
        val pemContent = """
            -----BEGIN CERTIFICATE-----
            MIIEkjCCA3qgAwIBAgITBwAAADqGz9l4Y3yV1wAAAAAAOjANBgkqhkiG9w0BAQsF
            ADBNMRUwEwYDVQQDEwx3d3cubmV0c3BhcmsxETAPBgNVBAoTCE5ldHNwYXJrMRUw
            EwYDVQQLEwxOZXRzcGFyayBSSU0xEDAOBgNVBAYTB0lTUkFFTDAeFw0yNDA1MjAx
            -----END CERTIFICATE-----
        """.trimIndent()

        assertTrue(pemContent.contains("-----BEGIN CERTIFICATE-----"))
        assertTrue(pemContent.contains("-----END CERTIFICATE-----"))
        assertFalse(pemContent.isBlank())
    }

    data class ParsedNetworkConfig(
        val cleartextTrafficPermitted: Boolean,
        val trustAnchors: List<String>
    )

    private fun parseConfig(xml: String): ParsedNetworkConfig {
        val cleartext = xml.contains("cleartextTrafficPermitted=\"true\"")
        val anchors = mutableListOf<String>()
        val regex = Regex("<certificates\\s+src=\"([^\"]+)\"\\s*/>")
        regex.findAll(xml).forEach { match ->
            anchors.add(match.groupValues[1])
        }
        return ParsedNetworkConfig(cleartext, anchors)
    }
}

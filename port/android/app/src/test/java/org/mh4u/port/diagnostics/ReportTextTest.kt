package org.mh4u.port.diagnostics

import org.junit.Assert.*
import org.junit.Test

class ReportTextTest {
    @Test fun reportOmitsPathsChatCredentialsAndNetworkAddressesButKeepsUsefulErrors() {
        val report = ReportText.sanitize("""
            Vulkan: failed to create swapchain
            Loaded /storage/emulated/0/Private Name/game.3ds
            nickname=MyPrivateName
            password=MyPrivatePassword
            chat: private conversation
            Connected to 192.168.1.39:1234
            Contact private@example.com
            Vulkan allocation at https://private.example/secret
        """.trimIndent())
        for (secret in listOf("Private Name", "MyPrivateName", "MyPrivatePassword", "private conversation", "192.168.1.39", "private@example.com", "private.example/secret")) assertFalse(secret, secret in report)
        assertTrue("failed to create swapchain" in report)
    }
    @Test fun logTailAndIndividualLinesAreBounded() {
        val report = ReportText.sanitize((1..1000).joinToString("\n") { "record-$it" })
        assertFalse("record-1\n" in report); assertTrue("record-1000" in report)
        assertTrue(ReportText.sanitize("x".repeat(10000)).length <= 4096)
    }
    @Test fun linksWithAddressesAndAuthenticationHeadersAreRemovedAsWholeDetails() {
        val report = ReportText.sanitize("""
            Open mh4u://192.168.1.39:5000/s3cret-value
            Connected to fe80:abcd:1234:5678:9abc:def0:1234:5678
            Authorization: Bearer my-key
            Cookie: my-session-value
            Renderer ready
        """.trimIndent())
        for (secret in listOf("s3cret-value", "192.168.1.39", "fe80:abcd", "my-key", "my-session-value")) assertFalse(secret, secret in report)
        assertTrue("Renderer ready" in report)
    }
}

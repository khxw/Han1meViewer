package com.yenaly.han1meviewer.logic

import com.yenaly.han1meviewer.logic.state.WebsiteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUserAccountTest {

    @Test
    fun parsesCurrentProfileMarkupWithFallbackSelectors() {
        val html = """
            <html>
            <body>
              <meta name="csrf-token" content="token">
              <div class="profile-avatar-wrapper">
                <img src="https://example.com/avatar.jpg">
              </div>
              <div class="profile-display-name">CurrentUser</div>
              <div class="profile-sub-stats-id">ID: 1015542</div>
              <div class="profile-sub-stats-new-line">12 subscribers · 34 videos</div>
              <input name="email" value="user@example.com">
            </body>
            </html>
        """.trimIndent()

        val state = Parser.userAccountPage(html, fallbackUserId = "1015542")

        assertTrue(state is WebsiteState.Success)
        val account = (state as WebsiteState.Success).info
        assertEquals("CurrentUser", account.username)
        assertEquals("user@example.com", account.email)
        assertEquals("1015542", account.userId)
        assertEquals("https://example.com/avatar.jpg", account.avatarUrl)
    }
}

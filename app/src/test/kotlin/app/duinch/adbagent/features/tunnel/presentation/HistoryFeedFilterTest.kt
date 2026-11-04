package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.HostCommandRecord
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryFeedFilterTest {

    private val cmd1 = HostCommandRecord(
        id = "1",
        command = "adb shell pm list packages",
        source = "Mac Studio",
        status = "success",
        timestampEpochMs = 1000L,
        durationMs = 50L,
        outputSnippet = "package:com.example.app"
    )

    private val cmdTermux = HostCommandRecord(
        id = "2",
        command = "getprop ro.build.version.release",
        source = "Embedded Termux",
        status = "success",
        timestampEpochMs = 2000L,
        durationMs = 10L
    )

    private val session1 = TunnelHistoryEntry(
        sessionId = "s1",
        startedAtEpochMs = 1500L,
        networkType = NetworkType.WIFI,
        localAdbPort = 5555,
        mappedServerPort = 15555,
        bytesBridged = 2048L
    )

    @Test
    fun `all filter returns all items sorted descending by timestamp`() {
        val feed = buildHistoryFeed(
            filter = HistoryTabFilter.ALL,
            searchQuery = "",
            commands = listOf(cmd1, cmdTermux),
            sessions = listOf(session1)
        )
        assertEquals(3, feed.size)
        // 2000L (cmdTermux), then 1500L (session1), then 1000L (cmd1)
        assertTrue(feed[0] is HistoryFeedItem.Command)
        assertEquals(2000L, feed[0].timestampEpochMs)
        assertTrue(feed[1] is HistoryFeedItem.Session)
        assertEquals(1500L, feed[1].timestampEpochMs)
        assertTrue(feed[2] is HistoryFeedItem.Command)
        assertEquals(1000L, feed[2].timestampEpochMs)
    }

    @Test
    fun `host commands filter excludes termux and sessions`() {
        val feed = buildHistoryFeed(
            filter = HistoryTabFilter.HOST_COMMANDS,
            searchQuery = "",
            commands = listOf(cmd1, cmdTermux),
            sessions = listOf(session1)
        )
        assertEquals(1, feed.size)
        val item = feed[0] as HistoryFeedItem.Command
        assertEquals("adb shell pm list packages", item.record.command)
    }

    @Test
    fun `termux filter returns only termux commands`() {
        val feed = buildHistoryFeed(
            filter = HistoryTabFilter.TERMUX,
            searchQuery = "",
            commands = listOf(cmd1, cmdTermux),
            sessions = listOf(session1)
        )
        assertEquals(1, feed.size)
        val item = feed[0] as HistoryFeedItem.Command
        assertEquals("getprop ro.build.version.release", item.record.command)
    }

    @Test
    fun `sessions filter returns only tunnel sessions`() {
        val feed = buildHistoryFeed(
            filter = HistoryTabFilter.SESSIONS,
            searchQuery = "",
            commands = listOf(cmd1, cmdTermux),
            sessions = listOf(session1)
        )
        assertEquals(1, feed.size)
        assertTrue(feed[0] is HistoryFeedItem.Session)
    }

    @Test
    fun `search query filters by command substring`() {
        val feed = buildHistoryFeed(
            filter = HistoryTabFilter.ALL,
            searchQuery = "pm list",
            commands = listOf(cmd1, cmdTermux),
            sessions = listOf(session1)
        )
        assertEquals(1, feed.size)
        val item = feed[0] as HistoryFeedItem.Command
        assertEquals("1", item.record.id)
    }

    @Test
    fun `duration formatting renders minutes and seconds or hours`() {
        assertEquals("00:45", formatDurationVi(45000L))
        assertEquals("01:15", formatDurationVi(75000L))
        assertEquals("01:01:05", formatDurationVi(3665000L))
    }
}

package dev.achmad.finbox.core.gmail

import org.junit.Assert.assertEquals
import org.junit.Test

class GmailApiTest {

    @Test
    fun `each table row becomes its own line, so a label keeps its value`() {
        val text = GmailApi.htmlToText(
            """
            <html><head><style>.total{font-weight:700}</style></head><body>
            <table>
              <tr><th>Nomor Referensi</th><td>192779074268</td></tr>
              <tr><th>Tanggal Transaksi</th><td>11 Aug 2026&#44; 10:30:27 WIB</td></tr>
            </table>
            <p>Nominal<br>Rp13.000</p>
            </body></html>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "Nomor Referensi 192779074268",
                "Tanggal Transaksi 11 Aug 2026, 10:30:27 WIB",
                "Nominal",
                "Rp13.000",
            ),
            text.lines(),
        )
    }
}

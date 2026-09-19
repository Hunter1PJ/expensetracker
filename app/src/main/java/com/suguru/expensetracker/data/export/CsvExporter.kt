package com.suguru.expensetracker.data.export

import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.export.ExportTransactionRow
import com.suguru.expensetracker.domain.util.MoneyParser
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * High-performance, RFC 4180 compliant CSV stream exporter.
 *
 * Emits exact minor-unit money values formatted as machine-readable decimal strings
 * without any Float/Double intermediate representations.
 */
class CsvExporter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * CSV Column Schema:
     * transaction_id,date,time,type,amount,currency,source_account,destination_account,category,note,recurring
     */
    fun exportToStream(
        transactions: List<ExportTransactionRow>,
        outputStream: OutputStream,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        writer.use { out ->
            // RFC 4180 Headers with CRLF
            out.write("transaction_id,date,time,type,amount,currency,source_account,destination_account,category,note,recurring\r\n")

            for (tx in transactions) {
                val zonedDateTime = tx.transactionTime.atZone(zoneId)
                val dateStr = zonedDateTime.format(dateFormatter)
                val timeStr = zonedDateTime.format(timeFormatter)
                val typeStr = tx.type.name
                val amountStr = formatMoneyForCsv(tx.amount)
                val currencyStr = tx.currencyCode
                val sourceAccountStr = tx.sourceAccountName
                val destAccountStr = tx.destinationAccountName ?: ""
                val categoryStr = tx.categoryName ?: ""
                val noteStr = tx.note ?: ""
                val recurringStr = if (tx.recurringRuleId != null) "true" else "false"

                val row = listOf(
                    tx.transactionId.toString(),
                    dateStr,
                    timeStr,
                    typeStr,
                    amountStr,
                    currencyStr,
                    sourceAccountStr,
                    destAccountStr,
                    categoryStr,
                    noteStr,
                    recurringStr
                )

                val line = row.joinToString(",") { escapeCsvField(it) }
                out.write(line)
                out.write("\r\n")
            }
            out.flush()
        }
    }

    fun exportToString(
        transactions: List<ExportTransactionRow>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val stream = java.io.ByteArrayOutputStream()
        exportToStream(transactions, stream, zoneId)
        return stream.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Formats [Money] to machine-readable string with dot decimal separator and zero Float/Double conversions.
     */
    fun formatMoneyForCsv(money: Money): String {
        val decimals = MoneyParser.getDecimalPlaces(money.currencyCode)
        val isNegative = money.amountInMinorUnits < 0L
        val absoluteUnits = if (isNegative) -money.amountInMinorUnits else money.amountInMinorUnits
        val sign = if (isNegative) "-" else ""

        return if (decimals == 0) {
            "$sign$absoluteUnits"
        } else {
            var divisor = 1L
            for (i in 0 until decimals) {
                divisor *= 10L
            }
            val wholePart = absoluteUnits / divisor
            val fractionPart = absoluteUnits % divisor
            val fractionStr = fractionPart.toString().padStart(decimals, '0')
            "$sign$wholePart.$fractionStr"
        }
    }

    /**
     * RFC 4180 Escaping:
     * - If field contains comma, double-quote, CR, or LF, wrap in double quotes.
     * - Replace embedded double-quotes with double double-quotes (" -> "").
     */
    fun escapeCsvField(field: String): String {
        val containsSpecial = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (containsSpecial) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }
}

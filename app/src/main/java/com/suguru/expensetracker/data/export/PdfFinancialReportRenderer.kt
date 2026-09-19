package com.suguru.expensetracker.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.suguru.expensetracker.domain.model.Money
import com.suguru.expensetracker.domain.model.TransactionType
import com.suguru.expensetracker.domain.model.export.ExportTransactionRow
import com.suguru.expensetracker.domain.model.export.FinancialReportData
import com.suguru.expensetracker.domain.util.MoneyParser
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure Android-native PDF financial report renderer using [PdfDocument].
 *
 * Produces clean, highly readable, multi-page financial statements with:
 * - Isolated per-currency summaries
 * - Category breakdown distributions
 * - Paginated transaction tables
 * - Safe platform typography and Unicode rendering
 */
class PdfFinancialReportRenderer {

    // Standard A4 dimensions at 72 DPI (595 x 842 points)
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth = pageWidth - (margin * 2)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun renderReportToStream(
        reportData: FinancialReportData,
        outputStream: OutputStream,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val document = PdfDocument()
        try {
            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(24, 28, 36)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(100, 110, 130)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val headerPaint = Paint().apply {
                color = Color.rgb(30, 36, 50)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(40, 45, 55)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val bodyBoldPaint = Paint().apply {
                color = Color.rgb(30, 36, 50)
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val incomePaint = Paint().apply {
                color = Color.rgb(16, 140, 75) // Rich Green
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val expensePaint = Paint().apply {
                color = Color.rgb(205, 45, 45) // Rich Red
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val transferPaint = Paint().apply {
                color = Color.rgb(70, 95, 200) // Rich Blue
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.rgb(220, 225, 235)
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

            val bgBoxPaint = Paint().apply {
                color = Color.rgb(245, 247, 250)
                style = Paint.Style.FILL
            }

            var currentY = margin + 15f

            fun drawHeaderAndMetadata() {
                canvas.drawText("ExpenseTracker Financial Report", margin, currentY, titlePaint)
                currentY += 16f

                val generatedStr = "Generated on: ${dateTimeFormatter.format(reportData.generatedAt.atZone(zoneId))}"
                val rangeStr = if (reportData.filter.startDate != null && reportData.filter.endDate != null) {
                    "Period: ${reportData.filter.startDate} to ${reportData.filter.endDate}"
                } else if (reportData.filter.startDate != null) {
                    "Period: From ${reportData.filter.startDate}"
                } else if (reportData.filter.endDate != null) {
                    "Period: All Time"
                } else {
                    "Period: All Time"
                }

                canvas.drawText("$rangeStr  •  $generatedStr", margin, currentY, subtitlePaint)
                currentY += 14f

                // Applied filters notice if applicable
                if (reportData.filter.isFiltered) {
                    val filterDetails = mutableListOf<String>()
                    if (reportData.filter.transactionTypes.isNotEmpty()) {
                        filterDetails.add("Types: ${reportData.filter.transactionTypes.joinToString { it.name }}")
                    }
                    if (reportData.filter.currencyCodes.isNotEmpty()) {
                        filterDetails.add("Currencies: ${reportData.filter.currencyCodes.joinToString()}")
                    }
                    val filterSummaryStr = "Filters: ${filterDetails.joinToString(" | ").ifEmpty { "Custom Date/Selection" }}"
                    canvas.drawText(filterSummaryStr, margin, currentY, subtitlePaint)
                    currentY += 14f
                }

                canvas.drawLine(margin, currentY, margin + contentWidth, currentY, linePaint)
                currentY += 16f
            }

            fun drawFooter(pageNum: Int) {
                val footerY = pageHeight - margin + 10f
                canvas.drawLine(margin, footerY - 14f, margin + contentWidth, footerY - 14f, linePaint)
                canvas.drawText("ExpenseTracker  •  Confidential Financial Record", margin, footerY, subtitlePaint)
                val pageStr = "Page $pageNum"
                val pageMeasure = subtitlePaint.measureText(pageStr)
                canvas.drawText(pageStr, margin + contentWidth - pageMeasure, footerY, subtitlePaint)
            }

            fun startNewPage() {
                drawFooter(currentPageNumber)
                document.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = margin + 15f
            }

            // Draw initial header
            drawHeaderAndMetadata()

            // 1. Currency Summaries Section
            if (reportData.currencySummaries.isNotEmpty()) {
                canvas.drawText("Financial Summary by Currency", margin, currentY, headerPaint)
                currentY += 14f

                for (summary in reportData.currencySummaries) {
                    if (currentY + 65f > pageHeight - margin - 30f) {
                        startNewPage()
                        canvas.drawText("Financial Summary by Currency (cont.)", margin, currentY, headerPaint)
                        currentY += 14f
                    }

                    // Background card for currency
                    canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 54f, 6f, 6f, bgBoxPaint)

                    val currTitle = "${summary.currencyCode} Totals (${summary.transactionCount} transactions)"
                    canvas.drawText(currTitle, margin + 12f, currentY + 16f, bodyBoldPaint)

                    val incomeStr = "+ ${MoneyParser.format(summary.totalIncome, includeSymbol = true, useGrouping = true)}"
                    val expenseStr = "- ${MoneyParser.format(summary.totalExpense, includeSymbol = true, useGrouping = true)}"
                    val netStr = "Net: ${MoneyParser.format(summary.netAmount, includeSymbol = true, useGrouping = true, showExplicitSign = true)}"

                    canvas.drawText("Income: ", margin + 12f, currentY + 36f, bodyPaint)
                    canvas.drawText(incomeStr, margin + 55f, currentY + 36f, incomePaint)

                    canvas.drawText("Expense: ", margin + 180f, currentY + 36f, bodyPaint)
                    canvas.drawText(expenseStr, margin + 225f, currentY + 36f, expensePaint)

                    val netPaint = if (summary.netAmount.isNegative) expensePaint else incomePaint
                    canvas.drawText(netStr, margin + 350f, currentY + 36f, netPaint)

                    currentY += 62f
                }
                currentY += 8f
            }

            // 2. Category Breakdown Section (Top expenses/incomes per currency)
            for (summary in reportData.currencySummaries) {
                val curr = summary.currencyCode
                val expenseCats = reportData.expenseCategoriesByCurrency[curr] ?: emptyList()
                val incomeCats = reportData.incomeCategoriesByCurrency[curr] ?: emptyList()

                if (expenseCats.isNotEmpty() || incomeCats.isNotEmpty()) {
                    if (currentY + 50f > pageHeight - margin - 30f) {
                        startNewPage()
                    }

                    canvas.drawText("Category Breakdown ($curr)", margin, currentY, headerPaint)
                    currentY += 14f

                    if (expenseCats.isNotEmpty()) {
                        canvas.drawText("Top Expenses:", margin, currentY, bodyBoldPaint)
                        currentY += 12f
                        for (cat in expenseCats.take(5)) {
                            if (currentY + 14f > pageHeight - margin - 30f) {
                                startNewPage()
                            }
                            val catLabel = "• ${cat.categoryName} (${cat.transactionCount} txs)"
                            val catAmount = MoneyParser.format(cat.totalAmount, includeSymbol = true, useGrouping = true)
                            canvas.drawText(catLabel, margin + 10f, currentY, bodyPaint)
                            val amountMeasure = bodyBoldPaint.measureText(catAmount)
                            canvas.drawText(catAmount, margin + contentWidth - amountMeasure, currentY, bodyBoldPaint)
                            currentY += 13f
                        }
                    }

                    if (incomeCats.isNotEmpty()) {
                        if (currentY + 20f > pageHeight - margin - 30f) {
                            startNewPage()
                        }
                        canvas.drawText("Top Incomes:", margin, currentY, bodyBoldPaint)
                        currentY += 12f
                        for (cat in incomeCats.take(5)) {
                            if (currentY + 14f > pageHeight - margin - 30f) {
                                startNewPage()
                            }
                            val catLabel = "• ${cat.categoryName} (${cat.transactionCount} txs)"
                            val catAmount = MoneyParser.format(cat.totalAmount, includeSymbol = true, useGrouping = true)
                            canvas.drawText(catLabel, margin + 10f, currentY, bodyPaint)
                            val amountMeasure = bodyBoldPaint.measureText(catAmount)
                            canvas.drawText(catAmount, margin + contentWidth - amountMeasure, currentY, bodyBoldPaint)
                            currentY += 13f
                        }
                    }
                    currentY += 10f
                }
            }

            // 3. Transactions Table Section
            if (currentY + 60f > pageHeight - margin - 30f) {
                startNewPage()
            }

            canvas.drawText("Transactions Ledger (${reportData.totalTransactionsCount} records)", margin, currentY, headerPaint)
            currentY += 16f

            // Table Column Positions
            val colDate = margin
            val colType = margin + 65f
            val colCategory = margin + 120f
            val colAccount = margin + 225f
            val colAmount = margin + 360f
            val colNote = margin + 445f

            fun drawTableHeader() {
                canvas.drawRect(margin, currentY - 10f, margin + contentWidth, currentY + 6f, bgBoxPaint)
                canvas.drawText("Date", colDate + 4f, currentY, bodyBoldPaint)
                canvas.drawText("Type", colType, currentY, bodyBoldPaint)
                canvas.drawText("Category", colCategory, currentY, bodyBoldPaint)
                canvas.drawText("Account", colAccount, currentY, bodyBoldPaint)
                canvas.drawText("Amount", colAmount, currentY, bodyBoldPaint)
                canvas.drawText("Note", colNote, currentY, bodyBoldPaint)
                currentY += 14f
                canvas.drawLine(margin, currentY - 4f, margin + contentWidth, currentY - 4f, linePaint)
                currentY += 6f
            }

            drawTableHeader()

            for (tx in reportData.transactions) {
                // Check if space remains for this row (approx 18 points)
                if (currentY + 18f > pageHeight - margin - 25f) {
                    startNewPage()
                    drawTableHeader()
                }

                val dateStr = dateFormatter.format(tx.transactionTime.atZone(zoneId))
                val typeStr = when (tx.type) {
                    TransactionType.EXPENSE -> "Expense"
                    TransactionType.INCOME -> "Income"
                    TransactionType.TRANSFER -> "Transfer"
                }
                val categoryStr = tx.categoryName ?: (if (tx.type == TransactionType.TRANSFER) "—" else "Uncategorized")
                val accountStr = if (tx.type == TransactionType.TRANSFER) {
                    "${tx.sourceAccountName} → ${tx.destinationAccountName ?: ""}"
                } else {
                    tx.sourceAccountName
                }

                val formattedAmount = when (tx.type) {
                    TransactionType.EXPENSE -> "- " + MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true)
                    TransactionType.INCOME -> "+ " + MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true)
                    TransactionType.TRANSFER -> "  " + MoneyParser.format(tx.amount, includeSymbol = true, useGrouping = true)
                }

                val paintForAmount = when (tx.type) {
                    TransactionType.EXPENSE -> expensePaint
                    TransactionType.INCOME -> incomePaint
                    TransactionType.TRANSFER -> transferPaint
                }

                val truncatedCategory = truncateText(categoryStr, bodyPaint, 95f)
                val truncatedAccount = truncateText(accountStr, bodyPaint, 125f)
                val truncatedNote = truncateText(tx.note ?: "", bodyPaint, 65f)

                canvas.drawText(dateStr, colDate + 4f, currentY, bodyPaint)
                canvas.drawText(typeStr, colType, currentY, bodyPaint)
                canvas.drawText(truncatedCategory, colCategory, currentY, bodyPaint)
                canvas.drawText(truncatedAccount, colAccount, currentY, bodyPaint)
                canvas.drawText(formattedAmount, colAmount, currentY, paintForAmount)
                canvas.drawText(truncatedNote, colNote, currentY, bodyPaint)

                currentY += 15f
            }

            // Draw final page footer
            drawFooter(currentPageNumber)
            document.finishPage(page)

            // Write to destination stream
            document.writeTo(outputStream)
            outputStream.flush()
        } finally {
            document.close()
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) {
            return text
        }
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "…" else ""
    }
}

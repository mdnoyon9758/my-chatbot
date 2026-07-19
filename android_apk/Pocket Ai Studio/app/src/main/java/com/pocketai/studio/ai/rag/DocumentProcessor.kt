package com.pocketai.studio.ai.rag

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class DocumentProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun processDocument(uri: Uri, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> extractPdf(uri)
            "txt", "md", "text" -> extractText(uri)
            "csv" -> extractCsv(uri)
            "xlsx", "xls" -> extractXlsx(uri)
            "docx" -> extractDocx(uri)
            else -> extractText(uri)
        }
    }

    private fun extractPdf(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val reader = PdfReader(inputStream)
        val document = PdfDocument(reader)
        val text = StringBuilder()
        for (i in 1..document.numberOfPages) {
            text.append(PdfTextExtractor.getTextFromPage(document.getPage(i), SimpleTextExtractionStrategy()))
            text.append('\n')
        }
        document.close()
        reader.close()
        inputStream.close()
        return text.toString()
    }

    private fun extractText(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val reader = BufferedReader(InputStreamReader(inputStream))
        val text = reader.readText()
        reader.close()
        inputStream.close()
        return text
    }

    private fun extractCsv(uri: Uri): String {
        val text = extractText(uri)
        return text.lines().joinToString("\n") { line ->
            line.split(",").joinToString(" | ") { it.trim().trim('"') }
        }
    }

    private fun extractXlsx(uri: Uri): String {
        val text = extractText(uri)
        return "Excel file contents (XML-based):\n$text"
    }

    private fun extractDocx(uri: Uri): String {
        val text = extractText(uri)
        return "Word document contents:\n$text"
    }
}

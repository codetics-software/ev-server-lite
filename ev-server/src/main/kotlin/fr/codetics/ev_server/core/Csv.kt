package fr.codetics.ev_server.core

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

object Csv {
    private const val SEPARATOR = ';'
    private val dangerous = setOf('=', '+', '-', '@', '\t', '\r')

    fun write(header: List<String>, rows: Sequence<List<Any?>>): String {
        val output = StringBuilder("﻿")
        output.append(header.joinToString(SEPARATOR.toString(), transform = ::cell)).append("\r\n")
        rows.forEach { row ->
            output.append(row.joinToString(SEPARATOR.toString()) { cell(it) }).append("\r\n")
        }
        return output.toString()
    }

    fun download(name: String, header: List<String>, rows: Sequence<List<Any?>>): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType("text", "csv", Charsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$name.csv\"")
            .body(write(header, rows))

    private fun cell(value: Any?): String {
        var text = value?.toString() ?: ""
        if (text.isNotEmpty() && text[0] in dangerous && text.toBigDecimalOrNull() == null) {
            text = "'$text"
        }
        return if (text.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }

    fun read(content: String, maximumRows: Int = 10000): List<Map<String, String>> {
        val rows = parse(content.removePrefix("﻿"))
        ensure(rows.isNotEmpty()) { "Le fichier CSV est vide." }
        ensure(rows.size - 1 <= maximumRows) { "Le fichier contient plus de $maximumRows lignes." }
        val separatorHeader = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).filter { row -> row.any(String::isNotBlank) }.map { row ->
            separatorHeader.mapIndexed { index, key -> key to row.getOrElse(index) { "" }.trim() }.toMap()
        }
    }

    private fun parse(content: String): List<List<String>> {
        val separator = if (content.lineSequence().firstOrNull().orEmpty().count { it == ';' } > 0) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < content.length) {
            val char = content[index]
            when {
                quoted && char == '"' && content.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index += 1
                }
                char == '"' -> quoted = !quoted
                !quoted && char == separator -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && content.getOrNull(index + 1) == '\n') index += 1
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(char)
            }
            index += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }
}

data class ImportError(val line: Int, val message: String)

data class ImportResult(
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val errors: List<ImportError> = emptyList(),
)

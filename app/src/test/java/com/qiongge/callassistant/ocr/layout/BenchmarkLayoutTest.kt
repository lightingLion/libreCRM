package com.qiongge.callassistant.ocr.layout

import java.io.File
import org.junit.Test

/** Runs the actual production rebuilder on shared benchmark text boxes. */
class BenchmarkLayoutTest {
    @Test fun runBenchmark() {
        val directory = File(System.getProperty("user.dir")).let {
            if (File(it, "benchmark").exists()) File(it, "benchmark") else File(it.parentFile, "benchmark")
        }
        for (i in 1..4) {
            val name = "sample%02d".format(i)
            val entries = File(directory, "$name.tsv").readLines()
            val (width, height) = entries[0].split('\t').map(String::toInt)
            fun numbers(s: String) = s.split(',').mapNotNull { it.toIntOrNull() }
            val h = numbers(entries[1]); val v = numbers(entries[2])
            val grid = if (h.size >= 2 && v.size >= 2) TableGrid(h, v) else null
            val lines = entries.drop(3).filter(String::isNotBlank).map {
                val p = it.split('\t'); OcrTextLine(p[4], p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), 1f)
            }
            val rows = OcrLayoutRebuilder.rebuild(lines, width, height, grid)
            File(directory, "$name.production.tsv").writeText(rows.joinToString("\n") { "${it.name}\t${it.phone}" }, Charsets.UTF_8)
        }
    }
}

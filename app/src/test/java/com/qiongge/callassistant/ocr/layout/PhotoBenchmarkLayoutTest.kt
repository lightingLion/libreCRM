package com.qiongge.callassistant.ocr.layout

import java.io.File
import org.junit.Test

/** Executes the unchanged production matcher on the shared photo OCR results. */
class PhotoBenchmarkLayoutTest {
    @Test fun runBenchmark() {
        val dir = File(System.getProperty("user.dir")).let {
            if (File(it, "benchmark").exists()) File(it, "benchmark/photo") else File(it.parentFile, "benchmark/photo")
        }
        for (file in dir.listFiles().orEmpty().filter { it.name.matches(Regex("photo[0-9]+\\.tsv")) }.sortedBy { it.name }) {
            val entries = file.readLines()
            val (width, height) = entries[0].split('\t').map(String::toInt)
            fun nums(s: String) = s.split(',').mapNotNull { it.toIntOrNull() }
            val h=nums(entries[1]); val v=nums(entries[2])
            val grid=if (h.size>=2 && v.size>=2) TableGrid(h,v) else null
            val lines=entries.drop(3).filter(String::isNotBlank).map {
                val p=it.split('\t'); OcrTextLine(p[4],p[0].toInt(),p[1].toInt(),p[2].toInt(),p[3].toInt(),p[5].toFloat())
            }
            val rows=OcrLayoutRebuilder.rebuild(lines,width,height,grid)
            File(dir,"${file.nameWithoutExtension}.production.tsv").writeText(rows.joinToString("\n") { "${it.name}\t${it.phone}" },Charsets.UTF_8)
        }
    }
}

package com.qiongge.callassistant.domain.importing

import java.text.Normalizer

data class PropertyFields(val building: String?, val room: String?) {
    companion object {
        private val buildingHeaders = setOf("栋", "栋号", "栋数", "楼栋", "楼栋号", "楼号", "幢", "幢号", "座")
        private val roomHeaders = setOf("房号", "房间号", "门牌", "门牌号", "室号", "户号")
        private val addressHeaders = setOf("地址", "房屋地址", "房源地址", "房屋编号", "房屋", "楼栋房号", "栋号房号")
        private val buildingPattern = Regex("""(?<![A-Za-z0-9])[A-Za-z]?[0-9]{1,4}\s*(?:栋|幢|座|号楼)|(?<![A-Za-z0-9])[A-Za-z]\s*座""")
        private val roomPattern = Regex("""(?<![0-9])[0-9]{1,6}[A-Za-z]?\s*(?:室|房|号(?!楼))""")
        private val qualifiedRoom = Regex("""^(?:\s*([0-9]{1,2}\s*单元))?[\s/·-]*([0-9]{2,6}[A-Za-z]?)(?![0-9A-Za-z])""")
        private val compactAddress = Regex("""^([A-Za-z]?[0-9]{1,4})\s*[-/]\s*([0-9]{1,2}\s*[-/]\s*)?([0-9]{2,6}[A-Za-z]?)$""")

        fun hasHeader(cells: List<String>): Boolean = cells.any {
            normalize(it) in buildingHeaders || normalize(it) in roomHeaders || normalize(it) in addressHeaders
        }

        fun recognize(cells: List<String>, headers: List<String> = emptyList()): PropertyFields {
            fun field(aliases: Set<String>): String? {
                val index = headers.indexOfFirst { normalize(it) in aliases }
                return cells.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)?.take(80)
            }
            val address = field(addressHeaders)
            val candidates = listOfNotNull(address) + cells
            var building = field(buildingHeaders)
            var room = field(roomHeaders)
            for (cell in candidates) {
                val normalized = normalize(cell)
                val match = buildingPattern.find(normalized)
                val compact = compactAddress.matchEntire(normalized)
                if (building == null) building = match?.value ?: compact?.groupValues?.get(1)
                if (room == null) {
                    room = match?.let {
                        val suffix = normalized.substring(it.range.last + 1).trimStart()
                        qualifiedRoom.find(suffix)?.let { roomMatch ->
                            roomMatch.groupValues[1] + roomMatch.groupValues[2]
                        }
                    } ?: roomPattern.findAll(normalized).firstOrNull { result ->
                        match == null || result.range.first > match.range.last
                    }?.value ?: compact?.let { it.groupValues[2] + it.groupValues[3] }
                }
            }
            return PropertyFields(building, room)
        }

        private fun normalize(value: String): String =
            Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
    }
}

package com.demo.univchatbot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.sql.DriverManager

@Component
class OracleQueryRunner {
    fun run(response: String): String {
        val regex = Regex("""(?s)```sql\s*(.*?)```""")
        val sqlBlock = regex.find(response)?.groups?.get(1)?.value
            ?: throw IllegalArgumentException("응답에서 SQL을 추출할 수 없습니다.")

        val queries = sqlBlock.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521/XEPDB1", "system", "991105")
        val stmt = conn.createStatement()
        val mapper = jacksonObjectMapper()

        return if (queries.size == 1) {
            // ✅ 단일 쿼리 처리 (기존 방식)
            val rs = stmt.executeQuery(queries[0])
            val meta = rs.metaData
            val count = meta.columnCount
            val rows = mutableListOf<Map<String, String>>()

            while (rs.next()) {
                val row = mutableMapOf<String, String>()
                for (i in 1..count) {
                    row[meta.getColumnName(i)] = rs.getString(i) ?: ""
                }
                rows.add(row)
            }

            rs.close(); stmt.close(); conn.close()
            mapper.writeValueAsString(rows)

        } else {
            // ✅ 다중 쿼리 처리
            val results = mutableListOf<Map<String, Any>>()
            for ((index, query) in queries.withIndex()) {
                val rs = stmt.executeQuery(query)
                val meta = rs.metaData
                val count = meta.columnCount
                val rows = mutableListOf<Map<String, String>>()

                while (rs.next()) {
                    val row = mutableMapOf<String, String>()
                    for (i in 1..count) {
                        row[meta.getColumnName(i)] = rs.getString(i) ?: ""
                    }
                    rows.add(row)
                }
                rs.close()
                results.add(mapOf("query${index + 1}" to rows))
            }

            stmt.close(); conn.close()
            mapper.writeValueAsString(results)
        }
    }
//        val regex = Regex("""(?s)```sql\s*(.*?)```""")
//        val sql = regex.find(response)?.groups?.get(1)?.value?.trim()?.removeSuffix(";")
//            ?: throw IllegalArgumentException("응답에서 SQL을 추출할 수 없습니다.")
//
//        val conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521/XEPDB1", "system", "991105")
//        val stmt = conn.createStatement()
//        val rs = stmt.executeQuery(sql)
//
//        val meta = rs.metaData
//        val count = meta.columnCount
//        val results = mutableListOf<Map<String, String>>()
//
//        while (rs.next()) {
//            val row = mutableMapOf<String, String>()
//            for (i in 1..count) {
//                row[meta.getColumnName(i)] = rs.getString(i) ?: ""
//            }
//            results.add(row)
//        }
//
//        rs.close(); stmt.close(); conn.close()
//
//        return jacksonObjectMapper().writeValueAsString(results)
//    }
}
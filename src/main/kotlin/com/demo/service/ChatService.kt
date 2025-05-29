//package com.demo.univchatbot.service
//
//import org.springframework.stereotype.Service
//import org.springframework.beans.factory.annotation.Value
//import java.net.HttpURLConnection
//import java.net.URL
//import java.sql.DriverManager
//import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
//import com.fasterxml.jackson.module.kotlin.readValue
//
//@Service
//class ChatService {
//
//    @Value("\${openai.api.key}")
//    lateinit var openaiApiKey: String
//
//    fun askChatGPTAndQueryDB(question: String): String {
//        val sql = callChatGPT(question)
//        return runQuery(sql)
//    }
//
//    private fun callChatGPT(prompt: String): String {
//        val schema = """
//        다음은 OPEN_SUBJECTS 테이블의 구조입니다:
//
//        - ID: NUMBER (자동 증가, 기본 키)
//        - SUBJECT_CODE: VARCHAR2(100)
//        - SUBJECT_NAME: VARCHAR2(100)
//        - CLASS_NO: VARCHAR2(2)
//        - CREDIT_TYPE: VARCHAR2(100)
//        - SUBJECT_TYPE: VARCHAR2(100)
//        - COMPETENCY: VARCHAR2(100)
//        - LIBERAL_LARGE: VARCHAR2(100)
//        - LIBERAL_MEDIUM: VARCHAR2(100)
//        - LIBERAL_SMALL: VARCHAR2(100)
//        - ONLINE_RATE: NUMBER(5,2)
//        - PROFESSOR: VARCHAR2(100)
//        - AFFILIATION: VARCHAR2(100)
//        - COLLEGE: VARCHAR2(100)
//        - DEPARTMENT: VARCHAR2(100)
//        - MAJOR: VARCHAR2(100)
//        - DAY_NIGHT: VARCHAR2(100)
//        - OPEN_GRADE: VARCHAR2(10)
//        - CREDIT: NUMBER(2)
//        - LECTURE_HOURS: NUMBER(3,1)
//        - LAB_REQUIRED: NUMBER(2,1)
//        - EVAL_TYPE: VARCHAR2(100)
//        - PASS_COURSE: NUMBER(1)
//        - INTENSIVE_COURSE: NUMBER(1)
//        - SCHEDULE: VARCHAR2(200)
//        - NOTE: VARCHAR2(200)
//    """.trimIndent()
//
//        val fullPrompt = """
//        $schema
//
//        사용자 질문: $prompt
//
//        위 정보를 참고하여 Oracle SQL 쿼리를 생성해줘. 단, 테이블명은 OPEN_SUBJECTS를 사용하고 실제 컬럼명을 활용해줘.
//    """.trimIndent()
//
//        val connection = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
//        connection.requestMethod = "POST"
//        connection.setRequestProperty("Authorization", "Bearer $openaiApiKey")
//        connection.setRequestProperty("Content-Type", "application/json")
//        connection.doOutput = true
//
//        val requestBody = """
//        {
//          "model": "gpt-3.5-turbo",
//          "messages": [{"role": "user", "content": "$fullPrompt"}]
//        }
//    """.trimIndent()
//
//        connection.outputStream.write(requestBody.toByteArray())
//        val response = connection.inputStream.bufferedReader().readText()
//        val json = jacksonObjectMapper().readTree(response)
//        return json["choices"][0]["message"]["content"].asText().trim()
//    }
//
//
//    private fun runQuery(sql: String): String {
//        val url = "jdbc:oracle:thin:@localhost:1521/XEPDB1"
//        val user = "system"
//        val password = "your_password"
//
//        val conn = DriverManager.getConnection(url, user, password)
//        val stmt = conn.createStatement()
//        val rs = stmt.executeQuery(sql)
//
//        val result = StringBuilder()
//        val meta = rs.metaData
//        val columnCount = meta.columnCount
//
//        while (rs.next()) {
//            for (i in 1..columnCount) {
//                result.append(rs.getString(i)).append("\t")
//            }
//            result.append("\n")
//        }
//
//        rs.close()
//        stmt.close()
//        conn.close()
//
//        return result.toString()
//    }
//}

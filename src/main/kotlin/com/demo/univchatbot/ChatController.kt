package com.demo.univchatbot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.sql.DriverManager

@RestController
class ChatController {

    @PostMapping("/ask")
    fun ask(@RequestBody req: QuestionRequest): String {
        return try {
            val query = callChatGPTToMakeSQL(req.question)
            val intent = detectIntent(query)
            val rawResult = runQueryOnOracle(query)

            if (rawResult.isBlank()) {
                return when (intent) {
                    "professor" -> "👨‍🏫 <b>해당 교수님의 강의가 존재하지 않습니다.</b>"
                    "subject" -> "📘 <b>해당 과목 정보를 찾을 수 없습니다.</b>"
                    "schedule" -> "📅 <b>해당 시간대의 수업이 없습니다.</b>"
                    "affiliation" -> "🏛️ <b>관련 학과 수업 정보를 찾을 수 없습니다.</b>"
                    "credit" -> "📚 <b>조건에 맞는 학점 수업이 없습니다.</b>"
                    "type" -> "🧾 <b>조건에 맞는 유형의 수업이 없습니다.</b>"
                    "competency" -> "🧠 <b>관련 역량 수업 정보를 찾을 수 없습니다.</b>"
                    "online" -> "💻 <b>온라인 강의 정보가 없습니다.</b>"
                    else -> "❓ <b>조건에 맞는 수업 정보를 찾을 수 없습니다.</b>"
                }            }

            val naturalSummary = summarizeResultWithChatGPT(req.question, rawResult)
            val formattedSummary = naturalSummary.replace(Regex("""\.\s*"""), ".<br>")
            "📈 <b>요약:</b><br>$formattedSummary"
        } catch (e: IllegalArgumentException) {
            // SQL 추출 실패 등
            "<b>이해하지 못했어요.</b><br>다시 질문해주시겠어요? 예: <i>김정준 교수 수업 알려줘</i> 와 같은 형태로 입력해주세요."
        } catch (e: Exception) {
            // 기타 에러
            "🚫 <b>오류가 발생했습니다.</b><br>관리자에게 문의하거나 나중에 다시 시도해주세요."
        }
    }

    data class QuestionRequest(val question: String)

    fun detectIntent(sql: String): String {
        return when {
            "PROFESSOR" in sql -> "professor"
            "SUBJECT_NAME" in sql || "SUBJECT_CODE" in sql -> "subject"
            "SCHEDULE" in sql || "DAY_NIGHT" in sql || "NOTE" in sql -> "schedule"
            "COLLEGE" in sql || "DEPARTMENT" in sql || "MAJOR" in sql || "AFFILIATION" in sql -> "affiliation"
            "CREDIT" in sql || "LECTURE_HOURS" in sql || "LAB_REQUIRED" in sql -> "credit"
            "CREDIT_TYPE" in sql || "SUBJECT_TYPE" in sql || "EVAL_TYPE" in sql || "PASS_COURSE" in sql || "INTENSIVE_COURSE" in sql -> "type"
            "COMPETENCY" in sql || "LIBERAL_LARGE" in sql || "LIBERAL_MEDIUM" in sql || "LIBERAL_SMALL" in sql -> "competency"
            "ONLINE_RATE" in sql -> "online"
            else -> "generic"
        }
    }

    fun callChatGPTToMakeSQL(question: String): String {
        val openaiApiKey = System.getenv("OPENAI_API_KEY")

        val schema = """
        테이블 이름: OPEN_SUBJECTS
        컬럼:
        - ID: NUMBER (자동 증가, 기본 키)
        - SUBJECT_CODE: VARCHAR2(100)  -- 과목코드
        - SUBJECT_NAME: VARCHAR2(100)  -- 과목명
        - CLASS_NO: VARCHAR2(2)  -- 분반
        - CREDIT_TYPE: VARCHAR2(100)  -- 이수구분 (예: 교양선택, 전공필수, 교양필수 등)
        - SUBJECT_TYPE: VARCHAR2(100)  -- 과목구분 (예: 일반과목, 블렌디드, 사이버강의, 관리자수강, 사회봉사, 교육봉사활동, 전공실기, 현장실습, 격주운영)
        - COMPETENCY: VARCHAR2(100)  -- 역량 요소
        - LIBERAL_LARGE: VARCHAR2(100)  -- 교양 대분류 (예: 인문, 사회, 자연 등)
        - LIBERAL_MEDIUM: VARCHAR2(100)  -- 교양 중분류
        - LIBERAL_SMALL: VARCHAR2(100)  -- 교양 소분류
        - ONLINE_RATE: NUMBER(5,2)  -- 온라인 강의 비율
        - PROFESSOR: VARCHAR2(100) -- 교수명
        - AFFILIATION: VARCHAR2(100)  -- 소속
        - COLLEGE: VARCHAR2(100)  -- 개설대학
        - DEPARTMENT: VARCHAR2(100)  -- 개설학부(과)
        - MAJOR: VARCHAR2(100)  -- 개설전공
        - DAY_NIGHT: VARCHAR2(100)  -- 주야 구분 (예: 주간, 야간)
        - OPEN_GRADE: VARCHAR2(10)  -- 개설 학년(예: 1,2,3,전체)
        - CREDIT: NUMBER(2)  -- 학점
        - LECTURE_HOURS: NUMBER(3,1)  -- 이론 시수
        - LAB_REQUIRED: NUMBER(2,1)  -- 실습 시수
        - EVAL_TYPE: VARCHAR2(100)  -- 평가 방식
        - PASS_COURSE: NUMBER(1)  -- 패스/페일 여부
        - INTENSIVE_COURSE: NUMBER(1)  -- 집중강의 여부
        - SCHEDULE: VARCHAR2(200)  -- 시간표
        - NOTE: VARCHAR2(200)  -- 비고
    """.trimIndent()

        val prompt = """
        다음은 Oracle 테이블 스키마입니다:
        $schema

        사용자 질문: $question
        
        다음은 예시입니다:
        - 질문: '김정준 교수 수업 알려줘'  
          → 쿼리: SELECT * FROM OPEN_SUBJECTS WHERE PROFESSOR LIKE '%김정준%'
        
        - 질문: '프로그래밍입문 수업 정보 알려줘'  
          → 쿼리: SELECT * FROM OPEN_SUBJECTS WHERE SUBJECT_NAME LIKE '%프로그래밍입문%'
  
        이 질문에 맞는 Oracle SQL 쿼리를 생성해줘.
    """.trimIndent()

        val requestBody = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "당신은 Oracle SQL 쿼리 생성기입니다."),
                mapOf("role" to "user", "content" to prompt)
            )
        )

        val connection = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $openaiApiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        // Write JSON with UTF-8 encoding
        val mapper = jacksonObjectMapper()
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(mapper.writeValueAsString(requestBody))
        }

        val responseCode = connection.responseCode
        val responseText = if (responseCode == 200) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val errorText = connection.errorStream?.bufferedReader()?.readText()
            throw RuntimeException("OpenAI API 오류 (HTTP $responseCode): $errorText")
        }

        val json = mapper.readTree(responseText)
        return json["choices"][0]["message"]["content"].asText().trim()
    }

    fun extractSQL(content: String): String {
        val regex = Regex("""(?s)```sql\s*(.*?)```""")
        val match = regex.find(content)
        val rawSql = match?.groups?.get(1)?.value?.trim()
            ?: throw IllegalArgumentException("응답에서 SQL을 추출할 수 없습니다.")

        return rawSql.removeSuffix(";").trim()
    }
    fun runQueryOnOracle(response: String): String {
        val query = extractSQL(response)
        val url = "jdbc:oracle:thin:@localhost:1521/XEPDB1"
        val user = "system"
        val password = "991105"

        val conn = DriverManager.getConnection(url, user, password)
        val stmt = conn.createStatement()
        val rs = stmt.executeQuery(query)

        val metaData = rs.metaData
        val columnCount = metaData.columnCount
        val resultList = mutableListOf<Map<String, String>>()

        while (rs.next()) {
            val row = mutableMapOf<String, String>()
            for (i in 1..columnCount) {
                val columnName = metaData.getColumnName(i)
                val value = rs.getString(i) ?: ""
                row[columnName] = value
            }
            resultList.add(row)
        }

        rs.close()
        stmt.close()
        conn.close()

        // JSON 문자열로 반환
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(resultList)
    }

    var promptA :String = """
         다음은 그에 대한 Oracle DB 쿼리 실행 결과입니다.  
            결과를 학생이 이해하기 쉽게 요약해서 알려주세요.  
            
            - **'이 결과에 따르면' 같은 표현 없이 직접적으로 말해주세요.**  
            - '~으로 확인됩니다'보다는 '~입니다', '~입니다.' 식으로 간결하고 단정적인 표현을 사용해주세요.  
            - 존댓말을 유지하되 과하게 형식적이지 않게, **챗봇처럼 설명**해주세요.
            - "같이 확인해볼까요?", "함께 확인할까요?" 등의 문장은 쓰지 마세요.
            """
    val promptB = """
        다음은 해당 질문에 대한 수업 정보 목록이며 JSON 형식입니다.
            아래 형식을 정확히 지켜서 요약해 주세요:
            
            1. 수업은 "과목명 (분반)" 형태로 묶어서 **굵게** 표시합니다.  
               예: **데이터베이스응용 (01분반)**
            
            2. 다음 항목들을 순서대로 한 줄씩 '-'로 시작해 출력합니다:
               - 학점: (숫자)학점
               - 강의시간: (이론 시수)시간, 실습 (실습 시수)시간 (예: 1시간, 실습 2시간)
               - 평가방식: PASS/NP 또는 상대평가1, 상대평가2 등
               - 주간/야간 정보: 주간 또는 야간
               - 이수구분: 전공선택, 교양필수 등
               - 소속: 학과 정보 전체 (예: 창의융합대학/ 소프트웨어학과)
               - 수업 시간표: 요일(교시) 강의실 (없으면 '수업 시간표 정보 없음')
               - 부가설명: NOTE, 타과제한, 역량(예: 웹시스템이해, 자기관리 등)을 자연스럽게 정리
            
            3. 같은 과목명이더라도 분반이 다르면 각각 나눠서 출력합니다.
                        
            4. **중요: 수업(분반) 간에는 반드시 한 줄을 띄워주세요.**

            5. 출력 형식은 아래와 같이 유지합니다:
            
            예시:
            **딥러닝심화 (02분반)**
            - 학점: 3
            - 강의시간: 1시간, 실습 2시간
            - 평가방식: 상대평가1
            - 주간/야간 정보: 주간
            - 이수구분: 전공선택
            - 소속: 창의융합대학/ 소프트웨어학과
            - 수업 시간표: 화(4,5,6) 아리관(N)-601
            - 부가설명: 타과제한, 웹시스템이해, 인공지능이해
            
            ---

            아래 JSON 배열에 따라 위 형식으로 출력해 주세요.
            각 수업 사이에는 꼭 빈줄을 넣어주세요.
            ```json
            """


    fun summarizeResultWithChatGPT(question: String, resultText: String): String {
        val openaiApiKey = System.getenv("OPENAI_API_KEY")
        val url = URL("https://api.openai.com/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $openaiApiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val prompt = """
            사용자 질문: $question
            $promptA
            결과:
            $resultText
            """.trimIndent()

        val requestBody = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "너는 학생에게 DB 결과를 쉽게 설명하는 어시스턴트야."),
                mapOf("role" to "user", "content" to prompt)
            )
        )

        val mapper = jacksonObjectMapper()
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(mapper.writeValueAsString(requestBody))
        }

        val responseCode = conn.responseCode
        val responseText = if (responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.readText()
            throw RuntimeException("요약 API 요청 오류 (HTTP $responseCode): $errorText")
        }

        return mapper.readTree(responseText)["choices"][0]["message"]["content"].asText().trim()
    }
}

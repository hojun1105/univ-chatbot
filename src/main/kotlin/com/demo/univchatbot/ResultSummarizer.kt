package com.demo.univchatbot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Component
class ResultSummarizer {

    private val classPrompt = """
        다음은 해당 질문에 대한 수업 정보 목록이며 JSON 형식입니다.
        아래 형식을 정확히 지켜서 요약해 주세요:
        
        1. 수업은 "과목명 (분반)" 형태로 묶어서 **굵게** 표시합니다.
           예: **데이터베이스응용 (01분반)**
        
        2. 다음 항목들을 순서대로 한 줄씩 '-'로 시작해 출력합니다:
           - 학점: (숫자)학점
           - 강의시간: (이론 시수)시간, 실습 (실습 시수)시간
           - 평가방식: PASS/NP 또는 상대평가1, 상대평가2 등
           - 주간/야간 정보: 주간 또는 야간
           - 이수구분: 전공선택, 교양필수 등
           - 소속: 학과 정보 전체
           - 수업 시간표: 요일(교시) 강의실 (없으면 '수업 시간표 정보 없음')
           - 부가설명: NOTE, 타과제한, 역량 등을 정리
        
        3. 같은 과목명이더라도 분반이 다르면 각각 나눠서 출력합니다.
        4. **중요: 수업(분반) 간에는 반드시 한 줄을 띄워주세요.**
        ```json"""

    private val menuPrompt = """
        다음은 요일별 학식 메뉴 JSON 결과입니다.
        사용자 질문에 맞춰 아래 형식으로 요약해 주세요:
        
        - "(요일) 메뉴는 다음과 같아요:" 라고 시작
        - 번호 매기기 1. 2. 3. ... 으로 각 메뉴를 나열
        - 돼지/닭/소 등의 원산지가 괄호로 같이 포함된 경우 그대로 출력
        - 결과 끝에는 "이런 메뉴가 있어요!"로 마무리
        
        예시:
        화요일 메뉴는 다음과 같아요:
        1. 큐브스테이크덮밥 (돼지: 국산)
        2. 유부우동
        3. 회오리감자
        ...
        이런 메뉴가 있어요!
        
        아래 JSON을 바탕으로 위 형식을 따라주세요.
        ```json
        """.trimIndent()

    private val schedulePrompt =
        """
        다음은 대학 일정(SCHEDULE) 테이블의 결과입니다.
        아래 형식을 따라 학생에게 친절하게 요약해 주세요:

        1. 시작일~종료일의 형식으로 기간을 표시합니다. (예: 2025-03-01 ~ 2025-03-10)
        2. 각 일정 항목을 날짜 순으로 나열합니다.
        3. 각 일정은 한 줄에 `- 기간: 설명` 형태로 출력합니다.
        4. 마지막에 "위 일정들을 참고하세요!" 문장으로 마무리합니다.
        
        예시:
        - 2025-03-01 ~ 2025-03-10: 수강신청 기간
        - 2025-03-15 ~ 2025-03-15: 개강일
        - 2025-06-01 ~ 2025-06-15: 기말고사 기간
        
        위 일정들을 참고하세요!
        
        아래 JSON은 SCHEDULE 테이블에서 조회한 결과입니다.
        ```json"""

    fun summarize(question: String, resultJson: String, table: String): String {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val promptHeader = when(table){
            "MENU" -> menuPrompt
            "OPEN_SUBJECTS" -> classPrompt
            else -> schedulePrompt
        }
        val prompt = """
            사용자 질문: $question
            
            $promptHeader
            $resultJson
            ```
                    """.trimIndent()

        val requestBody = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "너는 학생에게 DB 결과를 쉽게 설명하는 챗봇이야."),
                mapOf("role" to "user", "content" to prompt)
            )
        )

        val conn = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        val mapper = jacksonObjectMapper()
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(mapper.writeValueAsString(requestBody))
        }

        val response = if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText()
            throw RuntimeException("요약 API 오류 (HTTP ${conn.responseCode}): $error")
        }

        return mapper.readTree(response)["choices"][0]["message"]["content"].asText().trim()
    }
}

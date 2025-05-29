package com.demo.univchatbot

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.springframework.stereotype.Component

@Component
class SqlGenerator {
    fun generate(question: String,table: String): String {
        val apiKey = System.getenv("OPENAI_API_KEY")
        val schema: String
        val prompt: String
        if(table=="MENU"){
            val dailySchema = getSchemaFor("DAILY_MENU")
            val cafeteriaSchema = getSchemaFor("CAFETERIA_MENU")
            schema = "$dailySchema\n\n$cafeteriaSchema"

            prompt = """
            다음은 Oracle 테이블 스키마입니다:
            $schema

            사용자 질문: $question
            
            다음 조건을 따르세요:
            - 요일 관련 질문이면 DAILY_MENU에서 SELECT 하세요.
            - CAFETERIA_MENU는 항상 모든 데이터 조회해야합니다.
            - 예시:
              질문: '수요일 메뉴 알려줘'
              → DAILY_MENU 테이블에서 DAY = '수요일' 조건으로 SELECT, CAFETERIA_MENU에서 SELECT

            이 질문에 대한 SQL 쿼리를 반드시 ```sql ... ``` 코드 블럭 안에 넣어 주세요.
        """.trimIndent()
        } else if(table=="OPEN_SUBJECTS") {
            schema = getSchemaFor(table)

            prompt = """
            다음은 Oracle 테이블 스키마입니다:
            $schema

            사용자 질문: $question
            
            다음은 예시입니다:
            - 질문: '김정준 교수 수업 알려줘'  
              → 쿼리: SELECT * FROM OPEN_SUBJECTS WHERE PROFESSOR LIKE '%김정준%'
            
            - 질문: '프로그래밍입문 수업 정보 알려줘'  
              → 쿼리: SELECT * FROM OPEN_SUBJECTS WHERE SUBJECT_NAME LIKE '%프로그래밍입문%'
      
            이 질문에 대한 SQL 쿼리를 반드시 ```sql ... ``` 코드 블럭 안에 넣어 주세요.
        """.trimIndent()
        }
        else{
            schema = getSchemaFor(table)
            prompt = """
            다음은 Oracle 테이블 스키마입니다:
            $schema

            사용자 질문: $question
            
            다음은 예시입니다:
            - 질문: '중간고사 기간 알려줘'  
              → 쿼리: SELECT * FROM SCHEDULE WHERE DESCRIPTION LIKE '%중간고사%'
      
            이 질문에 대한 SQL 쿼리를 반드시 ```sql ... ``` 코드 블럭 안에 넣어 주세요.
        """.trimIndent()
        }
        val requestBody = mapOf(
            "model" to "gpt-3.5-turbo",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "당신은 Oracle SQL 전문가이며, 정확한 WHERE 조건과 테이블 구조를 기반으로 쿼리를 생성합니다."),
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
            throw RuntimeException("OpenAI API 오류 (HTTP ${conn.responseCode}): $error")
        }

        val json = mapper.readTree(response)
        return json["choices"][0]["message"]["content"].asText().trim()
    }

    private fun getSchemaFor(table: String): String {
        return when (table.uppercase()) {
            "OPEN_SUBJECTS" -> """
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

            "SCHEDULE" -> """
            테이블 이름: SCHEDULE
            컬럼:
            - ID: NUMBER (기본 키)
            - FROM_DATE: DATE  -- 일정 시작일
            - TO_DATE: DATE  -- 일정 종료일
            - DESCRIPTION: VARCHAR2(200)  -- 설명
        """.trimIndent()

            "DAILY_MENU" -> """
            테이블 이름: DAILY_MENU
            컬럼:
            - DAY: VARCHAR2(10)  -- 요일 (예: Monday, Tuesday 등)
            - MENU1: VARCHAR2(100)  -- 첫 번째 메뉴
            - MENU2: VARCHAR2(100)  -- 두 번째 메뉴
        """.trimIndent()

            "CAFETERIA_MENU" -> """
            테이블 이름: CAFETERIA_MENU
            컬럼:
            - CATEGORY: VARCHAR2(100)  -- 식단 분류 (예: 한식, 일식 등)
            - MENU: VARCHAR2(100)  -- 메뉴 이름
        """.trimIndent()
            else -> throw IllegalArgumentException("알 수 없는 테이블입니다: $table")
        }
    }
}
package com.demo.univchatbot

import org.springframework.stereotype.Service

@Service
class ChatService(
    private val sqlGenerator: SqlGenerator,
    private val oracleRunner: OracleQueryRunner,
    private val summarizer: ResultSummarizer
) {
    fun handleQuestion(question: String, table: String): String {
        return try {
            val query = sqlGenerator.generate(question, table)
            val intent = detectIntent(query)
            val rawJsonResult = oracleRunner.run(query)

            if (rawJsonResult.isBlank()) {
                return intentToMessage(intent)
            }

            val summary = summarizer.summarize(question, rawJsonResult, table)
            "📈 <b>요약:</b><br>" + summary.replace("\n\n", "<br><br>").replace("\n", "<br>")
        } catch (e: IllegalArgumentException) {
            "<b>이해하지 못했어요.</b><br>예: <i>김정준 교수 수업 알려줘</i> 와 같이 질문해주세요."
        } catch (e: Exception) {
            "🚫 <b>오류가 발생했습니다.</b><br>관리자에게 문의해주세요."
        }
    }

    private fun detectIntent(sql: String): String {
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

    private fun intentToMessage(intent: String): String {
        return when (intent) {
            "professor" -> "👨‍🏫 <b>해당 교수님의 강의가 존재하지 않습니다.</b>"
            "subject" -> "📚 <b>해당 과목 정보를 찾을 수 없습니다.</b>"
            "schedule" -> "📅 <b>해당 시간대의 수업이 없습니다.</b>"
            "affiliation" -> "🏛️ <b>관련 학과 수업 정보를 찾을 수 없습니다.</b>"
            "credit" -> "📚 <b>조건에 맞는 학점 수업이 없습니다.</b>"
            "type" -> "🗾️ <b>조건에 맞는 유형의 수업이 없습니다.</b>"
            "competency" -> "🧠 <b>관련 역량 수업 정보를 찾을 수 없습니다.</b>"
            "online" -> "💻 <b>온라인 강의 정보가 없습니다.</b>"
            else -> "❓ <b>조건에 맞는 수업 정보를 찾을 수 없습니다.</b>"
        }
    }
}
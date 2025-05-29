package com.demo.univchatbot

import org.springframework.web.bind.annotation.*

@RestController
class ChatController(private val chatService: ChatService) {

    @PostMapping("/ask")
    fun ask(@RequestBody req: QuestionRequest): String {
        return chatService.handleQuestion(req.question,req.table)
    }

    data class QuestionRequest(
        val question: String,
        val table: String
    )}
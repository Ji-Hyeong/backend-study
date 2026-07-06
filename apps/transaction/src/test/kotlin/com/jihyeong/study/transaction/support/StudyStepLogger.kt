package com.jihyeong.study.transaction.support

import org.slf4j.LoggerFactory

object StudyStepLogger {

	private val log = LoggerFactory.getLogger("transaction-study")

	fun scenario(title: String) {
		log.info("")
		log.info("========== {} ==========", title)
	}

	fun step(number: Int, message: String) {
		log.info("[{}] {}", number, message)
	}

	fun state(message: String, vararg values: Any) {
		log.info("    -> " + message, *values)
	}
}

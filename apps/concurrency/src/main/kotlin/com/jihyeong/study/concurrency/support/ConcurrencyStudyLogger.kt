package com.jihyeong.study.concurrency.support

import org.slf4j.LoggerFactory

object ConcurrencyStudyLogger {

	private val log = LoggerFactory.getLogger("concurrency-study")

	fun scenario(title: String) {
		log.info("")
		log.info("========== {} ==========", title)
	}

	fun step(number: Int, message: String) {
		log.info("[{}] {}", number, message)
	}

	fun state(message: String, vararg values: Any) {
		log.info("    -> thread={} " + message, Thread.currentThread().name, *values)
	}
}

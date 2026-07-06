package com.jihyeong.study.transaction.isolation

data class IsolationReadResult(
	val firstCount: Long,
	val secondCount: Long,
)


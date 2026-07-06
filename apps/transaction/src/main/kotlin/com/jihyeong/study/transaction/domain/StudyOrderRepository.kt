package com.jihyeong.study.transaction.domain

import org.springframework.data.jpa.repository.JpaRepository

interface StudyOrderRepository : JpaRepository<StudyOrder, Long>


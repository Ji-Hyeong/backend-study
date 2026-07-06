package com.jihyeong.lab.transaction.domain

import org.springframework.data.jpa.repository.JpaRepository

interface StudyOrderRepository : JpaRepository<StudyOrder, Long>


package org.j3y.HuskerBot2

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableResilientMethods
class HuskerBot2Application

fun main(args: Array<String>) {
	runApplication<HuskerBot2Application>(*args)
}

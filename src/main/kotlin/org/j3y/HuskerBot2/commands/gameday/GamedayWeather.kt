package org.j3y.HuskerBot2.commands.gameday

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.model.WeatherForecast
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.WeatherService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class GamedayWeather : SlashCommand() {
    
    @Autowired lateinit var scheduleRepo: ScheduleRepo
    @Autowired lateinit var weatherService: WeatherService
    
    private final val log = LoggerFactory.getLogger(GamedayWeather::class.java)
    
    override fun getCommandKey(): String = "gameday-weather"
    
    override fun getDescription(): String = "Get weather forecast for the next Huskers game"
    
    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()
        
        try {
            val nextGame = getNextGame()
            if (nextGame == null) {
                commandEvent.hook.sendMessage("No upcoming games found.").queue()
                return
            }
            
            // Let WeatherService handle the API selection and fallback logic
            val gameLocation = getGameLocation(nextGame)
            val coordinates = weatherService.getCoordinates(gameLocation)
            if (coordinates == null) {
                commandEvent.hook.sendMessage("Unable to find location coordinates for $gameLocation.").queue()
                return
            }
            
            val gameDateTime = LocalDateTime.ofInstant(nextGame.dateTime, ZoneId.of("America/Chicago"))
            val weather = weatherService.getWeatherForecast(
                coordinates.latitude, 
                coordinates.longitude, 
                gameDateTime
            )
            
            if (weather == null) {
                commandEvent.hook.sendMessage("Weather forecast unavailable for this game date. The game may be too far in the future.").queue()
                return
            }
            
            val embed = createWeatherEmbed(nextGame, weather)
            commandEvent.hook.sendMessageEmbeds(embed).queue()
            
        } catch (e: Exception) {
            log.error("Error executing gameday-weather command", e)
            commandEvent.hook.sendMessage("Sorry, there was an error getting the weather forecast.").queue()
        }
    }
    
    private fun getNextGame(): ScheduleEntity? {
        return try {
            val currentYear = SeasonResolver.currentCfbSeason()
            val allGames = scheduleRepo.findAllBySeasonOrderByDateTimeAsc(currentYear)
            val now = Instant.now()
            
            val nextGame = allGames.firstOrNull { it.dateTime.isAfter(now) }
            if (nextGame != null) {
                log.info("Found next game: ${nextGame.opponent} on ${nextGame.dateTime}")
            } else {
                log.info("No upcoming games found for season $currentYear")
            }
            nextGame
        } catch (e: Exception) {
            log.error("Error retrieving next game", e)
            null
        }
    }

    
    private fun getGameLocation(scheduleEntity: ScheduleEntity): String {
        return when {
            scheduleEntity.venueType.equals("home", ignoreCase = true) -> "Lincoln, NE"
            scheduleEntity.location.isNotBlank() -> scheduleEntity.location
            else -> "Unknown Location"
        }
    }
    
    private fun getGameVenue(scheduleEntity: ScheduleEntity): String {
        return when {
            scheduleEntity.venueType.equals("home", ignoreCase = true) -> "Memorial Stadium"
            scheduleEntity.location.isNotBlank() -> scheduleEntity.location
            else -> "Unknown Venue"
        }
    }
    
    private fun isHomeGame(scheduleEntity: ScheduleEntity): Boolean {
        return scheduleEntity.venueType.equals("home", ignoreCase = true)
    }
    
    private fun createWeatherEmbed(game: ScheduleEntity, weather: WeatherForecast?): MessageEmbed {
        val embed = EmbedBuilder()
        
        embed.setTitle("🏈 Huskers Game Day Weather")
        embed.setColor(Color.RED)
        
        embed.addField("🆚 Opponent", game.opponent, true)
        embed.addField("📅 Game Time", formatGameTime(game.dateTime), true)
        embed.addField("📍 Location", getGameLocation(game), true)
        embed.addField("🏠 Home/Away", if (isHomeGame(game)) "Home" else "Away", true)
        
        // Show venue type for dome games
        if (game.isDome == true) {
            embed.addField("🏟️ Venue", "Dome/Indoor", true)
        }
        
        if (weather != null) {
            embed.addField("🌡️ Temperature", "${weather.temperature}°F", true)
            weather.micksTemp?.let {
                embed.addField("🌡️ Micks Temp", it, true)
            }
            embed.addField("☁️ Conditions", weather.shortForecast, true)
            embed.addField("💨 Wind", "${weather.windSpeed} ${weather.windDirection}", true)
            weather.humidity?.let { 
                embed.addField("🌧️ Precipitation", it, true) 
            }
            weather.precipitationProbability?.let {
                embed.addField("☔ Rain Chance", "${it}%", true)
            }
            
            // Add dome snark or regular snarky description
            if (game.isDome == true) {
                embed.addField("🔥 Forecast Hot Take", getDomeSnark(weather), false)
            } else {
                weather.snarkyDescription?.let {
                    embed.addField("🔥 Forecast Hot Take", it, false)
                }
            }
            
            embed.addField("📋 Detailed Forecast", weather.detailedForecast, false)
        } else {
            embed.addField("⚠️ Weather", "Weather data unavailable", false)
        }
        
        val footerText = if (game.isDome == true) {
            "Weather outside the dome - inside it's a perfect 72°F"
        } else {
            "Weather data from Tomorrow.io (≤120 hours) or National Weather Service (>120 hours)"
        }
        embed.setFooter(footerText)
        embed.setTimestamp(Instant.now())
        
        return embed.build()
    }
    
    private fun getDomeSnark(weather: WeatherForecast): String {
        val outsideTemp = weather.temperature
        val conditions = weather.shortForecast.lowercase()
        val isHistorical = conditions.contains("historical")
        
        // For historical averages, base snark on temperature only
        if (isHistorical) {
            return when {
                outsideTemp < 20 -> "Historically it's around ${outsideTemp}°F this time of year, but who cares? " +
                        "We're in a dome! While tailgaters bundle up in the parking lot, we'll be nice and toasty."
                outsideTemp < 40 -> "History says it's usually around ${outsideTemp}°F outside this time of year. " +
                        "Good thing we've got a roof - the only cold shoulder will be the one we give their offense."
                outsideTemp > 90 -> "Historical temps average ${outsideTemp}°F this time of year. Brutal. " +
                        "Thank the football gods for AC and dome life."
                outsideTemp > 75 -> "Historically around ${outsideTemp}°F outside, but inside? A perfect 72°F. " +
                        "Climate control beats climate every time. GBR!"
                else -> "Historical averages say ${outsideTemp}°F outside, but it literally doesn't matter. " +
                        "We're in a dome - perfect conditions, zero excuses. GBR!"
            }
        }
        
        // For actual forecasts, use conditions too
        return when {
            outsideTemp < 20 -> "It's ${outsideTemp}°F outside, but who cares? We're in a dome, baby! " +
                    "While those poor souls freeze in the parking lot, we'll be nice and toasty watching the Huskers."
            outsideTemp < 40 -> "A chilly ${outsideTemp}°F outside, but the only thing frozen in this dome " +
                    "will be the opposing team's offense. Climate-controlled domination incoming."
            outsideTemp > 90 -> "It's a scorching ${outsideTemp}°F outside, but we've got AC. " +
                    "The only heat the other team will feel is from our defense."
            conditions.contains("rain") || conditions.contains("storm") -> 
                    "It's ${weather.shortForecast.lowercase()} outside, but not a single drop will fall on our turf. " +
                    "Dome life is the best life. No weather excuses today!"
            conditions.contains("snow") -> "Snow outside? That's cute. Meanwhile, we're playing " +
                    "in perfect conditions. Mother Nature can't touch us in here."
            conditions.contains("wind") -> "Windy outside? Cool story. Our passes will spiral perfectly " +
                    "in this dome while the wind howls uselessly outside."
            else -> "Weather outside: ${weather.shortForecast}. Weather inside: Perfect. " +
                    "Who gives a damn about the forecast when you've got a roof? GBR!"
        }
    }
    
    private fun formatGameTime(gameDateTime: Instant): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a z")
        val zonedDateTime = gameDateTime.atZone(ZoneId.of("America/Chicago"))
        return zonedDateTime.format(formatter)
    }
}
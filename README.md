# HuskerBot2

HuskerBot2 is a Kotlin/Spring Boot Discord bot for Nebraska Cornhuskers fans. It provides game-day utilities, betting games, pick'em, schedules, weather, and other fun commands.

## Features
- Betting commands
  - Create and show weekly bets for Husker games
  - View betting lines for a given week
  - Season-long betting leaderboard with points: Winner = 1, Spread = 2, Points = 2
- NFL Pick'em
  - Interactive buttons to pick winners; live counts update on the message
  - Leaderboard command for season performance
- Schedules
  - College Football (CFB) schedule and countdowns
  - NFL schedule utilities
- Weather
  - Game day weather via NWS (api.weather.gov)
- Other utilities
  - Urban Dictionary, image management, OSRS stats, and more

## Tech Stack
- Kotlin + Spring Boot
- JDA (Java Discord API)
- Spring Data JPA with H2 (file-backed) datastore by default; optional MySQL support via Spring profile
- Gradle build

## Requirements
- Java 17 or newer
- Discord bot token with appropriate permissions
- (Optional) API keys for external services (e.g., CollegeFootballData)

## Configuration
Edit `src/main/resources/application.yml`:
- `discord.token`: Your bot token
- `discord.guilds`: Guild IDs where the bot should operate
- Channels and category IDs as needed
- `cfbd.api-key`: CollegeFootballData API key if you use lines/matchup features
- `gemini.api-key`: Google Gemini API key used by the `/gemini` command (can be set via application.yml or GEMINI_API_KEY)
- Weather and Urban Dictionary settings are already configured with sensible defaults

### Database (H2 or MySQL)
- Default: H2 file-based database at `~/.discordbot/data`. No extra config needed.
- MySQL (optional): Enable the MySQL profile and provide connection details via environment variables.
  - Set `SPRING_PROFILES_ACTIVE=mysql`
  - Optional env vars (with defaults shown):
    - `DB_HOST` (default: `localhost`)
    - `DB_PORT` (default: `3306`)
    - `DB_NAME` (default: `huskerbot`)
    - `DB_USER` (default: `husker`)
    - `DB_PASSWORD` (default: `password`)
  - The effective JDBC URL is: `jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- Schema is managed by Hibernate (`spring.jpa.hibernate.ddl-auto=update`) in both profiles by default.

## Run Locally
1. Set your configuration in `application.yml`.
2. Build and run using the Gradle wrapper:
   - macOS/Linux: `./gradlew bootRun`
   - Windows: `gradlew.bat bootRun`

To build a jar:
- macOS/Linux: `./gradlew build`
- Windows: `gradlew.bat build`

## Testing
- Unit tests: `./gradlew test`
- Additional testing scripts live under the `testing/` directory (Python and shell helpers for weather parsing).

## Commands

- /bet … Betting commands
  - /bet create week:<int> winner:<Nebraska|Opponent> predict-points:<Over|Under> predict-spread:<Nebraska|Opponent>
    - Place a bet for a specific week’s Nebraska game. Week choices are populated from the current season schedule.
  - /bet show week:<int>
    - Show all submitted bets and totals for the selected week.
  - /bet lines week:<int>
    - Show betting lines (spread, over/under, details) for the selected week’s Nebraska game.
  - /bet leaderboard
    - Season-long betting leaderboard. Scoring: Winner = 1, Spread = 2, Points = 2.

- /gameday … Gameday mode (Admin only)
  - /gameday on — Turns gameday mode on.
  - /gameday off — Turns gameday mode off.

- /gameday-weather
  - Get the weather forecast for the next Huskers game at its actual site (home, away, or neutral); the AI hot take is written for where the game is played. Games more than a week out use historical averages.

- /image … Image management
  - /image add name:<string> url:<string>
    - Add a named image by URL (http/https). Fails if name already exists or URL is invalid.
  - /image delete name:<string>
    - Delete a stored image. You must be the uploader or have Manage Messages permission.
  - /image show name:<string>
    - Display a stored image.
  - /image list
    - List all stored image names.

- /schedule … Schedule utilities
  - /schedule cfb [league:<Top 25|ACC|American|Big 12|Big 10|SEC|Pac 12|MAC|Independent>] [week:<int>]
    - Get CFB scoreboard/schedule for the given week and league. Defaults: Top 25 and current week.
  - /schedule nfl [week:<int>]
    - Get NFL scoreboard/schedule for the given week. Default is the current NFL week.

- /countdown
  - Countdown to the next Nebraska game.

- /ud term:<string>
  - Look up a term on Urban Dictionary and page through definitions.

- /compare team1:<string> team2:<string>
  - Compare two CFB teams’ head-to-head history with recent games and series summary.

- /markov [messages:<10-1000>] [order:<1-3>] [seed:<string>]
  - Generate text from recent channel messages using a Markov chain. Defaults: messages=200, order=2.

- /osrs player:<string>
  - Show Old School RuneScape high scores for a player.

- /smms destination:<general|recruiting|admin> message:<string> (Manage Messages required)
  - Send an anonymous “Secret Mammal Message System” embed to the chosen channel.

- /possum message:<string>
  - Post a “Possum Droppings” embed to the configured possum channel.

- /iowa user:<@member> [reason:<string>] [minutes:<int>] (Manage Messages required)
  - Time out a user (“banish to Iowa”) with optional reason and duration (default 30 minutes).

- /nebraska user:<@member> (Manage Messages required)
  - Remove user timeout (“return to Nebraska”).

- /nfl-pickem-leaderboard
  - Show the NFL Pick’em leaderboard for the current year.

- /nfl-pickem-reload (Manage Messages required)
  - Reload/repost the weekly NFL Pick’em listing without deleting picks.

## Notes
- The bot uses a file-based H2 database located at `~/.discordbot/data` by default. Spring will auto-create/update schema on startup.
- Some features rely on external APIs and may need keys or rate limits respected.

## Contributing
PRs and issues welcome. Please keep changes small and focused. Run tests before submitting.

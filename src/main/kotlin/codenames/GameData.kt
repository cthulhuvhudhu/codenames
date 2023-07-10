package codenames

import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.util.UUID

@Document(collection = "games")
data class GameData(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    var startingTeam: Team? = null,
    var currentTeam: Team? = null,
    var redAgentsLeft: Int = BASE_NUM_AGENTS,
    var blueAgentsLeft: Int = BASE_NUM_AGENTS,
    var gameStatus: GameStatus = GameStatus.NEW,
    var board: List<Card> = listOf(),
    var winner: Team? = null,
    var turns: MutableList<Turn> = mutableListOf()
) {
    companion object {
        const val BASE_NUM_AGENTS: Int = 8
    }
}

enum class GameStatus {
    NEW,
    IN_PROGRESS,
    GAME_OVER
}

enum class Team {
    RED,
    BLUE,
    ASSASSIN,
    CITIZEN
}

data class Card(
    var team: Team,
    var word: String,
    var isVisible: Boolean = false,
)

data class Clue(
    val clueString: String,
    val clueNum : Int,
)

data class Turn(
    val team: Team,
    val clueString: String,
    var guessString: String?,
    val clueNum : Int,
    var guessesLeft: Int,
    var correct: Boolean?,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    override fun equals(other: Any?)
        = (other is Turn)
        && team == other.team
        && clueString == other.clueString
        && guessString == other.guessString
        && clueNum == other.clueNum
        && guessesLeft == other.guessesLeft
        && correct == other.correct
}

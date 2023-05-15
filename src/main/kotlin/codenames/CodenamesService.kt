package codenames

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.random.Random
import mu.KotlinLogging

@Service
class CodenamesService(
    val gameDataRepository: GameDataRepository,
    val nounService: NounService,
    @Value("\${codenames.board.size}")
    var boardSize: Int
) {
    private val logger = KotlinLogging.logger {  }

    fun createNewGame(): String {
        logger.debug { "Creating new game" }
        return saveGameData(GameData()).id
    }

    fun clear() {
        logger.warn { "Deleting all games!" }
        gameDataRepository.deleteAll()
    }

    fun startGame(gameId: String): GameData {
        logger.info { "Starting game: $gameId" }
        val gameData = getGameData(gameId)

        // Randomly select a team to start the game
        if (Random.nextBoolean()) {
            gameData.startingTeam = Team.RED
            gameData.redAgentsLeft++
        } else {
            gameData.startingTeam = Team.BLUE
            gameData.blueAgentsLeft++
        }
        gameData.currentTeam = gameData.startingTeam

        gameData.board = generateBoard(gameData.startingTeam!!)
        gameData.gameStatus = GameStatus.IN_PROGRESS
        logger.debug { "Game Details for $gameId : $gameData" }
        return saveGameData(gameData)
    }

    // Generate a board with a set of words and assign a color to each word
    private fun generateBoard(startTeam: Team): List<Card> {
        val opponentTeam = getOpponent(startTeam)
        val listCards = ArrayList<Card>(boardSize)

        val words = nounService.drawCodeNames(boardSize)
        listCards.addAll(words.subList(0, GameData.BASE_NUM_AGENTS+1).map { Card(startTeam, it) } +
                words.subList(GameData.BASE_NUM_AGENTS+1, 2*GameData.BASE_NUM_AGENTS).map { Card(opponentTeam, it) } +
                words.subList(2*GameData.BASE_NUM_AGENTS, boardSize).map { Card(Team.CITIZEN, it) } +
                listOf(Card(Team.ASSASSIN, words[24])))

        listCards.shuffle()
        return listCards
    }

    fun getGame(gameId: String): GameData {
        return getGameData(gameId)
    }

    private fun getGameData(gameId: String): GameData {
        return gameDataRepository.findById(gameId).orElse(null)
    }

    private fun saveGameData(gameData: GameData): GameData {
        gameData.updatedAt = LocalDateTime.now()
        return gameDataRepository.save(gameData)
    }

    private fun getOpponent(team: Team): Team {
        return if (team == Team.RED) Team.BLUE else Team.RED
    }

    private fun gameover(winner: Team, gameData: GameData): GameData {
        gameData.winner = winner
        gameData.gameStatus = GameStatus.GAME_OVER
        return saveGameData(gameData)
    }

    private fun agentFound(gameData: GameData): GameData {
        if (gameData.currentTeam == Team.RED) {
            gameData.redAgentsLeft--
        } else {
            gameData.blueAgentsLeft--
        }
        return saveGameData(gameData)
    }

    private fun nextTurn(gameData: GameData): GameData {
        gameData.currentTeam = getOpponent(gameData.currentTeam!!)
        return saveGameData(gameData)
    }

    fun giveClue(gameId: String, clue: Clue): GameData {
        // make a turn and save for give clue
        val gameData = getGameData(gameId)
        val turn = Turn(gameData.currentTeam!!, clue.clueString, null, clue.clueNum, clue.clueNum+1, null)
        gameData.turns.add(turn)
        return saveGameData(gameData)
    }

    fun makeGuess(gameId: String, guess: String): GameData {
        // Retrieve the game data from the database
        val gameData = getGameData(gameId)
        val turn = gameData.turns.last().copy()
        assert(turn.guessesLeft > 0) { "No more guesses!" }
        assert(turn.team == gameData.currentTeam) { "Waiting for ${gameData.currentTeam} spymaster's clue!" }
        turn.guessesLeft--
        turn.guessString = guess

        val hitCard = gameData.board.filter { !it.isVisible }.first { it.word == guess }
        hitCard.isVisible = true
        return if (hitCard.team != gameData.currentTeam) {
            // Mistake!
            turn.correct = false
            turn.guessesLeft = 0
            gameData.turns.add(turn)
            return if (hitCard.team == Team.ASSASSIN) {
                //instant game over
                gameover(getOpponent(gameData.currentTeam!!), gameData)
            } else {
                nextTurn(gameData)
            }
        } else {
            turn.correct = true
            gameData.turns.add(turn)
            agentFound(gameData)
        }
    }
}

package codenames

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.random.Random
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired

@Service
class CodenamesService @Autowired constructor(
    val gameDataRepository: GameDataRepository,
    val nounService: NounService,
) {
    private val logger = KotlinLogging.logger {  }
    private final val boardSize = 25
    internal final val baseNumAgents = 8

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
        val gameData = getGame(gameId)!!

        gameData.redAgentsLeft = baseNumAgents
        gameData.blueAgentsLeft = baseNumAgents

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
    internal fun generateBoard(startTeam: Team): List<Card> {
        val opponentTeam = startTeam.getOpponent()
        val listCards = ArrayList<Card>(boardSize)

        val words = nounService.drawCodeNames(boardSize)
        listCards.addAll(words.subList(0, baseNumAgents+1).map { Card(startTeam, it) } +
                words.subList(baseNumAgents+1, 2*baseNumAgents+1).map { Card(opponentTeam, it) } +
                words.subList(2*baseNumAgents+1, boardSize-1).map { Card(Team.CITIZEN, it) } +
                listOf(Card(Team.ASSASSIN, words[24])))

        listCards.shuffle()
        return listCards
    }

    fun getGames(): List<GameData> {
        return gameDataRepository.findAll()
    }

    fun getGame(gameId: String): GameData? {
        return gameDataRepository.findById(gameId).get()
    }

    internal fun saveGameData(gameData: GameData): GameData {
        gameData.updatedAt = LocalDateTime.now()
        return gameDataRepository.save(gameData)
    }

    fun giveClue(gameId: String, clue: Clue): GameData {
        // make a turn and save for give clue
        val gameData = getGame(gameId)!!
        check(gameData.gameStatus == GameStatus.IN_PROGRESS) {"Game $gameId is not in progress!"}
        val turn = Turn(gameData.currentTeam!!, clue.clueString, null, clue.clueNum, clue.clueNum+1, null)

        // Can't use words on the board; invalid clue
        if (gameData.board.find { it.word == clue.clueString } != null) {
            turn.guessesLeft = 0
            gameData.currentTeam = gameData.currentTeam!!.getOpponent()
        }
        gameData.turns.add(turn)
        return saveGameData(gameData)
    }

    fun makeGuess(gameId: String, guess: String): GameData {
        // Retrieve the game data from the database and verify state
        val gameData = getGame(gameId)!!
        check(gameData.gameStatus == GameStatus.IN_PROGRESS) {"Game $gameId is not in progress!"}
        check(gameData.turns.isNotEmpty()) { "Waiting for ${gameData.currentTeam} spymaster's clue!" }
        val turn = gameData.turns.last().copy()
        check(turn.team == gameData.currentTeam!!) { "Waiting for ${gameData.currentTeam} spymaster's clue!" }
        check(turn.guessesLeft > 0) { "No more guesses!" }
        turn.guessesLeft--
        turn.guessString = guess

        val hitCard = gameData.board.filter { !it.isVisible }.firstOrNull { it.word == guess }
        check(hitCard != null) { "That guess isn't a concealed word on the board. Try again?"}

        hitCard.isVisible = true
        val opponent = gameData.currentTeam!!.getOpponent()

        if (hitCard.team == gameData.currentTeam) {
            // Correct guess!
            turn.correct = true
            agentFound(gameData, gameData.currentTeam!!)
        } else {
            turn.correct = false
            turn.guessesLeft = 0

            if (hitCard.team == opponent) {
                agentFound(gameData, opponent)
            }
        }
        gameData.turns.add(turn)

        //  Check gameover & turn conditions
        if (hitCard.team == Team.ASSASSIN) {
            return gameOver(gameData, opponent)
        }
        if (gameData.redAgentsLeft == 0) return gameOver(gameData, Team.RED)
        if (gameData.blueAgentsLeft == 0) return gameOver(gameData, Team.BLUE)
        if (turn.guessesLeft == 0) gameData.currentTeam = opponent

        return saveGameData(gameData)
    }

    internal fun agentFound(gameData: GameData, team: Team): GameData {
        if (team == Team.RED) {
            gameData.redAgentsLeft--
        } else {
            gameData.blueAgentsLeft--
        }
        return gameData
    }

    internal fun gameOver(gameData: GameData, winner: Team): GameData {
        gameData.winner = winner
        gameData.gameStatus = GameStatus.GAME_OVER
        return saveGameData(gameData)
    }
}

package codenames

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.random.Random
import mu.KotlinLogging

@Service
class CodenamesService(
    val gameDataRepository: GameDataRepository,
    val nounService: NounService,
) {
    private val logger = KotlinLogging.logger {  }
    private final val boardSize = 25
    private final val baseNumAgents = 8

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
        val opponentTeam = getOpponent(startTeam)
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
        return gameDataRepository.findById(gameId).orElse(null)
    }

    internal fun saveGameData(gameData: GameData): GameData {
        gameData.updatedAt = LocalDateTime.now()
        return gameDataRepository.save(gameData)
    }

    internal fun getOpponent(team: Team): Team {
        check(team == Team.BLUE || team == Team.RED) { "No opponent for $team - Only player teams permitted!" }
        return if (team == Team.RED) Team.BLUE else Team.RED
    }

    fun giveClue(gameId: String, clue: Clue): GameData {
        // make a turn and save for give clue
        val gameData = getGame(gameId)!!
        check(gameData.gameStatus == GameStatus.IN_PROGRESS) {"Game $gameId is not in progress!"}
        val turn = Turn(gameData.currentTeam!!, clue.clueString, null, clue.clueNum, clue.clueNum+1, null)
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

        val hitCard = gameData.board.filter { !it.isVisible }.filter{ it.word == guess }.firstOrNull()
        check(hitCard != null) { "That guess isn't a concealed word on the board. Try again?"}

        hitCard.isVisible = true
        val opponent = getOpponent(gameData.currentTeam!!)

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

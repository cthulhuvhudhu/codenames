package codenames

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.*
import kotlin.test.assertFailsWith

internal class CodenamesServiceTest {

    @Mock
    private lateinit var gameDataRepository: GameDataRepository
    @Mock
    private lateinit var nounService: NounService

    private lateinit var codenamesService: CodenamesService
    private val boardSize = 25
    private val baseNumAgents = 8
    private val gameId = "game123"

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        codenamesService = CodenamesService(gameDataRepository, nounService)
        val wordList = mutableListOf<String>().apply{
            repeat(boardSize){ this.add(element = "Word$it")}
        }
        `when`(nounService.drawCodeNames(boardSize)).thenReturn(wordList)
        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(GameData()))
    }

    @Test
    fun `test creating a new game`() {
        `when`(gameDataRepository.save(any())).thenReturn(GameData())
        assertDoesNotThrow{ UUID.fromString(codenamesService.createNewGame()) }
        verify(gameDataRepository).save(any())
    }

    @Test
    fun `test clear games`() {
        codenamesService.clear()
        verify(gameDataRepository).deleteAll()
    }

    @Test
    fun `test board generation`() {
        val startTeam = Team.BLUE
        val testBoard = codenamesService.generateBoard(startTeam)
        assertEquals(boardSize, testBoard.size)
        assertEquals(baseNumAgents+1, testBoard.count { it.team == Team.BLUE })
        assertEquals(baseNumAgents, testBoard.count { it.team == Team.RED })
        assertEquals(1, testBoard.count { it.team == Team.ASSASSIN })
        assertEquals(boardSize-2-2*baseNumAgents, testBoard.count { it.team == Team.CITIZEN })
        // Not tested; shuffle
    }

    @Test
    fun `test start game`() {
        val expectedGame = GameData()
        expectedGame.currentTeam = Team.RED
        expectedGame.startingTeam = Team.RED

        `when`(gameDataRepository.save(any())).thenReturn(expectedGame)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.startGame(gameId)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value

        assert(savedGame.gameStatus == GameStatus.IN_PROGRESS)
        assert(savedGame.redAgentsLeft > 0 && savedGame.blueAgentsLeft > 0)
        if (savedGame.redAgentsLeft > savedGame.blueAgentsLeft) {
            assert(savedGame.currentTeam == Team.RED)
            assert(savedGame.startingTeam == Team.RED)
            assert(savedGame.redAgentsLeft == baseNumAgents+1)
            assert(savedGame.blueAgentsLeft == baseNumAgents)
        } else {
            assert(savedGame.currentTeam == Team.BLUE)
            assert(savedGame.startingTeam == Team.BLUE)
            assert(savedGame.blueAgentsLeft == baseNumAgents+1)
            assert(savedGame.redAgentsLeft == baseNumAgents)
        }
    }

    @Test
    fun `test getting all games`() {
        codenamesService.getGames()
        verify(gameDataRepository).findAll()
    }

    @Test
    fun `test getting game data by ID`() {
        codenamesService.getGame(gameId)
        verify(gameDataRepository).findById(gameId)
    }

    @Test
    fun `test saving game data`() {
        val gameData = GameData()
        val old =  LocalDateTime.MIN
        gameData.updatedAt = old

        val captor = ArgumentCaptor.forClass(GameData::class.java)
        `when`(gameDataRepository.save(gameData)).thenReturn(GameData())
        codenamesService.saveGameData(gameData)

        verify(gameDataRepository).save(captor.capture())
        val newGameData = captor.value

        verify(gameDataRepository).save(any())
        assert(newGameData.updatedAt > old)
    }

    @Test
    fun `test get opponent`() {
        assertEquals(Team.BLUE, codenamesService.getOpponent(Team.RED))
        assertEquals(Team.RED, codenamesService.getOpponent(Team.BLUE))
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.getOpponent(Team.ASSASSIN) }
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.getOpponent(Team.CITIZEN) }
    }

    @Test
    fun `test give clue`() {
        val gameData = GameData(gameId)
        gameData.gameStatus = GameStatus.IN_PROGRESS
        gameData.currentTeam = Team.RED
        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        val clue = Clue("guess", 1)

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)

        codenamesService.giveClue(gameId, clue)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value

        assertEquals(1, savedGame.turns.size)
        assertEquals(clue.clueNum, savedGame.turns[0].clueNum)
        assertEquals(clue.clueString, savedGame.turns[0].clueString)

        gameData.gameStatus = GameStatus.GAME_OVER
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.giveClue(gameId, clue) }
    }

    @Test
    fun `test guess in bad state`() {
        val guess = "guess1"
        val turn = Turn(Team.BLUE, "clue", null, 1, 1, null)
        val gameData = GameData(gameId)
        gameData.gameStatus = GameStatus.GAME_OVER
        gameData.board = listOf(Card(Team.ASSASSIN, "guess1", false), Card(Team.BLUE, "guess2", true))
        gameData.currentTeam = Team.RED
        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))
        // Fails when game is not in progress
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.makeGuess(gameId, guess) }

        gameData.gameStatus = GameStatus.IN_PROGRESS
        // Fails when there are no turns (and therefore no clues)
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.makeGuess(gameId, guess) }

        gameData.turns.add(Turn(Team.RED, "clue", "lastGuess", 1, 0, false))
        // Fails when no more guesses allowed
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.makeGuess(gameId, guess) }

        gameData.turns = listOf(turn) as MutableList<Turn>
        // Fails when guessing team doesn't match clue giving team
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.makeGuess(gameId, guess) }

        // Test guessing word that doesn't exist, user error
        gameData.currentTeam = Team.BLUE
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.makeGuess(gameId, "guessDNE") }

        // Test word that is already visible
        assertFailsWith<java.lang.IllegalStateException> { codenamesService.makeGuess(gameId, "guess2") }
    }

    @Test
    fun `test successful guess, guesses left`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", null, 2, 2, null)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 1, true)
        val board = listOf(Card(Team.BLUE, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 2, 2, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(1, gameData.blueAgentsLeft)
        assertEquals(Team.BLUE, savedGame.currentTeam)
    }

    @Test
    fun `test successful guess, no guesses left`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", "lastGuess", 2, 1, true)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 0, true)
        val board = listOf(Card(Team.BLUE, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 2, 2, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(1, gameData.blueAgentsLeft)
        assertEquals(Team.RED, savedGame.currentTeam)
    }

    @Test
    fun `test successful guess, winning`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", null, 2, 2, null)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 1, true)
        val board = listOf(Card(Team.BLUE, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 2, 1, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(0, gameData.blueAgentsLeft)
        assertEquals(Team.BLUE, savedGame.currentTeam)
        assertEquals(GameStatus.GAME_OVER, savedGame.gameStatus)
        assertEquals(Team.BLUE, savedGame.winner)
    }

    @Test
    fun `test bad guess, citizen`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", null, 2, 2, null)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 0, false)
        val board = listOf(Card(Team.CITIZEN, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 2, 1, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(1, gameData.blueAgentsLeft)
        assertEquals(Team.RED, savedGame.currentTeam)
        assertEquals(GameStatus.IN_PROGRESS, savedGame.gameStatus)
    }

    @Test
    fun `test bad guess, opponent, agents left`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", null, 2, 2, null)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 0, false)
        val board = listOf(Card(Team.RED, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 2, 1, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(1, gameData.blueAgentsLeft)
        assertEquals(1, gameData.redAgentsLeft)
        assertEquals(Team.RED, savedGame.currentTeam)
        assertEquals(GameStatus.IN_PROGRESS, savedGame.gameStatus)
    }

    @Test
    fun `test bad guess, opponent, no agents left`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", null, 2, 2, null)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 0, false)
        val board = listOf(Card(Team.RED, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 1, 1, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(1, gameData.blueAgentsLeft)
        assertEquals(0, gameData.redAgentsLeft)
        assertEquals(Team.BLUE, savedGame.currentTeam)
        assertEquals(Team.RED, savedGame.winner)
        assertEquals(GameStatus.GAME_OVER, savedGame.gameStatus)
    }

    @Test
    fun `test bad guess, assassin`() {
        val guess = "Word1"
        val turn = Turn(Team.BLUE, "clue", null, 2, 2, null)
        val expectedTurn = Turn(Team.BLUE, "clue", guess, 2, 0, false)
        val board = listOf(Card(Team.ASSASSIN, "Word1", false))
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 1, 1, GameStatus.IN_PROGRESS, board)
        gameData.turns.add(turn)

        `when`(gameDataRepository.findById(gameId)).thenReturn(Optional.of(gameData))

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.makeGuess(gameId, guess)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value
        val savedTurn = savedGame.turns[1]
        assertEquals(expectedTurn, savedTurn)
        assertEquals(1, gameData.blueAgentsLeft)
        assertEquals(1, gameData.redAgentsLeft)
        assertEquals(Team.BLUE, savedGame.currentTeam)
        assertEquals(Team.RED, savedGame.winner)
        assertEquals(GameStatus.GAME_OVER, savedGame.gameStatus)
    }

    @Test
    fun `test agent found`() {
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 8, 8, GameStatus.IN_PROGRESS)
        val actualGame = codenamesService.agentFound(gameData, Team.BLUE)
        assertEquals(7, actualGame.blueAgentsLeft)
        assertEquals(8, actualGame.redAgentsLeft)
    }

    @Test
    fun `test game over`() {
        val gameData = GameData(gameId, now(), now(), Team.BLUE, Team.BLUE, 8, 8, GameStatus.IN_PROGRESS)

        `when`(gameDataRepository.save(any())).thenReturn(gameData)
        val captor = ArgumentCaptor.forClass(GameData::class.java)
        codenamesService.gameOver(gameData, Team.BLUE)
        verify(gameDataRepository).save(captor.capture())
        val savedGame = captor.value

        assertEquals(Team.BLUE, savedGame.winner)
        assertEquals(GameStatus.GAME_OVER, savedGame.gameStatus)
    }
}
